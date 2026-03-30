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
import java.util.function.BooleanSupplier;

final class BenchmarkHarness {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String sessionId = UUID.randomUUID().toString();
    private final Path rootDirectory;
    private final Path benchmarkFile;
    private final Path diagnosticsFile;
    private final BooleanSupplier benchmarkEnabled;
    private final BooleanSupplier diagnosticsJsonEnabled;

    BenchmarkHarness(Path rootDirectory) {
        this(rootDirectory, () -> Config.BENCHMARK_HARNESS_ENABLED.get(), () -> Config.DIAGNOSTICS_JSON_ENABLED.get());
    }

    BenchmarkHarness(Path rootDirectory, BooleanSupplier benchmarkEnabled, BooleanSupplier diagnosticsJsonEnabled) {
        this.rootDirectory = rootDirectory;
        this.benchmarkFile = rootDirectory.resolve("benchmark-events.jsonl");
        this.diagnosticsFile = rootDirectory.resolve("diagnostics-latest.json");
        this.benchmarkEnabled = benchmarkEnabled;
        this.diagnosticsJsonEnabled = diagnosticsJsonEnabled;
        ensureDirectories();
    }

    String sessionId() {
        return sessionId;
    }

    void recordStage(String stageName, long durationNanos, boolean mainThread, String threadName) {
        if (!benchmarkEnabled.getAsBoolean()) {
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
    }

    void recordWorldJoinSession(StageProfiler.SessionSummary summary) {
        appendEvent("world_join_session", summary.toJson());
    }

    void recordSnapshot(PackFingerprintSnapshot fingerprintSnapshot, JsonObject statsSnapshot) {
        JsonObject payload = new JsonObject();
        payload.add("fingerprint", fingerprintSnapshot.toJson());
        payload.add("stats", statsSnapshot);
        appendEvent("snapshot", payload);
        if (diagnosticsJsonEnabled.getAsBoolean()) {
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

    void recordCacheResult(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            boolean hit,
            String reasonCode,
            String detail) {
        JsonObject payload = createDiagnosticPayload(fingerprintSnapshot, module);
        payload.addProperty("outcome", hit ? "hit" : "miss");
        payload.addProperty("reasonCode", reasonCode);
        payload.addProperty("detail", detail == null ? "" : detail);
        appendEvent("cache_result", payload);
    }

    void recordInvalidation(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            String reasonCode,
            String detail) {
        JsonObject payload = createDiagnosticPayload(fingerprintSnapshot, module);
        payload.addProperty("reasonCode", reasonCode);
        payload.addProperty("detail", detail == null ? "" : detail);
        appendEvent("invalidation", payload);
    }

    void recordCacheUsage(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            long bytesUsed,
            long entryCount,
            long budgetMiB) {
        JsonObject payload = createDiagnosticPayload(fingerprintSnapshot, module);
        payload.addProperty("bytesUsed", bytesUsed);
        payload.addProperty("entryCount", entryCount);
        payload.addProperty("budgetMiB", budgetMiB);
        appendEvent("cache_usage", payload);
    }

    void recordQuarantine(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            boolean active,
            String reasonCode,
            String detail) {
        JsonObject payload = createDiagnosticPayload(fingerprintSnapshot, module);
        payload.addProperty("active", active);
        payload.addProperty("reasonCode", reasonCode);
        payload.addProperty("detail", detail == null ? "" : detail);
        appendEvent("quarantine", payload);
    }

    private synchronized void appendEvent(String eventType, JsonObject payload) {
        if (!benchmarkEnabled.getAsBoolean()) {
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
            Files.writeString(path, PRETTY_GSON.toJson(payload), StandardCharsets.UTF_8);
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

    private JsonObject createDiagnosticPayload(PackFingerprintSnapshot fingerprintSnapshot, String module) {
        JsonObject payload = new JsonObject();
        payload.addProperty("fingerprint", fingerprintSnapshot != null ? fingerprintSnapshot.fingerprint() : "unavailable");
        payload.addProperty("warmCold", fingerprintSnapshot != null ? fingerprintSnapshot.executionTemperature() : "unknown");
        payload.addProperty("module", module);
        return payload;
    }
}
