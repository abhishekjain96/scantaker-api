package com.scantaker.api.dto;

import com.scantaker.api.entity.Scan;
import com.scantaker.api.entity.ScanType;
import jakarta.validation.constraints.*;

public final class ScanDtos {
    private ScanDtos() {}

    public record View(Long id, String code, String slug, String name, ScanType type, String bodyPart, String desc,
                       int price, Integer mrp, String category, String img, String emoji, boolean popular, boolean active, int order) {
        public static View from(Scan s) {
            int calculatedMrp = s.getMrp() != null && s.getMrp() > 0 ? s.getMrp() : Math.round(s.getPrice() * 2.1f);
            String cat = s.getCategory() != null ? s.getCategory() : categorize(s.getBodyPart(), s.getName());
            return new View(s.getId(), s.getCode(), s.getSlug(), s.getName(), s.getType(), s.getBodyPart(), s.getDescription(),
                    s.getPrice(), calculatedMrp, cat, s.getImg(), s.getEmoji(), s.isPopular(), s.isActive(), s.getSortOrder());
        }

        private static String categorize(String bodyPart, String name) {
            String b = ((bodyPart != null ? bodyPart : "") + " " + (name != null ? name : "")).toLowerCase();
            if (b.contains("brain") || b.contains("head") || b.contains("sinus") || b.contains("pns") || b.contains("neck")) return "Brain & Head";
            if (b.contains("spine") || b.contains("cervical") || b.contains("lumbar") || b.contains("dorsal")) return "Spine & Back";
            if (b.contains("chest") || b.contains("lung") || b.contains("heart") || b.contains("hrct")) return "Chest & Lungs";
            if (b.contains("abdomen") || b.contains("pelvis") || b.contains("kub") || b.contains("kidney")) return "Abdomen & Pelvis";
            if (b.contains("knee") || b.contains("shoulder") || b.contains("hip") || b.contains("ankle") || b.contains("wrist") || b.contains("joint")) return "Bones & Joints";
            return "General Scans";
        }
    }

    public record Request(
        @NotBlank(message = "Scan name is required") String name,
        @NotNull(message = "Type is required") ScanType type,
        @NotBlank(message = "Body part is required") String bodyPart,
        String desc,
        @Positive(message = "Price must be greater than 0") int price,
        Integer mrp,
        String category,
        String img, String emoji,
        Boolean popular, Boolean active, Integer order) {}
}