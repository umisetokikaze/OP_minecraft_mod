package io.github.umisetokikaze.foundation.cache;

public record CacheStatusSnapshot(
        CacheModuleId module,
        boolean enabled,
        boolean quarantined,
        boolean rebuildRequested,
        long bytesUsed,
        long entryCount,
        String lastReasonCode,
        String lastDetail) {
}
