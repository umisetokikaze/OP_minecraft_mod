package io.github.umisetokikaze.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

final class FoundationStats {
    private final ConcurrentHashMap<String, StageAggregate> stageAggregates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheAggregate> cacheAggregates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InvalidationAggregate> invalidationAggregates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QuarantineState> quarantinedModules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheUsageAggregate> cacheUsageAggregates = new ConcurrentHashMap<>();
    private volatile String warmColdState = "cold";

    void setWarmColdState(String warmColdState) {
        this.warmColdState = warmColdState;
    }

    void recordStage(String stageName, long durationNanos, boolean mainThread, String threadName) {
        stageAggregates.computeIfAbsent(stageName, ignored -> new StageAggregate())
                .record(durationNanos, mainThread, threadName);
    }

    void recordCacheResult(String module, boolean hit, String reasonCode, String detail) {
        cacheAggregates.computeIfAbsent(module, ignored -> new CacheAggregate())
                .record(hit, reasonCode, detail);
    }

    void recordInvalidation(String module, String reasonCode, String detail) {
        invalidationAggregates.computeIfAbsent(
                        invalidationKey(module, reasonCode, detail),
                        ignored -> new InvalidationAggregate(module, reasonCode, detail))
                .record();
    }

    void quarantine(String module, String reasonCode, String detail) {
        quarantinedModules.put(module, new QuarantineState(true, reasonCode, detail));
    }

    void clearQuarantine(String module, String reasonCode, String detail) {
        quarantinedModules.put(module, new QuarantineState(false, reasonCode, detail));
    }

    void recordCacheUsage(String module, long bytesUsed, long entryCount, long budgetMiB) {
        cacheUsageAggregates.computeIfAbsent(module, ignored -> new CacheUsageAggregate())
                .record(bytesUsed, entryCount, budgetMiB);
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
        invalidationAggregates.values().stream()
                .sorted(Comparator
                        .comparing(InvalidationAggregate::module)
                        .thenComparing(InvalidationAggregate::reasonCode)
                        .thenComparing(InvalidationAggregate::detail))
                .forEach(item -> invalidations.add(item.toJson()));
        root.add("invalidationReasons", invalidations);

        JsonArray quarantine = new JsonArray();
        quarantinedModules.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("module", entry.getKey());
                    item.add("state", entry.getValue().toJson());
                    quarantine.add(item);
                });
        root.add("quarantine", quarantine);

        JsonArray cacheUsage = new JsonArray();
        cacheUsageAggregates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> cacheUsage.add(entry.getValue().toJson(entry.getKey())));
        root.add("cacheUsage", cacheUsage);
        return root;
    }

    private String invalidationKey(String module, String reasonCode, String detail) {
        return module + "|" + reasonCode + "|" + (detail == null ? "" : detail);
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
        private volatile String lastReasonCode = "NONE";
        private volatile String lastDetail = "";

        void record(boolean hit, String reasonCode, String detail) {
            if (hit) {
                hits.increment();
            } else {
                misses.increment();
            }
            lastReasonCode = reasonCode == null || reasonCode.isBlank() ? "NONE" : reasonCode;
            lastDetail = detail == null ? "" : detail;
        }

        JsonObject toJson(String module) {
            JsonObject json = new JsonObject();
            json.addProperty("module", module);
            json.addProperty("hits", hits.sum());
            json.addProperty("misses", misses.sum());
            json.addProperty("lastReasonCode", lastReasonCode);
            json.addProperty("lastDetail", lastDetail);
            return json;
        }
    }

    private record InvalidationAggregate(String module, String reasonCode, String detail, LongAdder count) {
        private InvalidationAggregate(String module, String reasonCode, String detail) {
            this(module, reasonCode, detail == null ? "" : detail, new LongAdder());
        }

        void record() {
            count.increment();
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("module", module);
            json.addProperty("reasonCode", reasonCode);
            json.addProperty("detail", detail);
            json.addProperty("count", count.sum());
            return json;
        }
    }

    private record QuarantineState(boolean active, String reasonCode, String detail) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("active", active);
            json.addProperty("reasonCode", reasonCode);
            json.addProperty("detail", detail == null ? "" : detail);
            return json;
        }
    }

    private static final class CacheUsageAggregate {
        private final AtomicLong bytesUsed = new AtomicLong();
        private final AtomicLong entryCount = new AtomicLong();
        private final AtomicLong budgetMiB = new AtomicLong();

        void record(long bytesUsed, long entryCount, long budgetMiB) {
            this.bytesUsed.set(bytesUsed);
            this.entryCount.set(entryCount);
            this.budgetMiB.set(budgetMiB);
        }

        JsonObject toJson(String module) {
            JsonObject json = new JsonObject();
            json.addProperty("module", module);
            json.addProperty("bytesUsed", bytesUsed.get());
            json.addProperty("entryCount", entryCount.get());
            json.addProperty("budgetMiB", budgetMiB.get());
            return json;
        }
    }
}
