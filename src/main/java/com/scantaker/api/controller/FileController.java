package com.scantaker.api.controller;

import com.scantaker.api.service.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileStorage storage;

    @GetMapping("/{name}")
    public ResponseEntity<Resource> serve(@PathVariable String name) {
        Resource r = storage.load(name);
        return ResponseEntity.ok()
                .contentType(FileStorage.type(name))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(r);
    }
}
