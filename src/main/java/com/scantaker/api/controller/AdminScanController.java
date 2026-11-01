package com.scantaker.api.controller;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.common.Slug;
import com.scantaker.api.dto.ScanDtos.*;
import com.scantaker.api.entity.Scan;
import com.scantaker.api.repository.ScanRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/scans")
@RequiredArgsConstructor
public class AdminScanController {
    private final ScanRepository scans;

    @GetMapping
    public List<View> all() {
        return scans.findAllByOrderBySortOrderAsc().stream().map(View::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Request r) {
        Scan s = new Scan();
        String base = Slug.of(r.name()), slug = base;
        int i = 2;
        while (scans.existsBySlug(slug)) slug = base + "-" + i++;
        s.setSlug(slug);
        s.setSortOrder(r.order() != null ? r.order() : (int) scans.count() + 1);
        return View.from(scans.save(apply(s, r)));
    }

    @PutMapping("/{id}")
    public View update(@PathVariable Long id, @Valid @RequestBody Request r) {
        Scan s = scans.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Scan not found"));
        if (r.order() != null) s.setSortOrder(r.order());
        return View.from(scans.save(apply(s, r))); // slug same rehta hai (SEO URL na toote)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!scans.existsById(id)) throw new ApiException(HttpStatus.NOT_FOUND, "Scan not found");
        scans.deleteById(id);
    }

    private Scan apply(Scan s, Request r) {
        s.setName(r.name().trim());
        s.setType(r.type());
        s.setBodyPart(r.bodyPart().trim());
        s.setDescription(r.desc());
        s.setPrice(r.price());
        s.setEmoji(r.emoji() == null || r.emoji().isBlank() ? "🩻" : r.emoji());
        s.setImg(r.img() == null || r.img().isBlank() ? "/images/scans/" + s.getSlug() + ".webp" : r.img());
        if (r.popular() != null) s.setPopular(r.popular());
        if (r.active() != null) s.setActive(r.active());
        return s;
    }
}