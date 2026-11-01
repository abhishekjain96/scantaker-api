package com.scantaker.api.controller;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.ScanDtos.View;
import com.scantaker.api.repository.ScanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/scans")
@RequiredArgsConstructor
public class ScanController {
    private final ScanRepository scans;

    @GetMapping
    public List<View> all() {
        return scans.findByActiveTrueOrderBySortOrderAsc().stream().map(View::from).toList();
    }

    @GetMapping("/popular")
    public List<View> popular() {
        return scans.findByActiveTrueAndPopularTrueOrderBySortOrderAsc().stream().limit(8).map(View::from).toList();
    }

    @GetMapping("/{slug}")
    public View one(@PathVariable String slug) {
        return scans.findBySlug(slug).filter(s -> s.isActive()).map(View::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Scan not found"));
    }
}