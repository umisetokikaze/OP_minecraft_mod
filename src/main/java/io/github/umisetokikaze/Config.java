package io.github.umisetokikaze;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue FOUNDATION_ENABLED = BUILDER
            .comment("Enable the profiling foundation bootstrap.")
            .define("foundation.enabled", true);

    public static final ModConfigSpec.BooleanValue PACK_FINGERPRINT_ENABLED = BUILDER
            .comment("Enable pack fingerprint snapshot generation.")
            .define("foundation.packFingerprint.enabled", true);

    public static final ModConfigSpec.BooleanValue STAGE_PROFILING_ENABLED = BUILDER
            .comment("Enable stage timing and thread split measurements.")
            .define("foundation.stageProfiling.enabled", true);

    public static final ModConfigSpec.BooleanValue BENCHMARK_HARNESS_ENABLED = BUILDER
            .comment("Enable benchmark session logging and JSONL export.")
            .define("foundation.benchmark.enabled", true);

    public static final ModConfigSpec.BooleanValue DIAGNOSTICS_JSON_ENABLED = BUILDER
            .comment("Write diagnostics snapshots as JSON files.")
            .define("foundation.diagnostics.writeJson", true);

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Enable verbose foundation logging.")
            .define("foundation.logging.debug", false);

    public static final ModConfigSpec.IntValue CACHE_MAX_MIB = BUILDER
            .comment("Global cache budget in MiB for persistent cache modules.")
            .defineInRange("cache.maxMiB", 2048, 128, 1024 * 1024);

    public static final ModConfigSpec.BooleanValue CACHE_GLOBAL_ENABLED = BUILDER
            .comment("Enable the persistent safe cache layer globally.")
            .define("cache.enabled", true);

    public static final ModConfigSpec.BooleanValue CACHE_RESOURCE_INDEX_ENABLED = BUILDER
            .comment("Enable the resource index cache module.")
            .define("cache.resourceIndex.enabled", true);

    public static final ModConfigSpec.BooleanValue CACHE_NEGATIVE_LOOKUP_ENABLED = BUILDER
            .comment("Enable the negative lookup cache module.")
            .define("cache.negativeLookup.enabled", true);

    public static final ModConfigSpec.BooleanValue CACHE_COMMANDS_ENABLED = BUILDER
            .comment("Enable cache management commands.")
            .define("cache.commands.enabled", true);

    public static final ModConfigSpec.BooleanValue CACHE_INTEGRITY_STRICT = BUILDER
            .comment("Quarantine a module when cache integrity failures are detected.")
            .define("cache.integrity.strict", true);

    public static final ModConfigSpec.BooleanValue CACHE_REBUILD_ON_MISS = BUILDER
            .comment("Persist rebuilt cache entries after cold-path misses.")
            .define("cache.rebuildOnMiss", true);

    public static final ModConfigSpec.ConfigValue<String> EVICTION_POLICY = BUILDER
            .comment("Eviction policy for persistent cache modules.")
            .define("cache.evictionPolicy", "lru");

    public static final ModConfigSpec.ConfigValue<String> COMPATIBILITY_MODE = BUILDER
            .comment("Compatibility posture. Expected values: standard, safe.")
            .define("compatibility.mode", "standard");

    public static final ModConfigSpec.ConfigValue<String> WORLD_ENTRY_STAGING_INTENSITY = BUILDER
            .comment("World-entry staging intensity placeholder. Expected values: off, conservative, aggressive.")
            .define("worldEntry.stagingIntensity", "conservative");

    public static final ModConfigSpec.IntValue WORLD_ENTRY_OBSERVATION_TICKS = BUILDER
            .comment("Tick window for world-entry stall observation.")
            .defineInRange("worldEntry.observationTicks", 20 * 30, 20, 20 * 300);

    public static final ModConfigSpec.IntValue STALL_THRESHOLD_MS = BUILDER
            .comment("Frame delta threshold treated as a main-thread stall.")
            .defineInRange("benchmark.stallThresholdMs", 250, 16, 5000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RELEVANT_FINGERPRINT_PATHS = BUILDER
            .comment("Relevant resource roots reserved for future file-level hashing.")
            .defineListAllowEmpty(
                    "foundation.packFingerprint.relevantPaths",
                    List.of("assets/**", "data/**", "pack.mcmeta"),
                    () -> "",
                    value -> value instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static Map<String, String> fingerprintInputs() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("foundationEnabled", String.valueOf(FOUNDATION_ENABLED.get()));
        values.put("packFingerprintEnabled", String.valueOf(PACK_FINGERPRINT_ENABLED.get()));
        values.put("stageProfilingEnabled", String.valueOf(STAGE_PROFILING_ENABLED.get()));
        values.put("benchmarkHarnessEnabled", String.valueOf(BENCHMARK_HARNESS_ENABLED.get()));
        values.put("cacheGlobalEnabled", String.valueOf(CACHE_GLOBAL_ENABLED.get()));
        values.put("cacheResourceIndexEnabled", String.valueOf(CACHE_RESOURCE_INDEX_ENABLED.get()));
        values.put("cacheNegativeLookupEnabled", String.valueOf(CACHE_NEGATIVE_LOOKUP_ENABLED.get()));
        values.put("cacheCommandsEnabled", String.valueOf(CACHE_COMMANDS_ENABLED.get()));
        values.put("cacheIntegrityStrict", String.valueOf(CACHE_INTEGRITY_STRICT.get()));
        values.put("cacheRebuildOnMiss", String.valueOf(CACHE_REBUILD_ON_MISS.get()));
        values.put("cacheMaxMiB", String.valueOf(CACHE_MAX_MIB.get()));
        values.put("evictionPolicy", EVICTION_POLICY.get());
        values.put("compatibilityMode", COMPATIBILITY_MODE.get());
        values.put("worldEntryStagingIntensity", WORLD_ENTRY_STAGING_INTENSITY.get());
        values.put("relevantFingerprintPaths", RELEVANT_FINGERPRINT_PATHS.get().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        return values;
    }

    public static Map<String, String> diagnosticsInputs() {
        Map<String, String> values = new LinkedHashMap<>(fingerprintInputs());
        values.put("diagnosticsJsonEnabled", String.valueOf(DIAGNOSTICS_JSON_ENABLED.get()));
        values.put("debugLogging", String.valueOf(DEBUG_LOGGING.get()));
        values.put("worldEntryObservationTicks", String.valueOf(WORLD_ENTRY_OBSERVATION_TICKS.get()));
        values.put("stallThresholdMs", String.valueOf(STALL_THRESHOLD_MS.get()));
        return values;
    }

    public static Map<String, String> toFingerprintSettings() {
        return Map.copyOf(fingerprintInputs());
    }
}
