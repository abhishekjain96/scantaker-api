package com.scantaker.api.controller;

import com.scantaker.api.dto.ContentDtos.*;
import com.scantaker.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SiteController {
    private final SettingRepository settingRepo;
    private final FaqRepository faqRepo;
    private final AreaRepository areaRepo;

    static Map<String, String> settingsMap(SettingRepository r) {
        Map<String, String> m = new HashMap<>();
        r.findAll().forEach(s -> m.put(s.getKey(), s.getValue() == null ? "" : s.getValue()));
        return m;
    }

    @GetMapping("/api/site")
    public SiteView site() {
        return new SiteView(
            settingsMap(settingRepo),
            faqRepo.findByActiveTrueOrderBySortOrderAsc().stream().map(FaqView::from).toList(),
            areaRepo.findByActiveTrueOrderBySortOrderAsc().stream().map(AreaView::from).toList());
    }
}