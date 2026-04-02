package io.github.umisetokikaze.foundation.cache;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class VersionedCacheStoreFixtures {
    private VersionedCacheStoreFixtures() {
    }

    static ResourceIndexSnapshot sampleResourceIndexSnapshot() {
        return sampleResourceIndexSnapshot("example:item/a.json");
    }

    static ResourceIndexSnapshot sampleResourceIndexSnapshot(String lookupKey) {
        Map<String, Set<String>> pathIndex = new LinkedHashMap<>();
        pathIndex.put("example", new LinkedHashSet<>(Set.of("models/item/a.json")));
        return new ResourceIndexSnapshot(
                Set.of("example"),
                pathIndex,
                Set.of(lookupKey),
                Map.of(lookupKey, "vanilla"),
                "packs");
    }

    static NegativeLookupSnapshot sampleNegativeLookupSnapshot() {
        return new NegativeLookupSnapshot(
                Set.of("example"),
                Set.of("example:models/item/a.json"),
                "packs");
    }

    static ModelJsonParseSnapshot sampleModelJsonParseSnapshot() {
        return new ModelJsonParseSnapshot(
                Map.of("example:item/a", modelJson("minecraft:item/generated", Map.of("layer0", "example:item/a"))),
                Map.of("example:item/a", "example:models/item/a.json"),
                Set.of());
    }

    static ModelParentGraphSnapshot sampleModelParentGraphSnapshot() {
        return new ModelParentGraphSnapshot(
                Map.of("example:item/a", "minecraft:item/generated"),
                Map.of("example:item/a", java.util.List.of("minecraft:item/generated", "example:item/a")),
                Map.of("example:item/a", Map.of("layer0", "example:item/a")),
                Set.of("minecraft:item/generated"),
                Set.of(),
                Set.of(),
                Set.of());
    }

    static BlockstateExpansionSnapshot sampleBlockstateExpansionSnapshot() {
        return new BlockstateExpansionSnapshot(
                Map.of(
                        "example:test_block",
                        Map.of(
                                "",
                                java.util.List.of(new BlockstateExpansionSnapshot.ModelVariant("example:block/test_block", 0, 0, false, 1)))),
                Map.of());
    }

    static AtlasPlanSnapshot sampleAtlasPlanSnapshot() {
        return new AtlasPlanSnapshot(
                Set.of("minecraft:blocks"),
                Map.of("minecraft:blocks", Set.of("example:block/test_block")),
                Set.of(),
                Set.of("example:item/custom"));
    }

    private static com.google.gson.JsonObject modelJson(String parent, Map<String, String> textures) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("parent", parent);
        com.google.gson.JsonObject textureJson = new com.google.gson.JsonObject();
        textures.forEach(textureJson::addProperty);
        json.add("textures", textureJson);
        return json;
    }
}
