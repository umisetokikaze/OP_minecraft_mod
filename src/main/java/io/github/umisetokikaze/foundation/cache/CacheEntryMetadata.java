package io.github.umisetokikaze.foundation.cache;

public record CacheEntryMetadata(
        int schemaVersion,
        String fingerprint,
        String entryType,
        String checksum,
        long createdAtEpochMillis,
        long lastUsedAtEpochMillis,
        long sizeBytes,
        IntegrityState integrityState,
        String configDigest) {
}
