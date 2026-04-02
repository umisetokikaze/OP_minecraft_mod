package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonObject;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheResolverTest {
    @Test
    void allowsReuseWhenOnlyIrrelevantSettingChanges() {
        CacheResolver resolver = new CacheResolver();

        CacheResolution resolution = resolver.resolve(
                sampleSnapshot(Map.of("worldEntryStagingIntensity", "conservative", "compatibilityMode", "standard"), Map.of("assets/example/models/item/a.json", "1111")),
                sampleSnapshot(Map.of("worldEntryStagingIntensity", "aggressive", "compatibilityMode", "standard"), Map.of("assets/example/models/item/a.json", "1111")));

        assertTrue(resolution.changedInputs().contains(InvalidationReason.SETTINGS_CHANGED));
        assertTrue(resolution.resolutionFor(CacheModuleId.RESOURCE_INDEX).reuseAllowed());
        assertTrue(resolution.resolutionFor(CacheModuleId.MODEL_JSON_PARSE).reuseAllowed());
    }

    @Test
    void invalidatesResourceModulesWhenRelevantFilesChange() {
        CacheResolver resolver = new CacheResolver();

        CacheResolution resolution = resolver.resolve(
                sampleSnapshot(Map.of("compatibilityMode", "standard"), Map.of("assets/example/models/item/a.json", "1111")),
                sampleSnapshot(Map.of("compatibilityMode", "standard"), Map.of("assets/example/models/item/a.json", "2222")));

        assertFalse(resolution.resolutionFor(CacheModuleId.RESOURCE_INDEX).reuseAllowed());
        assertEquals(InvalidationReason.RELEVANT_FILES_CHANGED, resolution.resolutionFor(CacheModuleId.RESOURCE_INDEX).primaryReason());
        assertFalse(resolution.resolutionFor(CacheModuleId.MODEL_PARENT_GRAPH).reuseAllowed());
        assertFalse(resolution.resolutionFor(CacheModuleId.BLOCKSTATE_EXPANSION).reuseAllowed());
        assertFalse(resolution.resolutionFor(CacheModuleId.ATLAS_PLAN).reuseAllowed());
    }

    @Test
    void invalidatesOnlyTargetModuleWhenModuleSpecificSettingChanges() {
        CacheResolver resolver = new CacheResolver();

        CacheResolution resolution = resolver.resolve(
                sampleSnapshot(Map.of(
                        "compatibilityMode", "standard",
                        "cacheResourceIndexCompatibilityMode", "inherit",
                        "cacheNegativeLookupCompatibilityMode", "inherit"), Map.of("assets/example/models/item/a.json", "1111")),
                sampleSnapshot(Map.of(
                        "compatibilityMode", "standard",
                        "cacheResourceIndexCompatibilityMode", "safe",
                        "cacheNegativeLookupCompatibilityMode", "inherit"), Map.of("assets/example/models/item/a.json", "1111")));

        assertFalse(resolution.resolutionFor(CacheModuleId.RESOURCE_INDEX).reuseAllowed());
        assertTrue(resolution.resolutionFor(CacheModuleId.NEGATIVE_LOOKUP).reuseAllowed());
        assertTrue(resolution.resolutionFor(CacheModuleId.MODEL_JSON_PARSE).reuseAllowed());
        assertEquals(InvalidationReason.SETTINGS_CHANGED, resolution.resolutionFor(CacheModuleId.RESOURCE_INDEX).primaryReason());
    }

    private PackFingerprintSnapshot sampleSnapshot(Map<String, String> settings, Map<String, String> relevantFiles) {
        JsonObject mod = new JsonObject();
        mod.addProperty("modId", "example");
        mod.addProperty("version", "1.0.0");
        mod.addProperty("file", "mods/example.jar");
        mod.addProperty("fileHash", "abcd");

        JsonObject pack = new JsonObject();
        pack.addProperty("id", "vanilla");
        pack.addProperty("title", "Vanilla");
        pack.addProperty("order", 0);

        return new PackFingerprintSnapshot(
                "fp",
                "warm",
                "1.21.1",
                "21.1.122",
                List.of(mod),
                List.of(pack),
                relevantFiles,
                settings,
                Map.of(
                        "resource_index", 2,
                        "negative_lookup", 2,
                        "model_json_parse", 1,
                        "model_parent_graph", 1,
                        "blockstate_expansion", 1,
                        "atlas_plan", 1),
                "cfg");
    }
}
