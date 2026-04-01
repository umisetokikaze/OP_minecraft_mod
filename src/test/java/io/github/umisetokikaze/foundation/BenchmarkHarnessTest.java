package io.github.umisetokikaze.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkHarnessTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        for (String key : List.of(
                "momooptimizer.benchmark.caseId",
                "momooptimizer.benchmark.variant",
                "momooptimizer.benchmark.runIndex",
                "momooptimizer.benchmark.temperature",
                "momooptimizer.benchmark.worldId",
                "momooptimizer.benchmark.shaderEnabled"
        )) {
            System.clearProperty(key);
        }
    }

    @Test
    void writesStructuredDiagnosticEventsToJsonl() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness(tempDir, () -> true, () -> true);
        PackFingerprintSnapshot snapshot = new PackFingerprintSnapshot(
                "abc123",
                "warm",
                "1.21.1",
                "21.1.122",
                List.of(),
                List.of(),
                Map.of("cacheMaxMiB", "2048"));

        harness.recordCacheResult(snapshot, "foundation.pack_fingerprint.marker", true, "HIT", "");
        harness.recordInvalidation(snapshot, "foundation.pack_fingerprint", "FINGERPRINT_CHANGED", "resource-pack-order");
        harness.recordCacheUsage(snapshot, "foundation.pack_fingerprint.marker", 128L, 2L, 2048L);
        harness.recordQuarantine(snapshot, "foundation.pack_fingerprint", true, "IO_FAILURE", "marker-write-failed");

        Path jsonl = tempDir.resolve("benchmark-events.jsonl");
        List<String> lines = Files.readAllLines(jsonl);

        assertEquals(4, lines.size());
        assertTrue(lines.get(0).contains("\"eventType\":\"cache_result\""));
        assertTrue(lines.get(0).contains("\"fingerprint\":\"abc123\""));
        assertTrue(lines.get(1).contains("\"eventType\":\"invalidation\""));
        assertTrue(lines.get(1).contains("\"reasonCode\":\"FINGERPRINT_CHANGED\""));
        assertTrue(lines.get(2).contains("\"eventType\":\"cache_usage\""));
        assertTrue(lines.get(2).contains("\"bytesUsed\":128"));
        assertTrue(lines.get(3).contains("\"eventType\":\"quarantine\""));
        assertTrue(lines.get(3).contains("\"active\":true"));
    }

    @Test
    void writesSnapshotWithStructuredStats() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness(tempDir, () -> true, () -> true);
        PackFingerprintSnapshot snapshot = new PackFingerprintSnapshot(
                "abc123",
                "cold",
                "1.21.1",
                "21.1.122",
                List.of(),
                List.of(),
                Map.of("cacheMaxMiB", "2048"));
        JsonObject stats = new JsonObject();
        stats.addProperty("warmColdState", "cold");
        stats.add("cacheResults", new JsonArray());
        stats.add("quarantine", new JsonArray());
        stats.add("stages", new JsonArray());
        stats.add("cacheUsage", new JsonArray());
        stats.add("invalidationReasons", new JsonArray());

        harness.recordSnapshot(snapshot, stats);

        Path diagnostics = tempDir.resolve("diagnostics-latest.json");
        String content = Files.readString(diagnostics);

        assertTrue(content.contains("\"fingerprint\": \"abc123\""));
        assertTrue(content.contains("\"warmColdState\": \"cold\""));
    }

    @Test
    void recordsBenchmarkRunStartAndFinishForMatchingStartupCase() throws Exception {
        System.setProperty("momooptimizer.benchmark.caseId", "startup_warm");

        BenchmarkHarness harness = new BenchmarkHarness(tempDir, () -> true, () -> true);
        PackFingerprintSnapshot snapshot = new PackFingerprintSnapshot(
                "abc123",
                "warm",
                "1.21.1",
                "21.1.122",
                List.of(),
                List.of(),
                Map.of("cacheMaxMiB", "2048"));

        harness.beginBenchmarkRun(snapshot);
        harness.finishStartupBenchmark(12_500_000L);

        String content = Files.readString(tempDir.resolve("benchmark-events.jsonl"));

        assertTrue(content.contains("\"eventType\":\"benchmark_run_start\""));
        assertTrue(content.contains("\"eventType\":\"benchmark_run_finish\""));
        assertTrue(content.contains("\"caseId\":\"startup_warm\""));
        assertTrue(content.contains("\"startupMillis\":12.5"));
    }

    @Test
    void invalidatesBenchmarkRunWhenTemperatureDoesNotMatchExpectedCase() throws Exception {
        System.setProperty("momooptimizer.benchmark.caseId", "startup_cold");

        BenchmarkHarness harness = new BenchmarkHarness(tempDir, () -> true, () -> true);
        PackFingerprintSnapshot snapshot = new PackFingerprintSnapshot(
                "abc123",
                "warm",
                "1.21.1",
                "21.1.122",
                List.of(),
                List.of(),
                Map.of("cacheMaxMiB", "2048"));

        harness.beginBenchmarkRun(snapshot);
        harness.finishStartupBenchmark(12_500_000L);

        String content = Files.readString(tempDir.resolve("benchmark-events.jsonl"));

        assertTrue(content.contains("\"eventType\":\"benchmark_run_invalidated\""));
        assertTrue(content.contains("\"reason\":\"temperature-mismatch\""));
    }
}
