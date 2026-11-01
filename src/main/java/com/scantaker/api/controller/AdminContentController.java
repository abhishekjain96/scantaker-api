package com.scantaker.api.controller;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.ContentDtos.*;
import com.scantaker.api.entity.*;
import com.scantaker.api.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminContentController {
    private final SettingRepository settingRepo;
    private final FaqRepository faqRepo;
    private final AreaRepository areaRepo;
    private final com.scantaker.api.service.FileStorage storage;

    private static final Set<String> KEYS = Set.of(
        "whatsapp", "email", "heroBadge", "heroTitle", "heroSubtitle", "heroImage", "ctaTitle",
        "announcement", "instagram", "facebook", "youtube", "linkedin",
        "popupActive", "popupBadge", "popupTitle", "popupDiscount", "popupCode", "popupDesc", "couponsJson");

    /* ---------- settings ---------- */
    @GetMapping("/settings")
    public Map<String, String> settings() { return SiteController.settingsMap(settingRepo); }

    @PutMapping("/settings")
    public Map<String, String> saveSettings(@RequestBody Map<String, String> body) {
        body.forEach((k, v) -> {
            if (!KEYS.contains(k)) return;
            String val = v == null ? "" : v.trim();
            int maxLen = k.equals("couponsJson") ? 20000 : 2000;
            if (val.length() > maxLen) throw new ApiException(HttpStatus.BAD_REQUEST, k + " is too long");
            if (k.equals("whatsapp") && !val.matches("\\d{10,15}"))
                throw new ApiException(HttpStatus.BAD_REQUEST, "WhatsApp number must be digits with country code, e.g. 919876543210");
            settingRepo.save(new Setting(k, val));
        });
        return SiteController.settingsMap(settingRepo);
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadImage(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String stored = storage.save(file);
        return Map.of("url", "/api/files/" + stored, "filename", stored);
    }

    /* ---------- faqs ---------- */
    @GetMapping("/faqs")
    public List<FaqView> faqs() { return faqRepo.findAllByOrderBySortOrderAsc().stream().map(FaqView::from).toList(); }

    @PostMapping("/faqs")
    @ResponseStatus(HttpStatus.CREATED)
    public FaqView addFaq(@Valid @RequestBody FaqRequest r) {
        Faq f = new Faq();
        f.setSortOrder((int) faqRepo.count() + 1);
        return FaqView.from(faqRepo.save(apply(f, r)));
    }

    @PutMapping("/faqs/{id}")
    public FaqView editFaq(@PathVariable Long id, @Valid @RequestBody FaqRequest r) {
        Faq f = faqRepo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAQ not found"));
        return FaqView.from(faqRepo.save(apply(f, r)));
    }

    @DeleteMapping("/faqs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFaq(@PathVariable Long id) { faqRepo.deleteById(id); }

    private Faq apply(Faq f, FaqRequest r) {
        f.setQuestion(r.q().trim());
        f.setAnswer(r.a().trim());
        if (r.active() != null) f.setActive(r.active());
        if (r.order() != null) f.setSortOrder(r.order());
        return f;
    }

    /* ---------- areas ---------- */
    @GetMapping("/areas")
    public List<AreaView> areas() { return areaRepo.findAllByOrderBySortOrderAsc().stream().map(AreaView::from).toList(); }

    @PostMapping("/areas")
    @ResponseStatus(HttpStatus.CREATED)
    public AreaView addArea(@Valid @RequestBody AreaRequest r) {
        if (areaRepo.existsByNameIgnoreCase(r.name().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "This area already exists");
        Area a = new Area();
        a.setName(r.name().trim());
        a.setSortOrder((int) areaRepo.count() + 1);
        return AreaView.from(areaRepo.save(a));
    }

    @PutMapping("/areas/{id}")
    public AreaView editArea(@PathVariable Long id, @Valid @RequestBody AreaRequest r) {
        Area a = areaRepo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Area not found"));
        a.setName(r.name().trim());
        if (r.active() != null) a.setActive(r.active());
        if (r.order() != null) a.setSortOrder(r.order());
        return AreaView.from(areaRepo.save(a));
    }

    @DeleteMapping("/areas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArea(@PathVariable Long id) { areaRepo.deleteById(id); }
}