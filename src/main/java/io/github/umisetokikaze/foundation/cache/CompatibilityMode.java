package io.github.umisetokikaze.foundation.cache;

import java.util.Locale;

public enum CompatibilityMode {
    INHERIT,
    STANDARD,
    SAFE;

    public static CompatibilityMode fromConfigValue(String raw, CompatibilityMode fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "inherit" -> INHERIT;
            case "standard" -> STANDARD;
            case "safe" -> SAFE;
            default -> fallback;
        };
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
