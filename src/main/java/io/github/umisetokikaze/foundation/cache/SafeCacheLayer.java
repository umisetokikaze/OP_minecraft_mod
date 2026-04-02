package io.github.umisetokikaze.foundation.cache;

import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SafeCacheLayer {
    public static final int SCHEMA_VERSION = 1;

    private final ProfilingFoundation foundation;
    private final VersionedCacheStore store;
    private final ConcurrentHashMap<CacheModuleId, Boolean> quarantined = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheModuleId, Boolean> rebuildRequested = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheModuleId, String> lastReason = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheModuleId, String> lastDetail = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheModuleId, String> lastIntegrityState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheModuleId, String> lastIntegrityReason = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheModuleId, Long> integrityFailureCount = new ConcurrentHashMap<>();

    public SafeCacheLayer(ProfilingFoundation foundation, Path rootDirectory) {
        this.foundation = foundation;
        this.store = new VersionedCacheStore(rootDirectory, SCHEMA_VERSION);
    }

    public boolean isGlobalEnabled() {
        return Config.CACHE_GLOBAL_ENABLED.get();
    }

    public boolean isModuleEnabled(CacheModuleId module) {
        return switch (module) {
            case RESOURCE_INDEX -> Config.CACHE_RESOURCE_INDEX_ENABLED.get();
            case NEGATIVE_LOOKUP -> Config.CACHE_NEGATIVE_LOOKUP_ENABLED.get();
        };
    }

    public <T> CacheLookupResult<T> read(
            PackFingerprintSnapshot snapshot,
            CacheModuleId module,
            String dependencyDigest,
            String entryKey,
            CachePayloadCodec<T> codec) {
        if (!isGlobalEnabled()) {
            return miss(snapshot, module, InvalidationReason.GLOBAL_DISABLED, "");
        }
        if (!isModuleEnabled(module)) {
            return miss(snapshot, module, InvalidationReason.MODULE_DISABLED, "");
        }
        if (Boolean.TRUE.equals(quarantined.get(module))) {
            return miss(snapshot, module, InvalidationReason.MODULE_DISABLED, "quarantined");
        }
        if (Boolean.TRUE.equals(rebuildRequested.get(module))) {
            return miss(snapshot, module, InvalidationReason.REBUILD_REQUESTED, "manual-rebuild");
        }

        CacheLookupResult<T> result = store.read(module, dependencyDigest, stableEntryKey(entryKey), codec);
        note(snapshot, module, result.reason(), result.detail(), result.hit());
        if (!result.hit() && result.reason() == InvalidationReason.DESERIALIZE_FAILED) {
            quarantine(snapshot, module, "DESERIALIZE_FAILED", result.detail());
        }
        refreshUsage(snapshot, module);
        return result;
    }

    public <T> void write(
            PackFingerprintSnapshot snapshot,
            CacheModuleId module,
            String dependencyDigest,
            String entryKey,
            CachePayloadCodec<T> codec,
            T value) {
        if (!isGlobalEnabled() || !isModuleEnabled(module)) {
            return;
        }
        try {
            store.write(module, dependencyDigest, stableEntryKey(entryKey), codec, value);
            rebuildRequested.remove(module);
            note(snapshot, module, InvalidationReason.HIT, "write", true);
            evictIfNeeded();
            refreshUsage(snapshot, module);
        } catch (IOException exception) {
            foundation.recordInvalidation(snapshot, module.id(), InvalidationReason.IO_FAILURE.name(), exception.getClass().getSimpleName());
            quarantine(snapshot, module, "IO_FAILURE", "write-failed");
        }
    }

    public void invalidateModule(PackFingerprintSnapshot snapshot, CacheModuleId module, InvalidationReason reason, String detail) {
        try {
            store.purge(module);
        } catch (IOException exception) {
            quarantine(snapshot, module, "IO_FAILURE", "purge-failed");
            return;
        }
        note(snapshot, module, reason, detail, false);
        refreshUsage(snapshot, module);
    }

    public void purge(Optional<PackFingerprintSnapshot> snapshot, Optional<CacheModuleId> module) {
        if (module.isPresent()) {
            invalidateModule(snapshot.orElse(null), module.get(), InvalidationReason.PURGED, "manual");
            return;
        }
        for (CacheModuleId item : CacheModuleId.values()) {
            invalidateModule(snapshot.orElse(null), item, InvalidationReason.PURGED, "manual");
        }
    }

    public void markRebuildRequested(CacheModuleId module) {
        rebuildRequested.put(module, true);
    }

    public void clearRebuildRequested(CacheModuleId module) {
        rebuildRequested.remove(module);
    }

    public void quarantine(PackFingerprintSnapshot snapshot, CacheModuleId module, String reasonCode, String detail) {
        quarantined.put(module, true);
        foundation.quarantine(snapshot, module.id(), reasonCode, detail);
    }

    public void clearQuarantine(CacheModuleId module) {
        quarantined.remove(module);
        foundation.clearQuarantine(module.id(), "RECOVERED", "manual-reset");
    }

    public Map<CacheModuleId, CacheStatusSnapshot> statusSnapshot() {
        Map<CacheModuleId, CacheStatusSnapshot> snapshots = new EnumMap<>(CacheModuleId.class);
        for (CacheModuleId module : CacheModuleId.values()) {
            VersionedCacheStore.UsageStats usage = store.usage(module);
            snapshots.put(module, new CacheStatusSnapshot(
                    module,
                    isGlobalEnabled() && isModuleEnabled(module),
                    Boolean.TRUE.equals(quarantined.get(module)),
                    Boolean.TRUE.equals(rebuildRequested.get(module)),
                    usage.bytesUsed(),
                    usage.entryCount(),
                    lastIntegrityState.getOrDefault(module, IntegrityState.VALID.name()),
                    lastIntegrityReason.getOrDefault(module, "NONE"),
                    integrityFailureCount.getOrDefault(module, 0L),
                    lastReason.getOrDefault(module, "NONE"),
                    lastDetail.getOrDefault(module, "")));
        }
        return Map.copyOf(snapshots);
    }

    private void evictIfNeeded() throws IOException {
        long budgetBytes = Config.CACHE_MAX_MIB.get() * 1024L * 1024L;
        List<String> evicted = store.evictLeastRecentlyUsed(budgetBytes, Optional.empty());
        for (String entry : evicted) {
            foundation.recordInvalidation("foundation.cache", InvalidationReason.EVICTED.name(), entry);
        }
    }

    private <T> CacheLookupResult<T> miss(
            PackFingerprintSnapshot snapshot,
            CacheModuleId module,
            InvalidationReason reason,
            String detail) {
        note(snapshot, module, reason, detail, false);
        refreshUsage(snapshot, module);
        return CacheLookupResult.miss(reason, detail);
    }

    private void note(PackFingerprintSnapshot snapshot, CacheModuleId module, InvalidationReason reason, String detail, boolean hit) {
        lastReason.put(module, reason.name());
        lastDetail.put(module, detail == null ? "" : detail);
        noteIntegrity(module, reason);
        foundation.recordCacheResult(snapshot, module.id(), hit, reason.name(), detail);
        if (!hit && reason != InvalidationReason.ENTRY_MISSING && reason != InvalidationReason.REBUILD_REQUESTED) {
            foundation.recordInvalidation(snapshot, module.id(), reason.name(), detail);
        }
    }

    private void refreshUsage(PackFingerprintSnapshot snapshot, CacheModuleId module) {
        VersionedCacheStore.UsageStats usage = store.usage(module);
        foundation.recordCacheUsage(snapshot, module.id(), usage.bytesUsed(), usage.entryCount(), Config.CACHE_MAX_MIB.get());
    }

    private String stableEntryKey(String entryKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(entryKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }

    private void noteIntegrity(CacheModuleId module, InvalidationReason reason) {
        IntegrityState state = switch (reason) {
            case CHECKSUM_MISMATCH, DESERIALIZE_FAILED -> IntegrityState.CORRUPT;
            case SCHEMA_MISMATCH, FINGERPRINT_CHANGED, ENTRY_TYPE_MISMATCH -> IntegrityState.INVALIDATED;
            default -> IntegrityState.VALID;
        };
        lastIntegrityState.put(module, state.name());
        lastIntegrityReason.put(module, state == IntegrityState.VALID ? "NONE" : reason.name());
        if (state != IntegrityState.VALID) {
            integrityFailureCount.merge(module, 1L, Long::sum);
        }
        foundation.recordIntegrityResult(module.id(), state.name(), state == IntegrityState.VALID ? "NONE" : reason.name());
    }
}
