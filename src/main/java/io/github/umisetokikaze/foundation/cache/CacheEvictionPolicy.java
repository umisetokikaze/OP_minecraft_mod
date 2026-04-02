package io.github.umisetokikaze.foundation.cache;

import java.util.Locale;

public enum CacheEvictionPolicy {
    INHERIT,
    LRU,
    NONE;

    public static CacheEvictionPolicy fromConfigValue(String raw, CacheEvictionPolicy fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "inherit" -> INHERIT;
            case "lru" -> LRU;
            case "none" -> NONE;
            default -> fallback;
        };
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
