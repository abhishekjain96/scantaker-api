package com.scantaker.api.config;

import com.scantaker.api.entity.*;
import com.scantaker.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final ScanRepository scans;
    private final UserRepository users;
    private final CentreRepository centres;
    private final PasswordEncoder encoder;
    private final SettingRepository settingRepo;
    private final FaqRepository faqRepo;
    private final AreaRepository areaRepo;

    @Value("${app.admin.name}") private String adminName;
    @Value("${app.admin.mobile}") private String adminMobile;
    @Value("${app.admin.password}") private String adminPassword;

    record ScanSeedItem(String slug, String name, ScanType type, String bodyPart, String category,
                        String desc, int price, int mrp, String emoji, boolean popular, int order) {}

    @Override
    public void run(String... args) {
        seedComprehensiveScans();

        if (!users.existsByMobile(adminMobile)) {
            User a = new User();
            a.setName(adminName);
            a.setMobile(adminMobile);
            a.setPasswordHash(encoder.encode(adminPassword));
            a.setRole(Role.ADMIN);
            users.save(a);
            log.warn("Admin user created (mobile {}). Production me password zaroor badlo!", adminMobile);
        }

        // Seed demo centres
        Centre apex = null;
        if (centres.count() == 0) {
            apex = new Centre();
            apex.setName("Apex Diagnostic Centre");
            apex.setArea("Malviya Nagar");
            apex.setAddress("Sector 4, Malviya Nagar, Jaipur, Rajasthan 302017");
            apex.setPhone("9829012345");
            apex.setEmail("apex.malviya@diagnostic.com");
            apex.setContactPerson("Dr. Rajesh Sharma");
            apex.setModalities("MRI (1.5T / 3T), 128-Slice CT, Ultrasound, Digital X-Ray");
            apex.setDefaultDiscount(15.0);
            apex.setVerified(true);
            apex.setActive(true);
            centres.save(apex);

            Centre jrc = new Centre();
            jrc.setName("Jaipur Imaging & Research Centre");
            jrc.setArea("C-Scheme");
            jrc.setAddress("Bhagwan Das Road, C-Scheme, Jaipur 302001");
            jrc.setPhone("9829054321");
            jrc.setEmail("info@jaipurimaging.in");
            jrc.setContactPerson("Dr. Meenakshi Verma");
            jrc.setModalities("MRI 3.0 Tesla, High-Speed Dual Source CT");
            jrc.setDefaultDiscount(20.0);
            jrc.setVerified(true);
            jrc.setActive(true);
            centres.save(jrc);

            Centre soni = new Centre();
            soni.setName("Soni Diagnostic & Scan Centre");
            soni.setArea("JLN Marg");
            soni.setAddress("JLN Marg, Near Police Memorial, Jaipur 302004");
            soni.setPhone("9829098765");
            soni.setEmail("contact@sonihospital.com");
            soni.setContactPerson("Anil Soni");
            soni.setModalities("1.5T MRI, 64-Slice CT");
            soni.setDefaultDiscount(10.0);
            soni.setVerified(true);
            soni.setActive(true);
            centres.save(soni);
            log.info("Demo centres seeded");
        } else {
            apex = centres.findAll().get(0);
        }

        // Seed demo centre user (Apex Diagnostic Centre)
        if (!users.existsByMobile("8888888888") && apex != null) {
            User cu = new User();
            cu.setName("Apex Diagnostic Centre");
            cu.setMobile("8888888888");
            cu.setEmail("apex@diagnostic.com");
            cu.setPasswordHash(encoder.encode("Centre@12345"));
            cu.setRole(Role.CENTRE);
            cu.setCentre(apex);
            cu.setCity("Malviya Nagar, Jaipur");
            users.save(cu);
            log.info("Demo centre partner user created: 8888888888 / Centre@12345");
        }

        seedContent();
    }

    private void seedComprehensiveScans() {
        try {
            org.springframework.core.io.ClassPathResource res = new org.springframework.core.io.ClassPathResource("scans_catalog.json");
            if (res.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                List<Map<String, Object>> items = mapper.readValue(
                    res.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {}
                );
                log.info("Loading {} complete scans from scans_catalog.json", items.size());
                for (Map<String, Object> item : items) {
                    String slug = (String) item.get("slug");
                    String code = (String) item.get("code");
                    String name = (String) item.get("name");
                    String typeStr = (String) item.get("type");
                    ScanType type = "CT".equalsIgnoreCase(typeStr) ? ScanType.CT : ScanType.MRI;
                    String bodyPart = (String) item.get("bodyPart");
                    String category = (String) item.get("category");
                    String desc = (String) item.get("desc");
                    int mrp = item.get("mrp") != null ? ((Number) item.get("mrp")).intValue() : 5000;
                    int price = item.get("price") != null ? ((Number) item.get("price")).intValue() : 2499;
                    String emoji = (String) item.get("emoji");
                    boolean popular = Boolean.TRUE.equals(item.get("popular"));
                    int order = item.get("order") != null ? ((Number) item.get("order")).intValue() : 100;

                    Optional<Scan> existing = scans.findBySlug(slug);
                    if (existing.isPresent()) {
                        Scan s = existing.get();
                        boolean changed = false;
                        if (s.getCode() == null || s.getCode().isBlank()) {
                            s.setCode(code);
                            changed = true;
                        }
                        if (s.getCategory() == null || s.getCategory().isBlank()) {
                            s.setCategory(category != null ? category : "General Scans");
                            changed = true;
                        }
                        if (changed) scans.save(s);
                        continue;
                    }
                    Scan s = new Scan();
                    s.setSlug(slug);
                    s.setCode(code);
                    s.setName(name);
                    s.setType(type);
                    s.setBodyPart(bodyPart != null ? bodyPart : "General");
                    s.setCategory(category != null ? category : "General Scans");
                    s.setDescription(desc);
                    s.setMrp(mrp);
                    s.setPrice(price);
                    s.setEmoji(emoji != null ? emoji : "🩻");
                    s.setImg("/images/scans/" + slug + ".webp");
                    s.setPopular(popular);
                    s.setActive(true);
                    s.setSortOrder(order);
                    scans.save(s);
                }
                log.info("Comprehensive scans catalog seeded/verified: {} total items in database", scans.count());
                return;
            }
        } catch (Exception e) {
            log.error("Failed to seed from scans_catalog.json, falling back", e);
        }
    }

    private void seedContent() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("whatsapp", "919999999999");
        d.put("email", "support@scantaker.com");
        d.put("heroBadge", "Jaipur's MRI & CT Booking Platform");
        d.put("heroTitle", "Book Your [[MRI or CT Scan]] in [[Jaipur]], with Ease");
        d.put("heroSubtitle", "Upload your prescription or select a scan, fill in a few details and book your appointment. Our team will share centre details, price and available slots on WhatsApp.");
        d.put("heroImage", "/images/hero-banner.jpg");
        d.put("ctaTitle", "Quick, Simple and Convenient MRI & CT Booking in [[Jaipur]]");
        d.put("announcement", "");
        d.put("instagram", ""); d.put("facebook", ""); d.put("youtube", ""); d.put("linkedin", "");
        d.forEach((k, v) -> { if (!settingRepo.existsById(k)) settingRepo.save(new Setting(k, v)); });

        if (faqRepo.count() == 0) {
            String[][] f = {
                {"How will I receive the centre details?", "After you submit your request, our team shares the recommended centre's name, address, price and available slot on your WhatsApp."},
                {"Do I need a prescription?", "You can upload a prescription or enter the scan details manually."},
                {"Is there any booking fee?", "Submitting a request is simple. The final price is shared with you on WhatsApp before you confirm."},
                {"Can I change the date later?", "Yes, message us on WhatsApp and we will help you reschedule."},
                {"Can I choose a specific date?", "Yes, you pick a preferred date and time. The centre confirms the final slot."},
                {"Will I get a choice of centres?", "We recommend the most suitable centre for your scan and area. If you need another option, just ask us on WhatsApp."}};
            for (int i = 0; i < f.length; i++) {
                Faq q = new Faq();
                q.setQuestion(f[i][0]); q.setAnswer(f[i][1]); q.setSortOrder(i + 1);
                faqRepo.save(q);
            }
        }
        if (areaRepo.count() == 0) {
            String[] a = {"Vaishali Nagar", "Malviya Nagar", "C-Scheme", "Bani Park", "Gopalpura", "Mansarovar",
                          "Durgapura", "Jhotwara", "Shyam Nagar", "Raja Park", "Tonk Road", "MI Road"};
            for (int i = 0; i < a.length; i++) {
                Area x = new Area();
                x.setName(a[i]); x.setSortOrder(i + 1);
                areaRepo.save(x);
            }
        }
    }
}