package io.github.umisetokikaze.foundation.cache;

public record CacheEntryMetadata(
        int schemaVersion,
        String dependencyDigest,
        String entryType,
        String checksum,
        long createdAtEpochMillis,
        long lastUsedAtEpochMillis,
        long sizeBytes,
        IntegrityState integrityState) {
}
