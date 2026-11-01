package com.scantaker.api.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BookingStatus {
    PENDING, CONFIRMED, COMPLETED, CANCELLED;

    @JsonValue
    public String json() { return name().toLowerCase(); }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static BookingStatus from(String s) {
        if (s == null || s.isBlank()) return null;
        return valueOf(s.toUpperCase().trim());
    }
}