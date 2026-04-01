package io.github.umisetokikaze.foundation;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

final class BenchmarkRunMetrics {
    private Double startupMillis;
    private Double resourceReloadMillis;
    private Double worldJoinTtfcfMillis;
    private Integer worldJoinObservedTicks;
    private Integer worldJoin30sStallCount;
    private Double worldJoin30sMaxFrameMillis;

    BenchmarkRunMetrics withStartupMillis(double value) {
        this.startupMillis = value;
        return this;
    }

    BenchmarkRunMetrics withResourceReloadMillis(double value) {
        this.resourceReloadMillis = value;
        return this;
    }

    BenchmarkRunMetrics withWorldJoinTtfcfMillis(double value) {
        this.worldJoinTtfcfMillis = value;
        return this;
    }

    BenchmarkRunMetrics withWorldJoinObservedTicks(int value) {
        this.worldJoinObservedTicks = value;
        return this;
    }

    BenchmarkRunMetrics withWorldJoin30sStallCount(int value) {
        this.worldJoin30sStallCount = value;
        return this;
    }

    BenchmarkRunMetrics withWorldJoin30sMaxFrameMillis(double value) {
        this.worldJoin30sMaxFrameMillis = value;
        return this;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        addNumber(json, "startupMillis", startupMillis);
        addNumber(json, "resourceReloadMillis", resourceReloadMillis);
        addNumber(json, "worldJoinTtfcfMillis", worldJoinTtfcfMillis);
        addInteger(json, "worldJoinObservedTicks", worldJoinObservedTicks);
        addInteger(json, "worldJoin30sStallCount", worldJoin30sStallCount);
        addNumber(json, "worldJoin30sMaxFrameMillis", worldJoin30sMaxFrameMillis);
        return json;
    }

    private static void addNumber(JsonObject json, String key, Double value) {
        if (value == null) {
            json.add(key, JsonNull.INSTANCE);
        } else {
            json.addProperty(key, value);
        }
    }

    private static void addInteger(JsonObject json, String key, Integer value) {
        if (value == null) {
            json.add(key, JsonNull.INSTANCE);
        } else {
            json.addProperty(key, value);
        }
    }
}
