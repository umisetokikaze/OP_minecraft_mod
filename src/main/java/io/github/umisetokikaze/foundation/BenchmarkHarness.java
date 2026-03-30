package io.github.umisetokikaze.foundation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.momooptimizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

final class BenchmarkHarness {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String sessionId = UUID.randomUUID().toString();
    private final Path rootDirectory;
    private final Path benchmarkFile;
    private final Path diagnosticsFile;
    private final BenchmarkRunDescriptor runDescriptor = BenchmarkRunDescriptor.fromSystemProperties();
    private boolean benchmarkRunStarted;
    private boolean benchmarkRunFinished;
    private Double pendingWorldJoinTtfcfMillis;

    BenchmarkHarness(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.benchmarkFile = rootDirectory.resolve("benchmark-events.jsonl");
        this.diagnosticsFile = rootDirectory.resolve("diagnostics-latest.json");
        ensureDirectories();
    }

    String sessionId() {
        return sessionId;
    }

    void recordStage(String stageName, long durationNanos, boolean mainThread, String threadName) {
        if (!Config.BENCHMARK_HARNESS_ENABLED.get()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("stage", stageName);
        payload.addProperty("durationMillis", durationNanos / 1_000_000.0D);
        payload.addProperty("mainThread", mainThread);
        payload.addProperty("thread", threadName);
        appendEvent("stage", payload);
    }

    void recordResourceReloadSession(StageProfiler.SessionSummary summary) {
        appendEvent("resource_reload_session", summary.toJson());
        if (matchesCase(BenchmarkCaseId.RESOURCE_RELOAD)) {
            BenchmarkRunMetrics metrics = new BenchmarkRunMetrics()
                    .withResourceReloadMillis(summary.totalMillis());
            finishBenchmarkRun(metrics);
        }
    }

    void recordWorldJoinSession(StageProfiler.SessionSummary summary) {
        appendEvent("world_join_session", summary.toJson());
        if (matchesCase(BenchmarkCaseId.WORLD_JOIN_EXISTING)) {
            BenchmarkRunMetrics metrics = new BenchmarkRunMetrics()
                    .withWorldJoinTtfcfMillis(pendingWorldJoinTtfcfMillis == null ? 0.0D : pendingWorldJoinTtfcfMillis)
                    .withWorldJoinObservedTicks(summary.extra().get("observedTicks").getAsInt())
                    .withWorldJoin30sStallCount(summary.extra().get("stallCount").getAsInt())
                    .withWorldJoin30sMaxFrameMillis(summary.extra().get("maxFrameDeltaMillis").getAsDouble());
            finishBenchmarkRun(metrics);
        }
    }

    void recordSnapshot(PackFingerprintSnapshot fingerprintSnapshot, JsonObject statsSnapshot) {
        JsonObject payload = new JsonObject();
        payload.add("fingerprint", fingerprintSnapshot.toJson());
        payload.add("stats", statsSnapshot);
        appendEvent("snapshot", payload);
        if (Config.DIAGNOSTICS_JSON_ENABLED.get()) {
            writePrettyJson(diagnosticsFile, payload);
        }
    }

    void recordWorldJoinWindow(int observedTicks, int stallCount, long maxFrameDeltaNanos) {
        JsonObject payload = new JsonObject();
        payload.addProperty("observedTicks", observedTicks);
        payload.addProperty("stallCount", stallCount);
        payload.addProperty("maxFrameDeltaMillis", maxFrameDeltaNanos / 1_000_000.0D);
        appendEvent("world_join_window", payload);
    }

    void recordReloadObservation(int namespaceCount, long durationNanos) {
        JsonObject payload = new JsonObject();
        payload.addProperty("namespaceCount", namespaceCount);
        payload.addProperty("durationMillis", durationNanos / 1_000_000.0D);
        appendEvent("resource_reload", payload);
    }

    void beginBenchmarkRun(PackFingerprintSnapshot snapshot) {
        if (runDescriptor == null || benchmarkRunStarted) {
            return;
        }
        benchmarkRunStarted = true;
        JsonObject payload = runDescriptor.toJson(snapshot);
        payload.addProperty("runId", sessionId);
        payload.addProperty("actualTemperature", snapshot.executionTemperature());
        appendEvent("benchmark_run_start", payload);

        if (runDescriptor.shaderEnabled()) {
            invalidateBenchmarkRun("shader-enabled");
            return;
        }
        if (!runDescriptor.expectedTemperature().equals(snapshot.executionTemperature())) {
            JsonObject invalidation = runDescriptor.toJson(snapshot);
            invalidation.addProperty("runId", sessionId);
            invalidation.addProperty("reason", "temperature-mismatch");
            invalidation.addProperty("actualTemperature", snapshot.executionTemperature());
            appendEvent("benchmark_run_invalidated", invalidation);
            benchmarkRunFinished = true;
        }
    }

    void finishStartupBenchmark(long durationNanos) {
        if (!matchesCase(BenchmarkCaseId.STARTUP_COLD) && !matchesCase(BenchmarkCaseId.STARTUP_WARM)) {
            return;
        }
        BenchmarkRunMetrics metrics = new BenchmarkRunMetrics()
                .withStartupMillis(durationNanos / 1_000_000.0D);
        finishBenchmarkRun(metrics);
    }

    void noteWorldJoinTtfcf(long durationNanos) {
        pendingWorldJoinTtfcfMillis = durationNanos / 1_000_000.0D;
    }

    BenchmarkCaseId benchmarkCaseId() {
        return runDescriptor == null ? null : runDescriptor.caseId();
    }

    void finishStartupBenchmarkFromNow() {
        if (runDescriptor == null) {
            return;
        }
        finishStartupBenchmark(System.nanoTime() - runDescriptor.createdAtNanos());
    }

    private synchronized void appendEvent(String eventType, JsonObject payload) {
        if (!Config.BENCHMARK_HARNESS_ENABLED.get()) {
            return;
        }

        JsonObject event = new JsonObject();
        event.addProperty("sessionId", sessionId);
        event.addProperty("modId", momooptimizer.MODID);
        event.addProperty("timestamp", Instant.now().toString());
        event.addProperty("eventType", eventType);
        event.add("payload", payload);

        ensureDirectories();
        try {
            Files.writeString(
                    benchmarkFile,
                    GSON.toJson(event) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    Files.exists(benchmarkFile)
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException exception) {
            momooptimizer.LOGGER.warn("Failed to append benchmark event {}", eventType, exception);
        }
    }

    private void writePrettyJson(Path path, JsonObject payload) {
        ensureDirectories();
        try {
            Files.writeString(path, GSON.toJson(payload), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            momooptimizer.LOGGER.warn("Failed to write diagnostics snapshot {}", path, exception);
        }
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create foundation output directory " + rootDirectory, exception);
        }
    }

    private boolean matchesCase(BenchmarkCaseId caseId) {
        return runDescriptor != null
                && runDescriptor.caseId() == caseId
                && benchmarkRunStarted
                && !benchmarkRunFinished;
    }

    private void finishBenchmarkRun(BenchmarkRunMetrics metrics) {
        if (runDescriptor == null || !benchmarkRunStarted || benchmarkRunFinished) {
            return;
        }
        benchmarkRunFinished = true;
        JsonObject payload = metrics.toJson();
        payload.addProperty("runId", sessionId);
        payload.addProperty("caseId", runDescriptor.caseId().value());
        payload.addProperty("variant", runDescriptor.variant());
        payload.addProperty("runIndex", runDescriptor.runIndex());
        appendEvent("benchmark_run_finish", payload);
    }

    private void invalidateBenchmarkRun(String reason) {
        if (runDescriptor == null || benchmarkRunFinished) {
            return;
        }
        benchmarkRunFinished = true;
        JsonObject payload = new JsonObject();
        payload.addProperty("runId", sessionId);
        payload.addProperty("caseId", runDescriptor.caseId().value());
        payload.addProperty("variant", runDescriptor.variant());
        payload.addProperty("runIndex", runDescriptor.runIndex());
        payload.addProperty("reason", reason);
        appendEvent("benchmark_run_invalidated", payload);
    }
}
