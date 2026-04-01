package io.github.umisetokikaze.foundation;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunMetricsTest {
    @Test
    void serializesConfiguredMetrics() {
        JsonObject json = new BenchmarkRunMetrics()
                .withStartupMillis(123.5D)
                .withResourceReloadMillis(88.0D)
                .withWorldJoinTtfcfMillis(456.0D)
                .withWorldJoinObservedTicks(600)
                .withWorldJoin30sStallCount(2)
                .withWorldJoin30sMaxFrameMillis(48.25D)
                .toJson();

        assertEquals(123.5D, json.get("startupMillis").getAsDouble());
        assertEquals(88.0D, json.get("resourceReloadMillis").getAsDouble());
        assertEquals(456.0D, json.get("worldJoinTtfcfMillis").getAsDouble());
        assertEquals(600, json.get("worldJoinObservedTicks").getAsInt());
        assertEquals(2, json.get("worldJoin30sStallCount").getAsInt());
        assertEquals(48.25D, json.get("worldJoin30sMaxFrameMillis").getAsDouble());
    }

    @Test
    void serializesMissingMetricsAsJsonNull() {
        JsonObject json = new BenchmarkRunMetrics().toJson();

        assertTrue(json.get("startupMillis").isJsonNull());
        assertTrue(json.get("resourceReloadMillis").isJsonNull());
        assertTrue(json.get("worldJoinTtfcfMillis").isJsonNull());
        assertTrue(json.get("worldJoinObservedTicks").isJsonNull());
        assertTrue(json.get("worldJoin30sStallCount").isJsonNull());
        assertTrue(json.get("worldJoin30sMaxFrameMillis").isJsonNull());
    }
}
