package io.github.umisetokikaze;

import io.github.umisetokikaze.foundation.cache.CacheEvictionPolicy;
import io.github.umisetokikaze.foundation.cache.CacheModuleId;
import io.github.umisetokikaze.foundation.cache.CacheModuleSettings;
import io.github.umisetokikaze.foundation.cache.CacheModuleSettings.DebugLoggingSetting;
import io.github.umisetokikaze.foundation.cache.CompatibilityMode;
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

    public static final ModConfigSpec.BooleanValue CACHE_MODEL_JSON_PARSE_ENABLED = BUILDER
            .comment("Enable the model JSON parse cache module.")
            .define("cache.modelJsonParse.enabled", true);

    public static final ModConfigSpec.BooleanValue CACHE_MODEL_PARENT_GRAPH_ENABLED = BUILDER
            .comment("Enable the model parent graph cache module.")
            .define("cache.modelParentGraph.enabled", true);

    public static final ModConfigSpec.BooleanValue CACHE_BLOCKSTATE_EXPANSION_ENABLED = BUILDER
            .comment("Enable the blockstate expansion cache module.")
            .define("cache.blockstateExpansion.enabled", true);

    public static final ModConfigSpec.BooleanValue CACHE_ATLAS_PLAN_ENABLED = BUILDER
            .comment("Enable the atlas plan cache module.")
            .define("cache.atlasPlan.enabled", true);

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
            .define("cache.evictionPolicy", "lru", Config::isGlobalEvictionPolicy);

    public static final ModConfigSpec.ConfigValue<String> CACHE_DEBUG_LOGGING = BUILDER
            .comment("Enable verbose cache logging.")
            .define("cache.debugLogging", "disabled", Config::isModuleDebugLoggingSetting);

    public static final ModConfigSpec.ConfigValue<String> COMPATIBILITY_MODE = BUILDER
            .comment("Compatibility posture. Expected values: standard, safe.")
            .define("compatibility.mode", "standard", Config::isCompatibilityMode);

    public static final ModConfigSpec.IntValue CACHE_RESOURCE_INDEX_MAX_MIB = BUILDER
            .comment("Override resource index cache budget in MiB. Use -1 to inherit the global budget.")
            .defineInRange("cache.resourceIndex.maxMiB", -1, -1, 1024 * 1024);

    public static final ModConfigSpec.ConfigValue<String> CACHE_RESOURCE_INDEX_EVICTION_POLICY = BUILDER
            .comment("Override resource index eviction policy. Expected values: inherit, lru, none.")
            .define("cache.resourceIndex.evictionPolicy", "inherit", Config::isModuleEvictionPolicy);

    public static final ModConfigSpec.ConfigValue<String> CACHE_RESOURCE_INDEX_DEBUG_LOGGING = BUILDER
            .comment("Override resource index cache debug logging. Expected values: inherit, enabled, disabled.")
            .define("cache.resourceIndex.debugLogging", "inherit", Config::isModuleDebugLoggingSetting);

    public static final ModConfigSpec.ConfigValue<String> CACHE_RESOURCE_INDEX_COMPATIBILITY_MODE = BUILDER
            .comment("Override resource index compatibility mode. Expected values: inherit, standard, safe.")
            .define("cache.resourceIndex.compatibilityMode", "inherit", Config::isModuleCompatibilityMode);

    public static final ModConfigSpec.IntValue CACHE_NEGATIVE_LOOKUP_MAX_MIB = BUILDER
            .comment("Override negative lookup cache budget in MiB. Use -1 to inherit the global budget.")
            .defineInRange("cache.negativeLookup.maxMiB", -1, -1, 1024 * 1024);

    public static final ModConfigSpec.ConfigValue<String> CACHE_NEGATIVE_LOOKUP_EVICTION_POLICY = BUILDER
            .comment("Override negative lookup eviction policy. Expected values: inherit, lru, none.")
            .define("cache.negativeLookup.evictionPolicy", "inherit", Config::isModuleEvictionPolicy);

    public static final ModConfigSpec.ConfigValue<String> CACHE_NEGATIVE_LOOKUP_DEBUG_LOGGING = BUILDER
            .comment("Override negative lookup cache debug logging. Expected values: inherit, enabled, disabled.")
            .define("cache.negativeLookup.debugLogging", "inherit", Config::isModuleDebugLoggingSetting);

    public static final ModConfigSpec.ConfigValue<String> CACHE_NEGATIVE_LOOKUP_COMPATIBILITY_MODE = BUILDER
            .comment("Override negative lookup compatibility mode. Expected values: inherit, standard, safe.")
            .define("cache.negativeLookup.compatibilityMode", "inherit", Config::isModuleCompatibilityMode);

    public static final ModConfigSpec.IntValue CACHE_MODEL_JSON_PARSE_MAX_MIB = BUILDER
            .comment("Override model JSON parse cache budget in MiB. Use -1 to inherit the global budget.")
            .defineInRange("cache.modelJsonParse.maxMiB", -1, -1, 1024 * 1024);

    public static final ModConfigSpec.ConfigValue<String> CACHE_MODEL_JSON_PARSE_EVICTION_POLICY = BUILDER
            .comment("Override model JSON parse cache eviction policy. Expected values: inherit, lru, none.")
            .define("cache.modelJsonParse.evictionPolicy", "inherit", Config::isModuleEvictionPolicy);

    public static final ModConfigSpec.ConfigValue<String> CACHE_MODEL_JSON_PARSE_DEBUG_LOGGING = BUILDER
            .comment("Override model JSON parse cache debug logging. Expected values: inherit, enabled, disabled.")
            .define("cache.modelJsonParse.debugLogging", "inherit", Config::isModuleDebugLoggingSetting);

    public static final ModConfigSpec.ConfigValue<String> CACHE_MODEL_JSON_PARSE_COMPATIBILITY_MODE = BUILDER
            .comment("Override model JSON parse cache compatibility mode. Expected values: inherit, standard, safe.")
            .define("cache.modelJsonParse.compatibilityMode", "inherit", Config::isModuleCompatibilityMode);

    public static final ModConfigSpec.IntValue CACHE_MODEL_PARENT_GRAPH_MAX_MIB = BUILDER
            .comment("Override model parent graph cache budget in MiB. Use -1 to inherit the global budget.")
            .defineInRange("cache.modelParentGraph.maxMiB", -1, -1, 1024 * 1024);

    public static final ModConfigSpec.ConfigValue<String> CACHE_MODEL_PARENT_GRAPH_EVICTION_POLICY = BUILDER
            .comment("Override model parent graph cache eviction policy. Expected values: inherit, lru, none.")
            .define("cache.modelParentGraph.evictionPolicy", "inherit", Config::isModuleEvictionPolicy);

    public static final ModConfigSpec.ConfigValue<String> CACHE_MODEL_PARENT_GRAPH_DEBUG_LOGGING = BUILDER
            .comment("Override model parent graph cache debug logging. Expected values: inherit, enabled, disabled.")
            .define("cache.modelParentGraph.debugLogging", "inherit", Config::isModuleDebugLoggingSetting);

    public static final ModConfigSpec.ConfigValue<String> CACHE_MODEL_PARENT_GRAPH_COMPATIBILITY_MODE = BUILDER
            .comment("Override model parent graph cache compatibility mode. Expected values: inherit, standard, safe.")
            .define("cache.modelParentGraph.compatibilityMode", "inherit", Config::isModuleCompatibilityMode);

    public static final ModConfigSpec.IntValue CACHE_BLOCKSTATE_EXPANSION_MAX_MIB = BUILDER
            .comment("Override blockstate expansion cache budget in MiB. Use -1 to inherit the global budget.")
            .defineInRange("cache.blockstateExpansion.maxMiB", -1, -1, 1024 * 1024);

    public static final ModConfigSpec.ConfigValue<String> CACHE_BLOCKSTATE_EXPANSION_EVICTION_POLICY = BUILDER
            .comment("Override blockstate expansion cache eviction policy. Expected values: inherit, lru, none.")
            .define("cache.blockstateExpansion.evictionPolicy", "inherit", Config::isModuleEvictionPolicy);

    public static final ModConfigSpec.ConfigValue<String> CACHE_BLOCKSTATE_EXPANSION_DEBUG_LOGGING = BUILDER
            .comment("Override blockstate expansion cache debug logging. Expected values: inherit, enabled, disabled.")
            .define("cache.blockstateExpansion.debugLogging", "inherit", Config::isModuleDebugLoggingSetting);

    public static final ModConfigSpec.ConfigValue<String> CACHE_BLOCKSTATE_EXPANSION_COMPATIBILITY_MODE = BUILDER
            .comment("Override blockstate expansion cache compatibility mode. Expected values: inherit, standard, safe.")
            .define("cache.blockstateExpansion.compatibilityMode", "inherit", Config::isModuleCompatibilityMode);

    public static final ModConfigSpec.IntValue CACHE_ATLAS_PLAN_MAX_MIB = BUILDER
            .comment("Override atlas plan cache budget in MiB. Use -1 to inherit the global budget.")
            .defineInRange("cache.atlasPlan.maxMiB", -1, -1, 1024 * 1024);

    public static final ModConfigSpec.ConfigValue<String> CACHE_ATLAS_PLAN_EVICTION_POLICY = BUILDER
            .comment("Override atlas plan cache eviction policy. Expected values: inherit, lru, none.")
            .define("cache.atlasPlan.evictionPolicy", "inherit", Config::isModuleEvictionPolicy);

    public static final ModConfigSpec.ConfigValue<String> CACHE_ATLAS_PLAN_DEBUG_LOGGING = BUILDER
            .comment("Override atlas plan cache debug logging. Expected values: inherit, enabled, disabled.")
            .define("cache.atlasPlan.debugLogging", "inherit", Config::isModuleDebugLoggingSetting);

    public static final ModConfigSpec.ConfigValue<String> CACHE_ATLAS_PLAN_COMPATIBILITY_MODE = BUILDER
            .comment("Override atlas plan cache compatibility mode. Expected values: inherit, standard, safe.")
            .define("cache.atlasPlan.compatibilityMode", "inherit", Config::isModuleCompatibilityMode);

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
        values.put("cacheModelJsonParseEnabled", String.valueOf(CACHE_MODEL_JSON_PARSE_ENABLED.get()));
        values.put("cacheModelParentGraphEnabled", String.valueOf(CACHE_MODEL_PARENT_GRAPH_ENABLED.get()));
        values.put("cacheBlockstateExpansionEnabled", String.valueOf(CACHE_BLOCKSTATE_EXPANSION_ENABLED.get()));
        values.put("cacheAtlasPlanEnabled", String.valueOf(CACHE_ATLAS_PLAN_ENABLED.get()));
        values.put("cacheCommandsEnabled", String.valueOf(CACHE_COMMANDS_ENABLED.get()));
        values.put("cacheIntegrityStrict", String.valueOf(CACHE_INTEGRITY_STRICT.get()));
        values.put("cacheRebuildOnMiss", String.valueOf(CACHE_REBUILD_ON_MISS.get()));
        values.put("cacheMaxMiB", String.valueOf(CACHE_MAX_MIB.get()));
        values.put("evictionPolicy", EVICTION_POLICY.get());
        values.put("cacheDebugLogging", CACHE_DEBUG_LOGGING.get());
        values.put("compatibilityMode", COMPATIBILITY_MODE.get());
        values.put("cacheResourceIndexMaxMiB", String.valueOf(CACHE_RESOURCE_INDEX_MAX_MIB.get()));
        values.put("cacheResourceIndexEvictionPolicy", CACHE_RESOURCE_INDEX_EVICTION_POLICY.get());
        values.put("cacheResourceIndexDebugLogging", CACHE_RESOURCE_INDEX_DEBUG_LOGGING.get());
        values.put("cacheResourceIndexCompatibilityMode", CACHE_RESOURCE_INDEX_COMPATIBILITY_MODE.get());
        values.put("cacheNegativeLookupMaxMiB", String.valueOf(CACHE_NEGATIVE_LOOKUP_MAX_MIB.get()));
        values.put("cacheNegativeLookupEvictionPolicy", CACHE_NEGATIVE_LOOKUP_EVICTION_POLICY.get());
        values.put("cacheNegativeLookupDebugLogging", CACHE_NEGATIVE_LOOKUP_DEBUG_LOGGING.get());
        values.put("cacheNegativeLookupCompatibilityMode", CACHE_NEGATIVE_LOOKUP_COMPATIBILITY_MODE.get());
        values.put("cacheModelJsonParseMaxMiB", String.valueOf(CACHE_MODEL_JSON_PARSE_MAX_MIB.get()));
        values.put("cacheModelJsonParseEvictionPolicy", CACHE_MODEL_JSON_PARSE_EVICTION_POLICY.get());
        values.put("cacheModelJsonParseDebugLogging", CACHE_MODEL_JSON_PARSE_DEBUG_LOGGING.get());
        values.put("cacheModelJsonParseCompatibilityMode", CACHE_MODEL_JSON_PARSE_COMPATIBILITY_MODE.get());
        values.put("cacheModelParentGraphMaxMiB", String.valueOf(CACHE_MODEL_PARENT_GRAPH_MAX_MIB.get()));
        values.put("cacheModelParentGraphEvictionPolicy", CACHE_MODEL_PARENT_GRAPH_EVICTION_POLICY.get());
        values.put("cacheModelParentGraphDebugLogging", CACHE_MODEL_PARENT_GRAPH_DEBUG_LOGGING.get());
        values.put("cacheModelParentGraphCompatibilityMode", CACHE_MODEL_PARENT_GRAPH_COMPATIBILITY_MODE.get());
        values.put("cacheBlockstateExpansionMaxMiB", String.valueOf(CACHE_BLOCKSTATE_EXPANSION_MAX_MIB.get()));
        values.put("cacheBlockstateExpansionEvictionPolicy", CACHE_BLOCKSTATE_EXPANSION_EVICTION_POLICY.get());
        values.put("cacheBlockstateExpansionDebugLogging", CACHE_BLOCKSTATE_EXPANSION_DEBUG_LOGGING.get());
        values.put("cacheBlockstateExpansionCompatibilityMode", CACHE_BLOCKSTATE_EXPANSION_COMPATIBILITY_MODE.get());
        values.put("cacheAtlasPlanMaxMiB", String.valueOf(CACHE_ATLAS_PLAN_MAX_MIB.get()));
        values.put("cacheAtlasPlanEvictionPolicy", CACHE_ATLAS_PLAN_EVICTION_POLICY.get());
        values.put("cacheAtlasPlanDebugLogging", CACHE_ATLAS_PLAN_DEBUG_LOGGING.get());
        values.put("cacheAtlasPlanCompatibilityMode", CACHE_ATLAS_PLAN_COMPATIBILITY_MODE.get());
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

    public static CacheEvictionPolicy globalEvictionPolicy() {
        return CacheEvictionPolicy.fromConfigValue(EVICTION_POLICY.get(), CacheEvictionPolicy.LRU);
    }

    public static CompatibilityMode globalCompatibilityMode() {
        return CompatibilityMode.fromConfigValue(COMPATIBILITY_MODE.get(), CompatibilityMode.STANDARD);
    }

    public static DebugLoggingSetting globalCacheDebugLogging() {
        return DebugLoggingSetting.fromConfigValue(CACHE_DEBUG_LOGGING.get(), DebugLoggingSetting.DISABLED);
    }

    public static CacheModuleSettings cacheSettings(CacheModuleId module) {
        return switch (module) {
            case RESOURCE_INDEX -> moduleSettings(
                    module,
                    CACHE_RESOURCE_INDEX_ENABLED.get(),
                    CACHE_RESOURCE_INDEX_MAX_MIB.get(),
                    CACHE_RESOURCE_INDEX_EVICTION_POLICY.get(),
                    CACHE_RESOURCE_INDEX_DEBUG_LOGGING.get(),
                    CACHE_RESOURCE_INDEX_COMPATIBILITY_MODE.get());
            case NEGATIVE_LOOKUP -> moduleSettings(
                    module,
                    CACHE_NEGATIVE_LOOKUP_ENABLED.get(),
                    CACHE_NEGATIVE_LOOKUP_MAX_MIB.get(),
                    CACHE_NEGATIVE_LOOKUP_EVICTION_POLICY.get(),
                    CACHE_NEGATIVE_LOOKUP_DEBUG_LOGGING.get(),
                    CACHE_NEGATIVE_LOOKUP_COMPATIBILITY_MODE.get());
            case MODEL_JSON_PARSE -> moduleSettings(
                    module,
                    CACHE_MODEL_JSON_PARSE_ENABLED.get(),
                    CACHE_MODEL_JSON_PARSE_MAX_MIB.get(),
                    CACHE_MODEL_JSON_PARSE_EVICTION_POLICY.get(),
                    CACHE_MODEL_JSON_PARSE_DEBUG_LOGGING.get(),
                    CACHE_MODEL_JSON_PARSE_COMPATIBILITY_MODE.get());
            case MODEL_PARENT_GRAPH -> moduleSettings(
                    module,
                    CACHE_MODEL_PARENT_GRAPH_ENABLED.get(),
                    CACHE_MODEL_PARENT_GRAPH_MAX_MIB.get(),
                    CACHE_MODEL_PARENT_GRAPH_EVICTION_POLICY.get(),
                    CACHE_MODEL_PARENT_GRAPH_DEBUG_LOGGING.get(),
                    CACHE_MODEL_PARENT_GRAPH_COMPATIBILITY_MODE.get());
            case BLOCKSTATE_EXPANSION -> moduleSettings(
                    module,
                    CACHE_BLOCKSTATE_EXPANSION_ENABLED.get(),
                    CACHE_BLOCKSTATE_EXPANSION_MAX_MIB.get(),
                    CACHE_BLOCKSTATE_EXPANSION_EVICTION_POLICY.get(),
                    CACHE_BLOCKSTATE_EXPANSION_DEBUG_LOGGING.get(),
                    CACHE_BLOCKSTATE_EXPANSION_COMPATIBILITY_MODE.get());
            case ATLAS_PLAN -> moduleSettings(
                    module,
                    CACHE_ATLAS_PLAN_ENABLED.get(),
                    CACHE_ATLAS_PLAN_MAX_MIB.get(),
                    CACHE_ATLAS_PLAN_EVICTION_POLICY.get(),
                    CACHE_ATLAS_PLAN_DEBUG_LOGGING.get(),
                    CACHE_ATLAS_PLAN_COMPATIBILITY_MODE.get());
        };
    }

    private static CacheModuleSettings moduleSettings(
            CacheModuleId module,
            boolean enabled,
            int maxMiB,
            String evictionPolicy,
            String debugLogging,
            String compatibilityMode) {
        return new CacheModuleSettings(
                module,
                enabled,
                maxMiB,
                CacheEvictionPolicy.fromConfigValue(evictionPolicy, CacheEvictionPolicy.INHERIT),
                DebugLoggingSetting.fromConfigValue(debugLogging, DebugLoggingSetting.INHERIT),
                CompatibilityMode.fromConfigValue(compatibilityMode, CompatibilityMode.INHERIT));
    }

    private static boolean isGlobalEvictionPolicy(Object value) {
        return value instanceof String stringValue
                && ("lru".equalsIgnoreCase(stringValue) || "none".equalsIgnoreCase(stringValue));
    }

    private static boolean isModuleEvictionPolicy(Object value) {
        return value instanceof String stringValue
                && ("inherit".equalsIgnoreCase(stringValue)
                || "lru".equalsIgnoreCase(stringValue)
                || "none".equalsIgnoreCase(stringValue));
    }

    private static boolean isCompatibilityMode(Object value) {
        return value instanceof String stringValue
                && ("standard".equalsIgnoreCase(stringValue) || "safe".equalsIgnoreCase(stringValue));
    }

    private static boolean isModuleCompatibilityMode(Object value) {
        return value instanceof String stringValue
                && ("inherit".equalsIgnoreCase(stringValue)
                || "standard".equalsIgnoreCase(stringValue)
                || "safe".equalsIgnoreCase(stringValue));
    }

    private static boolean isModuleDebugLoggingSetting(Object value) {
        return value instanceof String stringValue
                && ("inherit".equalsIgnoreCase(stringValue)
                || "enabled".equalsIgnoreCase(stringValue)
                || "disabled".equalsIgnoreCase(stringValue));
    }
}
