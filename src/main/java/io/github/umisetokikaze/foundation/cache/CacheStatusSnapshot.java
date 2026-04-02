package io.github.umisetokikaze.foundation.cache;

public record CacheStatusSnapshot(
        CacheModuleId module,
        boolean enabled,
        boolean quarantined,
        boolean rebuildRequested,
        long bytesUsed,
        long entryCount,
        String lastIntegrityState,
        String lastIntegrityReasonCode,
        long integrityFailureCount,
        String lastReasonCode,
        String lastDetail) {
}
