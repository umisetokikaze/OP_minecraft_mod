package io.github.umisetokikaze.foundation;

import com.google.gson.JsonObject;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.cache.CacheModuleId;
import io.github.umisetokikaze.foundation.cache.CacheResolution;
import io.github.umisetokikaze.foundation.cache.ModuleCacheResolution;
import io.github.umisetokikaze.foundation.cache.SafeCacheLayer;
import io.github.umisetokikaze.momooptimizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;

public final class ProfilingFoundation {
    private static final ProfilingFoundation INSTANCE = new ProfilingFoundation();
    private static final StageHandle NOOP_HANDLE = () -> {
    };
    private static final String PACK_MARKER_MODULE = "foundation.pack_fingerprint.marker";

    private final Path rootDirectory = FMLPaths.GAMEDIR.get().resolve(momooptimizer.MODID).resolve("foundation");
    private final FoundationStats stats = new FoundationStats();
    private final BenchmarkHarness benchmarkHarness = new BenchmarkHarness(rootDirectory.resolve("benchmark"));
    private final StageProfiler profiler = new StageProfiler(stats, benchmarkHarness);
    private final SafeCacheLayer safeCacheLayer = new SafeCacheLayer(this, rootDirectory.resolve("cache"));

    private volatile PackFingerprintSnapshot currentFingerprint;
    private volatile CacheResolution currentCacheResolution;

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
        return new PackFingerprintService(this, profiler, rootDirectory.resolve("fingerprints"));
    }

    public SafeCacheLayer getSafeCacheLayer() {
        return safeCacheLayer;
    }

    public PackFingerprintSnapshot updateFingerprint(PackFingerprintSnapshot snapshot) {
        this.currentFingerprint = snapshot;
        stats.setWarmColdState(snapshot.executionTemperature());
        recordCacheResult(snapshot, PACK_MARKER_MODULE, "warm".equals(snapshot.executionTemperature()), snapshotMarkerReason(snapshot), "");
        recordCacheUsage(
                snapshot,
                PACK_MARKER_MODULE,
                measureDirectory(rootDirectory.resolve("fingerprints")),
                countDirectoryEntries(rootDirectory.resolve("fingerprints")),
                Config.CACHE_MAX_MIB.get());
        createFingerprintService().persistMarker(snapshot);
        benchmarkHarness.beginBenchmarkRun(snapshot);
        emitDiagnostics();
        return snapshot;
    }

    public PackFingerprintSnapshot currentFingerprint() {
        return currentFingerprint;
    }

    public void updateCacheResolution(CacheResolution resolution) {
        this.currentCacheResolution = resolution;
        emitDiagnostics();
    }

    public Path benchmarkDirectory() {
        return rootDirectory.resolve("benchmark");
    }

    public Path diagnosticsFile() {
        return benchmarkDirectory().resolve("diagnostics-latest.json");
    }

    public void recordReloadObservation(int namespaceCount, long durationNanos) {
        benchmarkHarness.recordReloadObservation(namespaceCount, durationNanos);
        emitDiagnostics();
    }

    public void recordWorldJoinWindow(int observedTicks, int stallCount, long maxFrameDeltaNanos) {
        benchmarkHarness.recordWorldJoinWindow(observedTicks, stallCount, maxFrameDeltaNanos);
        emitDiagnostics();
    }

    public void recordCacheResult(String module, boolean hit, String reasonCode, String detail) {
        recordCacheResult(currentFingerprint, module, hit, reasonCode, detail);
    }

    public void recordCacheResult(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            boolean hit,
            String reasonCode,
            String detail) {
        stats.recordCacheResult(module, hit, reasonCode, detail);
        benchmarkHarness.recordCacheResult(fingerprintSnapshot, module, hit, reasonCode, detail);
        momooptimizer.LOGGER.info(
                "Foundation cache_result module={} fingerprint={} warmCold={} outcome={} reasonCode={} detail={}",
                module,
                fingerprintValue(fingerprintSnapshot),
                warmColdValue(fingerprintSnapshot),
                hit ? "hit" : "miss",
                reasonCode,
                safeDetail(detail));
    }

    public void recordInvalidation(String module, String reasonCode, String detail) {
        recordInvalidation(currentFingerprint, module, reasonCode, detail);
    }

    public void recordInvalidation(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            String reasonCode,
            String detail) {
        stats.recordInvalidation(module, reasonCode, detail);
        benchmarkHarness.recordInvalidation(fingerprintSnapshot, module, reasonCode, detail);
        momooptimizer.LOGGER.info(
                "Foundation invalidation module={} fingerprint={} warmCold={} reasonCode={} detail={}",
                module,
                fingerprintValue(fingerprintSnapshot),
                warmColdValue(fingerprintSnapshot),
                reasonCode,
                safeDetail(detail));
        emitDiagnostics();
    }

    public void recordCacheUsage(String module, long bytesUsed, long entryCount, long budgetMiB) {
        recordCacheUsage(currentFingerprint, module, bytesUsed, entryCount, budgetMiB);
    }

    public void recordIntegrityResult(String module, String integrityState, String reasonCode) {
        stats.recordIntegrityResult(module, integrityState, reasonCode);
    }

    public void recordCacheUsage(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            long bytesUsed,
            long entryCount,
            long budgetMiB) {
        stats.recordCacheUsage(module, bytesUsed, entryCount, budgetMiB);
        benchmarkHarness.recordCacheUsage(fingerprintSnapshot, module, bytesUsed, entryCount, budgetMiB);
        momooptimizer.LOGGER.info(
                "Foundation cache_usage module={} fingerprint={} warmCold={} bytesUsed={} entryCount={} budgetMiB={}",
                module,
                fingerprintValue(fingerprintSnapshot),
                warmColdValue(fingerprintSnapshot),
                bytesUsed,
                entryCount,
                budgetMiB);
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

    public void quarantine(String module, String reasonCode) {
        quarantine(module, reasonCode, "");
    }

    public void quarantine(String module, String reasonCode, String detail) {
        quarantine(currentFingerprint, module, reasonCode, detail);
    }

    public void quarantine(
            PackFingerprintSnapshot fingerprintSnapshot,
            String module,
            String reasonCode,
            String detail) {
        stats.quarantine(module, reasonCode, detail);
        benchmarkHarness.recordQuarantine(fingerprintSnapshot, module, true, reasonCode, detail);
        momooptimizer.LOGGER.info(
                "Foundation quarantine module={} fingerprint={} warmCold={} active=true reasonCode={} detail={}",
                module,
                fingerprintValue(fingerprintSnapshot),
                warmColdValue(fingerprintSnapshot),
                reasonCode,
                safeDetail(detail));
        emitDiagnostics();
    }

    public void clearQuarantine(String module, String reasonCode, String detail) {
        stats.clearQuarantine(module, reasonCode, detail);
        benchmarkHarness.recordQuarantine(currentFingerprint, module, false, reasonCode, detail);
        momooptimizer.LOGGER.info(
                "Foundation quarantine module={} fingerprint={} warmCold={} active=false reasonCode={} detail={}",
                module,
                fingerprintValue(currentFingerprint),
                warmColdValue(currentFingerprint),
                reasonCode,
                safeDetail(detail));
        emitDiagnostics();
    }

    public void emitDiagnostics() {
        PackFingerprintSnapshot snapshot = currentFingerprint;
        if (snapshot == null) {
            return;
        }
        JsonObject statsJson = stats.toJson();
        CacheResolution resolution = currentCacheResolution;
        if (resolution != null) {
            statsJson.add("cacheResolution", cacheResolutionToJson(resolution));
        }
        benchmarkHarness.recordSnapshot(snapshot, statsJson);
        momooptimizer.LOGGER.info(
                "Foundation snapshot fingerprint={} warmCold={} stageCount={} cacheModuleCount={} quarantinedModuleCount={}",
                snapshot.fingerprint(),
                snapshot.executionTemperature(),
                statsJson.getAsJsonArray("stages").size(),
                statsJson.getAsJsonArray("cacheResults").size(),
                activeQuarantineCount(statsJson));
    }

    private String snapshotMarkerReason(PackFingerprintSnapshot snapshot) {
        return "warm".equals(snapshot.executionTemperature()) ? "HIT" : "MISS_NO_ENTRY";
    }

    private String fingerprintValue(PackFingerprintSnapshot snapshot) {
        return snapshot != null ? snapshot.fingerprint() : "unavailable";
    }

    private String warmColdValue(PackFingerprintSnapshot snapshot) {
        return snapshot != null ? snapshot.executionTemperature() : "unknown";
    }

    private String safeDetail(String detail) {
        return detail == null ? "" : detail;
    }

    private int activeQuarantineCount(JsonObject statsJson) {
        int active = 0;
        for (var element : statsJson.getAsJsonArray("quarantine")) {
            JsonObject state = element.getAsJsonObject().getAsJsonObject("state");
            if (state != null && state.get("active").getAsBoolean()) {
                active++;
            }
        }
        return active;
    }

    private JsonObject cacheResolutionToJson(CacheResolution resolution) {
        JsonObject json = new JsonObject();
        com.google.gson.JsonArray changedInputs = new com.google.gson.JsonArray();
        resolution.changedInputs().stream().map(Enum::name).forEach(changedInputs::add);
        json.add("changedInputs", changedInputs);

        com.google.gson.JsonArray modules = new com.google.gson.JsonArray();
        for (CacheModuleId module : CacheModuleId.values()) {
            ModuleCacheResolution moduleResolution = resolution.resolutionFor(module);
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("module", module.id());
            moduleJson.addProperty("dependencyDigest", moduleResolution.dependencyDigest());
            moduleJson.addProperty("reuseAllowed", moduleResolution.reuseAllowed());
            com.google.gson.JsonArray reasons = new com.google.gson.JsonArray();
            moduleResolution.invalidationReasons().stream().map(Enum::name).forEach(reasons::add);
            moduleJson.add("reasons", reasons);
            modules.add(moduleJson);
        }
        json.add("modules", modules);
        return json;
    }

    private long measureDirectory(Path path) {
        if (!Files.exists(path)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException exception) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private long countDirectoryEntries(Path path) {
        if (!Files.exists(path)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException exception) {
            return 0L;
        }
    }
}
