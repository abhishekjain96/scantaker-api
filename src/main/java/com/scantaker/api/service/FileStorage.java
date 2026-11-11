package com.scantaker.api.service;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.entity.StoredFile;
import com.scantaker.api.repository.StoredFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorage {
    private static final Set<String> EXT = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    private final Path root;
    private final StoredFileRepository repo;

    public FileStorage(@Value("${app.upload.dir}") String dir) throws IOException {
        this(dir, null);
    }

    @Autowired
    public FileStorage(@Value("${app.upload.dir}") String dir, StoredFileRepository repo) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.repo = repo;
        Files.createDirectories(root);
    }

    public String save(MultipartFile f) {
        if (f == null || f.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a file");
        String original = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : "";
        if (!EXT.contains(ext)) throw new ApiException(HttpStatus.BAD_REQUEST, "Only PDF, JPG, PNG or WEBP files are allowed");
        try {
            byte[] b = f.getBytes();
            boolean ok = (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F')           // PDF
                    || (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8)                         // JPEG
                    || (b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')    // PNG
                    || (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P');                         // WEBP
            if (!ok) throw new ApiException(HttpStatus.BAD_REQUEST, "This file is not a valid PDF, JPG, PNG or WEBP");
            String stored = UUID.randomUUID() + "." + (ext.equals("jpeg") ? "jpg" : ext);
            if (repo != null) {
                StoredFile entity = new StoredFile();
                entity.setFilename(stored);
                entity.setContentType(type(stored).toString());
                entity.setData(b);
                repo.save(entity);
            }
            try {
                Files.createDirectories(root);
                Files.write(root.resolve(stored), b);
            } catch (IOException diskEx) {
                if (repo == null) throw diskEx;
                log.warn("Could not write local cache for {} (persisted in DB): {}", stored, diskEx.getMessage());
            }
            return stored;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the file");
        }
    }

    public Resource load(String stored) {
        if (stored == null || stored.isBlank() || stored.contains("/") || stored.contains("\\") || stored.contains("..")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }
        Path p = root.resolve(stored).normalize();
        if (!p.startsWith(root)) throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        if (Files.exists(p) && Files.isRegularFile(p)) {
            return new FileSystemResource(p);
        }
        if (repo != null) {
            Optional<StoredFile> dbFile = repo.findByFilename(stored);
            if (dbFile.isPresent()) {
                byte[] bytes = dbFile.get().getData();
                try {
                    Files.createDirectories(root);
                    Files.write(p, bytes);
                } catch (IOException ignored) {}
                return new ByteArrayResource(bytes);
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
    }

    public static MediaType type(String stored) {
        if (stored == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = stored.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
