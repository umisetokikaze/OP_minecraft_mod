package io.github.umisetokikaze.foundation.cache;

public record CacheStatusSnapshot(
        CacheModuleId module,
        boolean enabled,
        boolean quarantined,
        boolean rebuildRequested,
        long bytesUsed,
        long entryCount,
        long budgetMiB,
        String evictionPolicy,
        String compatibilityMode,
        boolean debugLogging,
        boolean overBudget,
        String lastIntegrityState,
        String lastIntegrityReasonCode,
        long integrityFailureCount,
        String lastReasonCode,
        String lastDetail) {
}
