package com.scantaker.api.service;

import com.scantaker.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorage {
    private static final Set<String> EXT = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    private final Path root;

    public FileStorage(@Value("${app.upload.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
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
                    || (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F');            // WEBP
            if (!ok) throw new ApiException(HttpStatus.BAD_REQUEST, "This file is not a valid PDF, JPG, PNG or WEBP");
            String stored = UUID.randomUUID() + "." + (ext.equals("jpeg") ? "jpg" : ext);
            Files.write(root.resolve(stored), b);
            return stored;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the file");
        }
    }

    public Resource load(String stored) {
        Path p = root.resolve(stored).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        return new FileSystemResource(p);
    }

    public static MediaType type(String stored) {
        if (stored.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (stored.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (stored.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}