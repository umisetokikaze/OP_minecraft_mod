package io.github.umisetokikaze.foundation;

import com.google.gson.JsonObject;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.momooptimizer;
import java.nio.file.Path;

public final class ProfilingFoundation {
    private static final ProfilingFoundation INSTANCE = new ProfilingFoundation();

    private final Path rootDirectory = Path.of("run", momooptimizer.MODID, "foundation");
    private final FoundationStats stats = new FoundationStats();
    private final BenchmarkHarness benchmarkHarness = new BenchmarkHarness(rootDirectory.resolve("benchmark"));
    private final StageProfiler profiler = new StageProfiler(stats, benchmarkHarness);

    private volatile PackFingerprintSnapshot currentFingerprint;

    public static ProfilingFoundation getInstance() {
        return INSTANCE;
    }

    private ProfilingFoundation() {
    }

    public void onCommonSetup() {
        if (!Config.FOUNDATION_ENABLED.get()) {
            momooptimizer.LOGGER.info("Profiling Foundation disabled by config");
            return;
        }
        try (StageProfiler.StageScope ignored = profiler.begin("foundation.common_setup")) {
            if (Config.DEBUG_LOGGING.get()) {
                momooptimizer.LOGGER.info("Profiling Foundation bootstrap session={}", benchmarkHarness.sessionId());
            }
        }
    }

    public void onServerStarting() {
        if (Config.DEBUG_LOGGING.get()) {
            momooptimizer.LOGGER.info("Profiling Foundation server-side entrypoint active");
        }
    }

    public StageHandle beginStage(String stageName) {
        return profiler.begin(stageName);
    }

    public PackFingerprintService createFingerprintService() {
        return new PackFingerprintService(profiler, stats, rootDirectory.resolve("fingerprints"));
    }

    public PackFingerprintSnapshot updateFingerprint(PackFingerprintSnapshot snapshot) {
        this.currentFingerprint = snapshot;
        createFingerprintService().persistMarker(snapshot);
        emitDiagnostics();
        return snapshot;
    }

    public void recordReloadObservation(int namespaceCount, long durationNanos) {
        benchmarkHarness.recordReloadObservation(namespaceCount, durationNanos);
        emitDiagnostics();
    }

    public void recordWorldJoinWindow(int observedTicks, int stallCount, long maxFrameDeltaNanos) {
        benchmarkHarness.recordWorldJoinWindow(observedTicks, stallCount, maxFrameDeltaNanos);
        emitDiagnostics();
    }

    public void quarantine(String module, String reason) {
        stats.quarantine(module, reason);
        emitDiagnostics();
    }

    public void emitDiagnostics() {
        PackFingerprintSnapshot snapshot = currentFingerprint;
        if (snapshot == null) {
            return;
        }
        JsonObject statsJson = stats.toJson();
        benchmarkHarness.recordSnapshot(snapshot, statsJson);
        momooptimizer.LOGGER.info(
                "Foundation snapshot fingerprint={} warmCold={} stageCount={} quarantineCount={}",
                snapshot.fingerprint(),
                snapshot.executionTemperature(),
                statsJson.getAsJsonArray("stages").size(),
                statsJson.getAsJsonArray("quarantine").size());
    }
}
