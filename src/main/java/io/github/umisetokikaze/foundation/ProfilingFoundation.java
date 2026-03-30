package io.github.umisetokikaze.foundation;

import com.google.gson.JsonObject;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.momooptimizer;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

public final class ProfilingFoundation {
    private static final ProfilingFoundation INSTANCE = new ProfilingFoundation();
    private static final StageHandle NOOP_HANDLE = () -> {
    };

    private final Path rootDirectory = FMLPaths.GAMEDIR.get().resolve(momooptimizer.MODID).resolve("foundation");
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
        if (!Config.FOUNDATION_ENABLED.get() || !Config.STAGE_PROFILING_ENABLED.get()) {
            return NOOP_HANDLE;
        }
        return profiler.begin(stageName);
    }

    public StageHandle beginReloadSession() {
        if (!Config.FOUNDATION_ENABLED.get() || !Config.STAGE_PROFILING_ENABLED.get()) {
            return NOOP_HANDLE;
        }
        return profiler.beginSession("resource_reload", "foundation.client_reload.total");
    }

    public void finishReloadSession(StageHandle handle, int namespaceCount, long durationNanos) {
        if (!(handle instanceof StageProfiler.SessionHandle sessionHandle)) {
            return;
        }

        JsonObject extra = new JsonObject();
        extra.addProperty("namespaceCount", namespaceCount);
        extra.addProperty("durationMillis", durationNanos / 1_000_000.0D);
        StageProfiler.SessionSummary summary = sessionHandle.closeWithExtra(extra);
        if (summary != null) {
            benchmarkHarness.recordResourceReloadSession(summary);
        }
    }

    public StageHandle beginWorldJoinSession() {
        if (!Config.FOUNDATION_ENABLED.get() || !Config.STAGE_PROFILING_ENABLED.get()) {
            return NOOP_HANDLE;
        }
        return profiler.beginSession("world_join", "foundation.world_join.total");
    }

    public void finishWorldJoinSession(
            StageHandle handle,
            int observedTicks,
            int stallCount,
            long maxFrameDeltaNanos) {
        if (!(handle instanceof StageProfiler.SessionHandle sessionHandle)) {
            return;
        }

        JsonObject extra = new JsonObject();
        extra.addProperty("observedTicks", observedTicks);
        extra.addProperty("stallCount", stallCount);
        extra.addProperty("maxFrameDeltaMillis", maxFrameDeltaNanos / 1_000_000.0D);
        StageProfiler.SessionSummary summary = sessionHandle.closeWithExtra(extra);
        if (summary != null) {
            benchmarkHarness.recordWorldJoinSession(summary);
        }
    }

    public PackFingerprintService createFingerprintService() {
        return new PackFingerprintService(profiler, stats, rootDirectory.resolve("fingerprints"));
    }

    public PackFingerprintSnapshot updateFingerprint(PackFingerprintSnapshot snapshot) {
        this.currentFingerprint = snapshot;
        createFingerprintService().persistMarker(snapshot);
        benchmarkHarness.beginBenchmarkRun(snapshot);
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

    public void noteWorldJoinTtfcf(long durationNanos) {
        benchmarkHarness.noteWorldJoinTtfcf(durationNanos);
    }

    public void finishStartupBenchmark(long durationNanos) {
        benchmarkHarness.finishStartupBenchmark(durationNanos);
    }

    public void finishStartupBenchmarkFromNow() {
        benchmarkHarness.finishStartupBenchmarkFromNow();
    }

    public BenchmarkCaseId benchmarkCaseId() {
        return benchmarkHarness.benchmarkCaseId();
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
