package io.github.umisetokikaze.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

final class FoundationStats {
    private final ConcurrentHashMap<String, StageAggregate> stageAggregates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheAggregate> cacheAggregates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> quarantinedModules = new ConcurrentHashMap<>();
    private final List<String> invalidationReasons = new ArrayList<>();
    private volatile String warmColdState = "cold";

    void setWarmColdState(String warmColdState) {
        this.warmColdState = warmColdState;
    }

    void recordStage(String stageName, long durationNanos, boolean mainThread, String threadName) {
        stageAggregates.computeIfAbsent(stageName, ignored -> new StageAggregate())
                .record(durationNanos, mainThread, threadName);
    }

    void recordCacheResult(String module, boolean hit, String reason) {
        cacheAggregates.computeIfAbsent(module, ignored -> new CacheAggregate()).record(hit);
        if (reason != null && !reason.isBlank()) {
            synchronized (invalidationReasons) {
                invalidationReasons.add(module + ":" + reason);
            }
        }
    }

    void quarantine(String module, String reason) {
        quarantinedModules.put(module, reason);
    }

    JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("warmColdState", warmColdState);

        JsonArray stages = new JsonArray();
        stageAggregates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> stages.add(entry.getValue().toJson(entry.getKey())));
        root.add("stages", stages);

        JsonArray caches = new JsonArray();
        cacheAggregates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> caches.add(entry.getValue().toJson(entry.getKey())));
        root.add("cacheResults", caches);

        JsonArray invalidations = new JsonArray();
        synchronized (invalidationReasons) {
            invalidationReasons.stream()
                    .sorted(Comparator.naturalOrder())
                    .forEach(invalidations::add);
        }
        root.add("invalidationReasons", invalidations);

        JsonArray quarantine = new JsonArray();
        quarantinedModules.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("module", entry.getKey());
                    item.addProperty("reason", entry.getValue());
                    quarantine.add(item);
                });
        root.add("quarantine", quarantine);
        return root;
    }

    private static final class StageAggregate {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAdder mainThreadNanos = new LongAdder();
        private final LongAdder offThreadNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private volatile String lastThreadName = "unknown";

        void record(long durationNanos, boolean mainThread, String threadName) {
            count.increment();
            totalNanos.add(durationNanos);
            if (mainThread) {
                mainThreadNanos.add(durationNanos);
            } else {
                offThreadNanos.add(durationNanos);
            }
            maxNanos.accumulateAndGet(durationNanos, Math::max);
            lastThreadName = threadName;
        }

        JsonObject toJson(String stageName) {
            JsonObject json = new JsonObject();
            long total = totalNanos.sum();
            long main = mainThreadNanos.sum();
            long off = offThreadNanos.sum();
            json.addProperty("stage", stageName);
            json.addProperty("count", count.sum());
            json.addProperty("totalMillis", total / 1_000_000.0D);
            json.addProperty("maxMillis", maxNanos.get() / 1_000_000.0D);
            json.addProperty("mainThreadRatio", total == 0L ? 0.0D : (double) main / (double) total);
            json.addProperty("offThreadRatio", total == 0L ? 0.0D : (double) off / (double) total);
            json.addProperty("lastThread", lastThreadName);
            return json;
        }
    }

    private static final class CacheAggregate {
        private final LongAdder hits = new LongAdder();
        private final LongAdder misses = new LongAdder();

        void record(boolean hit) {
            if (hit) {
                hits.increment();
            } else {
                misses.increment();
            }
        }

        JsonObject toJson(String module) {
            JsonObject json = new JsonObject();
            json.addProperty("module", module);
            json.addProperty("hits", hits.sum());
            json.addProperty("misses", misses.sum());
            return json;
        }
    }
}
