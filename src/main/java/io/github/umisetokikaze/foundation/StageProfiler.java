package io.github.umisetokikaze.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.umisetokikaze.Config;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

final class StageProfiler {
    private final FoundationStats stats;
    private final BenchmarkHarness benchmarkHarness;
    private final ConcurrentHashMap<String, SessionState> activeSessions = new ConcurrentHashMap<>();

    StageProfiler(FoundationStats stats, BenchmarkHarness benchmarkHarness) {
        this.stats = stats;
        this.benchmarkHarness = benchmarkHarness;
    }

    StageScope begin(String stageName) {
        if (!Config.FOUNDATION_ENABLED.get() || !Config.STAGE_PROFILING_ENABLED.get()) {
            return disabledStageScope();
        }
        return new StageScope(
                stageName,
                System.nanoTime(),
                Thread.currentThread().getName(),
                List.copyOf(activeSessions.values()));
    }

    SessionHandle beginSession(String sessionType, String totalStageName) {
        if (!Config.FOUNDATION_ENABLED.get() || !Config.STAGE_PROFILING_ENABLED.get()) {
            return disabledSessionHandle();
        }

        SessionState state = new SessionState(sessionType, totalStageName);
        SessionState existing = activeSessions.putIfAbsent(sessionType, state);
        if (existing != null) {
            return new SessionHandle(existing, false, begin(totalStageName));
        }
        return new SessionHandle(state, true, begin(totalStageName));
    }

    private static boolean isMainThread(String threadName) {
        return threadName.equals("Render thread") || threadName.equals("main");
    }

    private StageScope disabledStageScope() {
        return new StageScope();
    }

    private SessionHandle disabledSessionHandle() {
        return new SessionHandle();
    }

    private SessionSummary completeSession(SessionState sessionState) {
        sessionState.finishedAt = Instant.now();
        activeSessions.remove(sessionState.sessionType, sessionState);
        return sessionState.toSummary();
    }

    final class StageScope implements StageHandle {
        private final String stageName;
        private final long startedAtNanos;
        private final String threadName;
        private final List<SessionState> sessions;
        private boolean closed;
        private final boolean enabled;

        private StageScope() {
            this.stageName = "";
            this.startedAtNanos = 0L;
            this.threadName = "disabled";
            this.sessions = List.of();
            this.enabled = false;
        }

        private StageScope(String stageName, long startedAtNanos, String threadName, List<SessionState> sessions) {
            this.stageName = stageName;
            this.startedAtNanos = startedAtNanos;
            this.threadName = threadName;
            this.sessions = sessions;
            this.enabled = true;
        }

        @Override
        public void close() {
            if (!enabled || closed) {
                return;
            }
            closed = true;

            long durationNanos = System.nanoTime() - startedAtNanos;
            boolean mainThread = isMainThread(threadName);
            stats.recordStage(stageName, durationNanos, mainThread, threadName);
            benchmarkHarness.recordStage(stageName, durationNanos, mainThread, threadName);
            for (SessionState session : sessions) {
                session.recordStage(stageName, durationNanos, mainThread, threadName);
            }
        }
    }

    final class SessionHandle implements StageHandle {
        private final SessionState sessionState;
        private final boolean owner;
        private final StageScope totalScope;
        private boolean closed;
        private final boolean enabled;

        private SessionHandle() {
            this.sessionState = null;
            this.owner = false;
            this.totalScope = disabledStageScope();
            this.enabled = false;
        }

        private SessionHandle(SessionState sessionState, boolean owner, StageScope totalScope) {
            this.sessionState = sessionState;
            this.owner = owner;
            this.totalScope = totalScope;
            this.enabled = true;
        }

        String sessionId() {
            return enabled && sessionState != null ? sessionState.sessionId : "disabled";
        }

        SessionSummary closeWithExtra(JsonObject extra) {
            if (!enabled || closed) {
                return null;
            }
            closed = true;
            totalScope.close();
            if (!owner || sessionState == null) {
                return null;
            }
            if (extra != null) {
                sessionState.setExtra(extra);
            }
            return completeSession(sessionState);
        }

        @Override
        public void close() {
            closeWithExtra(null);
        }
    }

    static final class SessionSummary {
        private final String sessionType;
        private final String totalStageName;
        private final String sessionId;
        private final Instant startedAt;
        private final Instant finishedAt;
        private final JsonObject extra;
        private final Map<String, SessionStageAggregate> stages;

        private SessionSummary(
                String sessionType,
                String totalStageName,
                String sessionId,
                Instant startedAt,
                Instant finishedAt,
                JsonObject extra,
                Map<String, SessionStageAggregate> stages) {
            this.sessionType = sessionType;
            this.totalStageName = totalStageName;
            this.sessionId = sessionId;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.extra = extra;
            this.stages = stages;
        }

        String sessionType() {
            return sessionType;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("sessionType", sessionType);
            json.addProperty("sessionId", sessionId);
            json.addProperty("startedAt", startedAt.toString());
            json.addProperty("finishedAt", finishedAt.toString());

            SessionStageAggregate totalStage = stages.get(totalStageName);
            long totalNanos = totalStage != null
                    ? totalStage.totalNanos()
                    : stages.values().stream().mapToLong(SessionStageAggregate::totalNanos).max().orElse(0L);
            long mainThreadNanos = totalStage != null
                    ? totalStage.mainThreadNanos()
                    : stages.values().stream().mapToLong(SessionStageAggregate::mainThreadNanos).sum();
            long offThreadNanos = totalStage != null
                    ? totalStage.offThreadNanos()
                    : stages.values().stream().mapToLong(SessionStageAggregate::offThreadNanos).sum();
            json.addProperty("totalMillis", totalNanos / 1_000_000.0D);
            json.addProperty("mainThreadMillis", mainThreadNanos / 1_000_000.0D);
            json.addProperty("offThreadMillis", offThreadNanos / 1_000_000.0D);
            json.addProperty("mainThreadRatio", totalNanos == 0L ? 0.0D : (double) mainThreadNanos / (double) totalNanos);
            json.addProperty("offThreadRatio", totalNanos == 0L ? 0.0D : (double) offThreadNanos / (double) totalNanos);

            JsonArray stagesJson = new JsonArray();
            stages.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> stagesJson.add(entry.getValue().toJson(entry.getKey())));
            json.add("stages", stagesJson);
            json.add("extra", extra.deepCopy());
            return json;
        }
    }

    private static final class SessionState {
        private final String sessionType;
        private final String totalStageName;
        private final String sessionId = UUID.randomUUID().toString();
        private final Instant startedAt = Instant.now();
        private final ConcurrentHashMap<String, SessionStageAggregate> stages = new ConcurrentHashMap<>();
        private volatile Instant finishedAt = startedAt;
        private volatile JsonObject extra = new JsonObject();

        private SessionState(String sessionType, String totalStageName) {
            this.sessionType = sessionType;
            this.totalStageName = totalStageName;
        }

        private void recordStage(String stageName, long durationNanos, boolean mainThread, String threadName) {
            stages.computeIfAbsent(stageName, ignored -> new SessionStageAggregate())
                    .record(durationNanos, mainThread, threadName);
        }

        private void setExtra(JsonObject extra) {
            this.extra = extra.deepCopy();
        }

        private SessionSummary toSummary() {
            return new SessionSummary(
                    sessionType,
                    totalStageName,
                    sessionId,
                    startedAt,
                    finishedAt,
                    extra,
                    Map.copyOf(stages));
        }
    }

    private static final class SessionStageAggregate {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAdder mainThreadNanos = new LongAdder();
        private final LongAdder offThreadNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private volatile String lastThread = "unknown";

        private void record(long durationNanos, boolean mainThread, String threadName) {
            count.increment();
            totalNanos.add(durationNanos);
            if (mainThread) {
                mainThreadNanos.add(durationNanos);
            } else {
                offThreadNanos.add(durationNanos);
            }
            maxNanos.accumulateAndGet(durationNanos, Math::max);
            lastThread = threadName;
        }

        private long totalNanos() {
            return totalNanos.sum();
        }

        private long mainThreadNanos() {
            return mainThreadNanos.sum();
        }

        private long offThreadNanos() {
            return offThreadNanos.sum();
        }

        private JsonObject toJson(String stageName) {
            JsonObject json = new JsonObject();
            long total = totalNanos();
            long main = mainThreadNanos();
            long off = offThreadNanos();
            json.addProperty("stage", stageName);
            json.addProperty("count", count.sum());
            json.addProperty("totalMillis", total / 1_000_000.0D);
            json.addProperty("maxMillis", maxNanos.get() / 1_000_000.0D);
            json.addProperty("mainThreadRatio", total == 0L ? 0.0D : (double) main / (double) total);
            json.addProperty("offThreadRatio", total == 0L ? 0.0D : (double) off / (double) total);
            json.addProperty("lastThread", lastThread);
            return json;
        }
    }
}
