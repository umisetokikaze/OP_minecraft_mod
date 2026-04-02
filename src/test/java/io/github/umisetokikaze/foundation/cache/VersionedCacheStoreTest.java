package io.github.umisetokikaze.foundation.cache;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedCacheStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsMetadataAndPayload() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        ResourceIndexSnapshot snapshot = sampleSnapshot(Set.of("example:item/a.json"), Set.of("example:missing.json"));

        store.write(CacheModuleId.RESOURCE_INDEX, "fp1", "cfg1", "entry", ResourceIndexSnapshot.codec("resource_index"), snapshot);

        CacheLookupResult<ResourceIndexSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "fp1",
                "cfg1",
                "entry",
                ResourceIndexSnapshot.codec("resource_index"));

        assertTrue(result.hit());
        assertEquals(snapshot.existenceSet(), result.value().existenceSet());
        assertEquals(snapshot.negativeLookupSet(), result.value().negativeLookupSet());
        assertEquals(1, result.metadata().schemaVersion());
        assertEquals("fp1", result.metadata().fingerprint());
        assertEquals("resource_index", result.metadata().entryType());
        assertEquals("cfg1", result.metadata().configDigest());
        assertEquals(IntegrityState.VALID, result.metadata().integrityState());
    }

    @Test
    void checksumMismatchInvalidatesSingleEntry() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        store.write(CacheModuleId.RESOURCE_INDEX, "fp1", "cfg1", "good", ResourceIndexSnapshot.codec("resource_index"), sampleSnapshot(Set.of("example:item/a.json"), Set.of()));
        store.write(CacheModuleId.RESOURCE_INDEX, "fp1", "cfg1", "bad", ResourceIndexSnapshot.codec("resource_index"), sampleSnapshot(Set.of("example:item/b.json"), Set.of()));

        Path fingerprintDir = tempDir.resolve("schema-v1").resolve("resource_index").resolve("fp1");
        Files.writeString(
                fingerprintDir.resolve("bad.data.json"),
                "{\"corrupt\":true}",
                StandardCharsets.UTF_8);

        CacheLookupResult<ResourceIndexSnapshot> bad = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "fp1",
                "cfg1",
                "bad",
                ResourceIndexSnapshot.codec("resource_index"));
        CacheLookupResult<ResourceIndexSnapshot> good = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "fp1",
                "cfg1",
                "good",
                ResourceIndexSnapshot.codec("resource_index"));

        assertFalse(bad.hit());
        assertEquals(InvalidationReason.CHECKSUM_MISMATCH, bad.reason());
        assertNotNull(bad.metadata());
        assertEquals(IntegrityState.CORRUPT, bad.metadata().integrityState());
        assertFalse(Files.exists(fingerprintDir.resolve("bad.meta.json")));
        assertFalse(Files.exists(fingerprintDir.resolve("bad.data.json")));
        assertTrue(good.hit());
    }

    @Test
    void entryTypeMismatchDiscardsSingleEntry() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        store.write(CacheModuleId.RESOURCE_INDEX, "fp1", "cfg1", "typed", ResourceIndexSnapshot.codec("resource_index"), sampleSnapshot(Set.of("example:item/a.json"), Set.of()));

        CacheLookupResult<ResourceIndexSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "fp1",
                "cfg1",
                "typed",
                ResourceIndexSnapshot.codec("negative_lookup"));

        Path fingerprintDir = tempDir.resolve("schema-v1").resolve("resource_index").resolve("fp1");
        assertFalse(result.hit());
        assertEquals(InvalidationReason.ENTRY_TYPE_MISMATCH, result.reason());
        assertNotNull(result.metadata());
        assertEquals(IntegrityState.INVALIDATED, result.metadata().integrityState());
        assertFalse(Files.exists(fingerprintDir.resolve("typed.meta.json")));
        assertFalse(Files.exists(fingerprintDir.resolve("typed.data.json")));
    }

    @Test
    void fingerprintMismatchDiscardsSingleEntry() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        store.write(CacheModuleId.RESOURCE_INDEX, "fp1", "cfg1", "entry", ResourceIndexSnapshot.codec("resource_index"), sampleSnapshot(Set.of("example:item/a.json"), Set.of()));

        Path fingerprintDir = tempDir.resolve("schema-v1").resolve("resource_index").resolve("fp1");
        Path metaPath = fingerprintDir.resolve("entry.meta.json");
        String rewritten = Files.readString(metaPath, StandardCharsets.UTF_8).replace("\"fingerprint\": \"fp1\"", "\"fingerprint\": \"fp-other\"");
        Files.writeString(metaPath, rewritten, StandardCharsets.UTF_8);

        CacheLookupResult<ResourceIndexSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "fp1",
                "cfg1",
                "entry",
                ResourceIndexSnapshot.codec("resource_index"));

        assertFalse(result.hit());
        assertEquals(InvalidationReason.FINGERPRINT_CHANGED, result.reason());
        assertNotNull(result.metadata());
        assertEquals(IntegrityState.INVALIDATED, result.metadata().integrityState());
        assertFalse(Files.exists(fingerprintDir.resolve("entry.meta.json")));
        assertFalse(Files.exists(fingerprintDir.resolve("entry.data.json")));
    }

    @Test
    void refreshesLastUsedTimestampOnReadHit() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        CacheEntryMetadata written = store.write(
                CacheModuleId.RESOURCE_INDEX,
                "fp1",
                "cfg1",
                "entry",
                ResourceIndexSnapshot.codec("resource_index"),
                sampleSnapshot(Set.of("example:item/a.json"), Set.of()));

        Thread.sleep(5L);

        CacheLookupResult<ResourceIndexSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "fp1",
                "cfg1",
                "entry",
                ResourceIndexSnapshot.codec("resource_index"));

        assertTrue(result.hit());
        assertNotNull(result.metadata());
        assertTrue(result.metadata().lastUsedAtEpochMillis() >= written.lastUsedAtEpochMillis());
    }

    @Test
    void evictsLeastRecentlyUsedEntriesWhenBudgetExceeded() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        store.write(CacheModuleId.RESOURCE_INDEX, "fp1", "cfg1", "first", ResourceIndexSnapshot.codec("resource_index"), sampleSnapshot(Set.of("example:item/a.json"), Set.of()));
        Thread.sleep(5L);
        store.write(CacheModuleId.RESOURCE_INDEX, "fp1", "cfg1", "second", ResourceIndexSnapshot.codec("resource_index"), sampleSnapshot(Set.of("example:item/very_large_name_that_pushes_budget.json"), Set.of()));

        long targetBudget = 10L;
        var evicted = store.evictLeastRecentlyUsed(targetBudget, java.util.Optional.of(CacheModuleId.RESOURCE_INDEX));

        assertFalse(evicted.isEmpty());
        assertTrue(evicted.get(0).contains("resource_index"));
    }

    private static ResourceIndexSnapshot sampleSnapshot(Set<String> existence, Set<String> negative) {
        Map<String, Set<String>> pathsByNamespace = new LinkedHashMap<>();
        pathsByNamespace.put("example", new LinkedHashSet<>(Set.of("models/item/a.json")));
        return new ResourceIndexSnapshot(Set.of("example"), pathsByNamespace, existence, negative, "packs");
    }
}
