package com.scantaker.api;

import com.scantaker.api.config.DataSeeder;
import com.scantaker.api.entity.Scan;
import com.scantaker.api.repository.ScanRepository;
import com.scantaker.api.repository.SettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataSeederPreservationTest {

    @Autowired
    private ScanRepository scanRepo;

    @Autowired
    private SettingRepository settingRepo;

    @Autowired
    private DataSeeder dataSeeder;

    @Test
    void reRunningSeederDoesNotOverwriteAdminCustomScanImageOrPrice() throws Exception {
        Scan mriBrain = scanRepo.findBySlug("mri-brain-plain").orElseThrow();
        String customImg = "/api/files/admin-uploaded-mri-brain.png";
        String customName = "MRI Brain 3T Custom Package";
        int customPrice = 2799;

        mriBrain.setImg(customImg);
        mriBrain.setName(customName);
        mriBrain.setPrice(customPrice);
        mriBrain.setActive(false);
        scanRepo.save(mriBrain);

        // Simulate backend cold restart running DataSeeder again
        dataSeeder.run();

        Scan afterRestart = scanRepo.findBySlug("mri-brain-plain").orElseThrow();
        assertEquals(customImg, afterRestart.getImg(), "Uploaded scan image must survive backend restart");
        assertEquals(customName, afterRestart.getName(), "Edited scan name must survive backend restart");
        assertEquals(customPrice, afterRestart.getPrice(), "Edited scan price must survive backend restart");
        assertFalse(afterRestart.isActive(), "Admin active/inactive toggle must not be overwritten");

        // Also ensure settings like heroImage aren't wiped if already customized
        assertTrue(settingRepo.findById("heroImage").isPresent());
    }
}
