package com.scantaker.api.dto;

import com.scantaker.api.entity.Area;
import com.scantaker.api.entity.Faq;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public final class ContentDtos {
    private ContentDtos() {}

    public record FaqView(Long id, String q, String a, boolean active, int order) {
        public static FaqView from(Faq f) { return new FaqView(f.getId(), f.getQuestion(), f.getAnswer(), f.isActive(), f.getSortOrder()); }
    }
    public record FaqRequest(@NotBlank(message = "Question is required") String q,
                             @NotBlank(message = "Answer is required") String a,
                             Boolean active, Integer order) {}

    public record AreaView(Long id, String name, boolean active, int order) {
        public static AreaView from(Area a) { return new AreaView(a.getId(), a.getName(), a.isActive(), a.getSortOrder()); }
    }
    public record AreaRequest(@NotBlank(message = "Area name is required") String name, Boolean active, Integer order) {}

    public record SiteView(Map<String, String> settings, List<FaqView> faqs, List<AreaView> areas) {}
}