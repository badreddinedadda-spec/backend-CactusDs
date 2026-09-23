package com.cactusds.backend.comon.security;


public final class UserAgentParser {

    private UserAgentParser() {
    }

    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Appareil inconnu";
        }
        return browser(userAgent) + " sur " + os(userAgent);
    }

    private static String browser(String ua) {
        // Order matters: Edge and Opera UAs also contain "Chrome", and Chrome's UA also
        // contains "Safari", so the more specific tokens must be checked first.
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Safari/")) return "Safari";
        return "Navigateur inconnu";
    }

    private static String os(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "Mac";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Linux")) return "Linux";
        return "appareil inconnu";
    }
}