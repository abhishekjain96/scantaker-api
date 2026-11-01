package com.scantaker.api.common;

public final class Slug {
    private Slug() {}

    public static String of(String s) {
        return s.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}