package com.scantaker.api;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.entity.StoredFile;
import com.scantaker.api.repository.StoredFileRepository;
import com.scantaker.api.service.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileStoragePersistenceTest {

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private StoredFileRepository storedFileRepo;

    @Test
    void savesToDatabaseAndRecoversAfterDiskWipe(@TempDir Path tempDir) throws Exception {
        FileStorage isolated = new FileStorage(tempDir.toString(), storedFileRepo);

        byte[] pngBytes = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 13, 'I', 'H', 'D', 'R'
        };
        MockMultipartFile file = new MockMultipartFile("file", "hero-scan.png", "image/png", pngBytes);

        String storedName = isolated.save(file);
        assertTrue(storedName.endsWith(".png"));
        assertTrue(storedFileRepo.existsByFilename(storedName), "File must be persisted in database");

        Path diskPath = tempDir.resolve(storedName);
        assertTrue(Files.exists(diskPath), "Local disk cache should exist initially");

        // Simulate Render container sleep / ephemeral disk wipe
        Files.delete(diskPath);
        assertFalse(Files.exists(diskPath), "Simulated disk wipe removes local file");

        Resource loaded = isolated.load(storedName);
        assertNotNull(loaded);
        assertArrayEquals(pngBytes, loaded.getContentAsByteArray(), "File must load identically from DB after disk wipe");
        assertTrue(Files.exists(diskPath), "Loading from DB should re-hydrate local cache");
    }

    @Test
    void supportsPdfJpegAndWebpMagicBytes(@TempDir Path tempDir) throws Exception {
        FileStorage isolated = new FileStorage(tempDir.toString(), storedFileRepo);

        byte[] pdf = "%PDF-1.7 sample".getBytes();
        byte[] jpg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 10, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

        String pdfName = isolated.save(new MockMultipartFile("file", "rx.pdf", "application/pdf", pdf));
        String jpgName = isolated.save(new MockMultipartFile("file", "scan.jpeg", "image/jpeg", jpg));
        String webpName = isolated.save(new MockMultipartFile("file", "hero.webp", "image/webp", webp));

        assertEquals(MediaType.APPLICATION_PDF, FileStorage.type(pdfName));
        assertEquals(MediaType.IMAGE_JPEG, FileStorage.type(jpgName));
        assertTrue(jpgName.endsWith(".jpg"));
        assertEquals(MediaType.parseMediaType("image/webp"), FileStorage.type(webpName));

        StoredFile dbPdf = storedFileRepo.findByFilename(pdfName).orElseThrow();
        assertArrayEquals(pdf, dbPdf.getData());
    }

    @Test
    void rejectsInvalidFilesAndPathTraversal(@TempDir Path tempDir) throws Exception {
        FileStorage isolated = new FileStorage(tempDir.toString(), storedFileRepo);

        assertThrows(ApiException.class, () ->
                isolated.save(new MockMultipartFile("file", "malware.exe", "application/octet-stream", "MZ1234".getBytes())));

        assertThrows(ApiException.class, () ->
                isolated.save(new MockMultipartFile("file", "fake.png", "image/png", "not a png header".getBytes())));

        assertThrows(ApiException.class, () -> isolated.load("../etc/passwd"));
        assertThrows(ApiException.class, () -> isolated.load("non-existent-uuid.png"));
    }
}
