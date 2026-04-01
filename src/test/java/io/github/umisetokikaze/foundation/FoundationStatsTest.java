package io.github.umisetokikaze.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationStatsTest {
    @Test
    void recordsStructuredDiagnosticsIntoSnapshotJson() {
        FoundationStats stats = new FoundationStats();

        stats.setWarmColdState("warm");
        stats.recordCacheResult("foundation.pack_fingerprint.marker", true, "HIT", "");
        stats.recordCacheResult("foundation.pack_fingerprint.marker", false, "MISS_INVALIDATED", "fingerprint-changed");
        stats.recordInvalidation("foundation.pack_fingerprint", "FINGERPRINT_CHANGED", "resource-pack-order");
        stats.recordInvalidation("foundation.pack_fingerprint", "FINGERPRINT_CHANGED", "resource-pack-order");
        stats.quarantine("foundation.pack_fingerprint", "IO_FAILURE", "marker-write-failed");
        stats.recordCacheUsage("foundation.pack_fingerprint.marker", 4096L, 3L, 2048L);

        JsonObject root = stats.toJson();

        assertEquals("warm", root.get("warmColdState").getAsString());

        JsonArray cacheResults = root.getAsJsonArray("cacheResults");
        JsonObject cache = cacheResults.get(0).getAsJsonObject();
        assertEquals("foundation.pack_fingerprint.marker", cache.get("module").getAsString());
        assertEquals(1L, cache.get("hits").getAsLong());
        assertEquals(1L, cache.get("misses").getAsLong());
        assertEquals("MISS_INVALIDATED", cache.get("lastReasonCode").getAsString());

        JsonArray invalidations = root.getAsJsonArray("invalidationReasons");
        JsonObject invalidation = invalidations.get(0).getAsJsonObject();
        assertEquals("foundation.pack_fingerprint", invalidation.get("module").getAsString());
        assertEquals("FINGERPRINT_CHANGED", invalidation.get("reasonCode").getAsString());
        assertEquals(2L, invalidation.get("count").getAsLong());

        JsonArray quarantine = root.getAsJsonArray("quarantine");
        JsonObject quarantineState = quarantine.get(0).getAsJsonObject().getAsJsonObject("state");
        assertTrue(quarantineState.get("active").getAsBoolean());
        assertEquals("IO_FAILURE", quarantineState.get("reasonCode").getAsString());

        JsonArray usage = root.getAsJsonArray("cacheUsage");
        JsonObject usageItem = usage.get(0).getAsJsonObject();
        assertEquals(4096L, usageItem.get("bytesUsed").getAsLong());
        assertEquals(3L, usageItem.get("entryCount").getAsLong());
        assertEquals(2048L, usageItem.get("budgetMiB").getAsLong());
    }

    @Test
    void clearQuarantineLeavesInactiveStateVisible() {
        FoundationStats stats = new FoundationStats();

        stats.quarantine("foundation.pack_fingerprint", "IO_FAILURE", "marker-write-failed");
        stats.clearQuarantine("foundation.pack_fingerprint", "RECOVERED", "manual-reset");

        JsonObject root = stats.toJson();
        JsonObject state = root.getAsJsonArray("quarantine")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("state");

        assertFalse(state.get("active").getAsBoolean());
        assertEquals("RECOVERED", state.get("reasonCode").getAsString());
    }
}
