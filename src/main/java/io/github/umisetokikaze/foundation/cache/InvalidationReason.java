package io.github.umisetokikaze.foundation.cache;

public enum InvalidationReason {
    HIT,
    ENTRY_MISSING,
    GLOBAL_DISABLED,
    MODULE_DISABLED,
    REBUILD_REQUESTED,
    SCHEMA_MISMATCH,
    FINGERPRINT_CHANGED,
    CHECKSUM_MISMATCH,
    DESERIALIZE_FAILED,
    IO_FAILURE,
    EVICTED,
    PURGED
}
