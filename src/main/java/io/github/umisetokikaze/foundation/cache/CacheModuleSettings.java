package io.github.umisetokikaze.foundation.cache;

import java.util.Locale;

public record CacheModuleSettings(
        CacheModuleId module,
        boolean enabled,
        int maxMiB,
        CacheEvictionPolicy evictionPolicy,
        DebugLoggingSetting debugLogging,
        CompatibilityMode compatibilityMode) {

    public enum DebugLoggingSetting {
        INHERIT,
        ENABLED,
        DISABLED;

        public static DebugLoggingSetting fromConfigValue(String raw, DebugLoggingSetting fallback) {
            if (raw == null) {
                return fallback;
            }
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "inherit" -> INHERIT;
                case "enabled" -> ENABLED;
                case "disabled" -> DISABLED;
                default -> fallback;
            };
        }

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
