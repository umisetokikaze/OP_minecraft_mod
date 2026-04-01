package io.github.umisetokikaze.foundation;

public enum BenchmarkCaseId {
    STARTUP_COLD("startup_cold"),
    STARTUP_WARM("startup_warm"),
    RESOURCE_RELOAD("resource_reload"),
    WORLD_JOIN_EXISTING("world_join_existing");

    private final String value;

    BenchmarkCaseId(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }

    static BenchmarkCaseId fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (BenchmarkCaseId candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        return null;
    }
}
