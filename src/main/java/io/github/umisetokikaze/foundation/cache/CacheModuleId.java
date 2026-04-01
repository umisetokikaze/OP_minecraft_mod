package io.github.umisetokikaze.foundation.cache;

public enum CacheModuleId {
    RESOURCE_INDEX("resource_index"),
    NEGATIVE_LOOKUP("negative_lookup");

    private final String id;

    CacheModuleId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
