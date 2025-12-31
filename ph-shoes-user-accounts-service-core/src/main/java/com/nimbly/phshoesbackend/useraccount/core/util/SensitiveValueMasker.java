package com.nimbly.phshoesbackend.useraccount.core.util;

public final class SensitiveValueMasker {

    private SensitiveValueMasker() {
    }

    public static String hashPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "(blank)";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(blank)";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            String domainPart = (atIndex >= 0) ? email.substring(atIndex) : "";
            return "***" + domainPart;
        }
        String domainPart = (atIndex >= 0) ? email.substring(atIndex) : "";
        return email.charAt(0) + "***" + domainPart;
    }

    public static String truncateForLog(String value, int max) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
