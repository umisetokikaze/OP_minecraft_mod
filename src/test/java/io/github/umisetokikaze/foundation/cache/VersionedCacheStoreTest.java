package io.github.umisetokikaze.foundation.cache;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
    void roundTripsResourceIndexMetadataAndPayload() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        ResourceIndexSnapshot snapshot = VersionedCacheStoreFixtures.sampleResourceIndexSnapshot();

        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "entry", ResourceIndexSnapshot.codec(), snapshot);

        CacheLookupResult<ResourceIndexSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "dep1",
                "entry",
                ResourceIndexSnapshot.codec());

        assertTrue(result.hit());
        assertEquals(snapshot.fileExistenceMap(), result.value().fileExistenceMap());
        assertEquals(snapshot.winnerOriginIndex(), result.value().winnerOriginIndex());
        assertEquals(1, result.metadata().schemaVersion());
        assertEquals("dep1", result.metadata().dependencyDigest());
        assertEquals("resource_index", result.metadata().entryType());
        assertEquals(IntegrityState.VALID, result.metadata().integrityState());
    }

    @Test
    void roundTripsNegativeLookupWithDedicatedEntryType() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        NegativeLookupSnapshot snapshot = VersionedCacheStoreFixtures.sampleNegativeLookupSnapshot();

        store.write(CacheModuleId.NEGATIVE_LOOKUP, "dep1", "entry", NegativeLookupSnapshot.codec(), snapshot);

        CacheLookupResult<NegativeLookupSnapshot> result = store.read(
                CacheModuleId.NEGATIVE_LOOKUP,
                "dep1",
                "entry",
                NegativeLookupSnapshot.codec());

        assertTrue(result.hit());
        assertTrue(result.value().isKnownMissing("missing", "models/item/a.json"));
        assertFalse(result.value().isKnownMissing("example", "models/item/a.json"));
        assertEquals("negative_lookup", result.metadata().entryType());
    }

    @Test
    void checksumMismatchInvalidatesSingleEntry() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "good", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot());
        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "bad", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot("example:item/b.json"));

        Path fingerprintDir = tempDir.resolve("schema-v1").resolve("resource_index").resolve("dep1");
        Files.writeString(
                fingerprintDir.resolve("bad.data.json"),
                "{\"corrupt\":true}",
                StandardCharsets.UTF_8);

        CacheLookupResult<ResourceIndexSnapshot> bad = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "dep1",
                "bad",
                ResourceIndexSnapshot.codec());
        CacheLookupResult<ResourceIndexSnapshot> good = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "dep1",
                "good",
                ResourceIndexSnapshot.codec());

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
        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "typed", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot());

        CacheLookupResult<NegativeLookupSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "dep1",
                "typed",
                NegativeLookupSnapshot.codec());

        Path fingerprintDir = tempDir.resolve("schema-v1").resolve("resource_index").resolve("dep1");
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
        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "entry", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot());

        Path fingerprintDir = tempDir.resolve("schema-v1").resolve("resource_index").resolve("dep1");
        Path metaPath = fingerprintDir.resolve("entry.meta.json");
        String rewritten = Files.readString(metaPath, StandardCharsets.UTF_8).replace("\"dependencyDigest\": \"dep1\"", "\"dependencyDigest\": \"dep-other\"");
        Files.writeString(metaPath, rewritten, StandardCharsets.UTF_8);

        CacheLookupResult<ResourceIndexSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "dep1",
                "entry",
                ResourceIndexSnapshot.codec());

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
                "dep1",
                "entry",
                ResourceIndexSnapshot.codec(),
                VersionedCacheStoreFixtures.sampleResourceIndexSnapshot());

        Thread.sleep(5L);

        CacheLookupResult<ResourceIndexSnapshot> result = store.read(
                CacheModuleId.RESOURCE_INDEX,
                "dep1",
                "entry",
                ResourceIndexSnapshot.codec());

        assertTrue(result.hit());
        assertNotNull(result.metadata());
        assertTrue(result.metadata().lastUsedAtEpochMillis() >= written.lastUsedAtEpochMillis());
    }

    @Test
    void evictsLeastRecentlyUsedEntriesWhenBudgetExceeded() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "first", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot());
        Thread.sleep(5L);
        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "second", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot("example:item/very_large_name_that_pushes_budget.json"));

        long targetBudget = 10L;
        var evicted = store.evictLeastRecentlyUsed(targetBudget, java.util.Optional.of(CacheModuleId.RESOURCE_INDEX));

        assertFalse(evicted.isEmpty());
        assertTrue(evicted.get(0).contains("resource_index"));
    }

    @Test
    void aggregatesUsagePerDependencyDigest() throws Exception {
        VersionedCacheStore store = new VersionedCacheStore(tempDir, 1);
        store.write(CacheModuleId.RESOURCE_INDEX, "dep1", "entry-a", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot());
        store.write(CacheModuleId.RESOURCE_INDEX, "dep2", "entry-b", ResourceIndexSnapshot.codec(), VersionedCacheStoreFixtures.sampleResourceIndexSnapshot("example:item/b.json"));

        Map<String, VersionedCacheStore.UsageStats> usage = store.usageByDependencyDigest(CacheModuleId.RESOURCE_INDEX);

        assertEquals(2, usage.size());
        assertTrue(usage.containsKey("dep1"));
        assertTrue(usage.containsKey("dep2"));
        assertEquals(1L, usage.get("dep1").entryCount());
    }
}
