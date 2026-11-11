package com.scantaker.api;

import com.scantaker.api.dto.AuthDtos.Signup;
import com.scantaker.api.entity.Role;
import com.scantaker.api.entity.User;
import com.scantaker.api.repository.UserRepository;
import com.scantaker.api.security.JwtService;
import com.scantaker.api.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SecurityAndControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    private MockMvc mockMvc;
    private String adminToken;
    private String patientToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User admin = userRepo.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow();
        adminToken = jwtService.generate(admin);

        var existingPatient = userRepo.findByMobile("7222223333");
        if (existingPatient.isPresent()) {
            patientToken = jwtService.generate(existingPatient.get());
        } else {
            patientToken = authService.signup(new Signup("Sec Patient", "7222223333", "sec.patient@scantaker.com", "Test@1234")).token();
        }
    }

    @Test
    void publicEndpointsWorkAndAdminEndpointsEnforceRbac() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.whatsapp").exists());

        mockMvc.perform(get("/api/scans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        // Analytics endpoints RBAC + real DB tracking
        mockMvc.perform(get("/api/admin/analytics/stats"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/analytics/stats")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/analytics/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"s_integ_1","visitorId":"v_integ_1","path":"/scans/mri-brain","referrer":"https://www.google.com/search?q=mri+jaipur","device":"Mobile","browser":"Chrome","city":"Jaipur, IN","eventType":"pageview","durationSeconds":42}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/admin/analytics/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayVisitors", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.allTimeVisitors", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.todayPageViews", greaterThanOrEqualTo(1)));
    }

    @Test
    void adminUploadedHeroImageServesPubliclyEvenWhenDiskCacheIsDeleted() throws Exception {
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 13, 'I', 'H', 'D', 'R'
        };
        MockMultipartFile file = new MockMultipartFile("file", "hero-live.png", "image/png", pngBytes);

        // Patient forbidden from uploading
        mockMvc.perform(multipart("/api/admin/upload").file(file)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        MvcResult uploadRes = mockMvc.perform(multipart("/api/admin/upload").file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/api/files/")))
                .andReturn();

        String body = uploadRes.getResponse().getContentAsString();
        String filename = body.replaceAll(".*\"filename\":\"([^\"]+)\".*", "$1");

        // Remove disk cache copy to mimic Render container wipe
        Files.deleteIfExists(Path.of("./target/test-uploads", filename));

        mockMvc.perform(get("/api/files/" + filename))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(pngBytes));
    }
}
