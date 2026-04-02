package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record AtlasPlanSnapshot(
        Set<String> atlasSources,
        Map<String, Set<String>> textureDependenciesByAtlas,
        Set<String> unresolvedTextureReferences,
        Set<String> skippedModels) {

    public AtlasPlanSnapshot {
        atlasSources = Set.copyOf(new LinkedHashSet<>(atlasSources));
        LinkedHashMap<String, Set<String>> normalizedDeps = new LinkedHashMap<>();
        textureDependenciesByAtlas.forEach((key, value) -> normalizedDeps.put(key, Set.copyOf(new LinkedHashSet<>(value))));
        textureDependenciesByAtlas = Map.copyOf(normalizedDeps);
        unresolvedTextureReferences = Set.copyOf(new LinkedHashSet<>(unresolvedTextureReferences));
        skippedModels = Set.copyOf(new LinkedHashSet<>(skippedModels));
    }

    public int totalTextureDependencies() {
        return textureDependenciesByAtlas.values().stream().mapToInt(Set::size).sum();
    }

    public static CachePayloadCodec<AtlasPlanSnapshot> codec() {
        return new CachePayloadCodec<>() {
            @Override
            public JsonElement encode(AtlasPlanSnapshot value) {
                JsonObject root = new JsonObject();
                root.add("atlasSources", SnapshotJson.array(value.atlasSources()));

                JsonObject dependencies = new JsonObject();
                value.textureDependenciesByAtlas().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> dependencies.add(entry.getKey(), SnapshotJson.array(entry.getValue())));
                root.add("textureDependenciesByAtlas", dependencies);

                root.add("unresolvedTextureReferences", SnapshotJson.array(value.unresolvedTextureReferences()));
                root.add("skippedModels", SnapshotJson.array(value.skippedModels()));
                return root;
            }

            @Override
            public AtlasPlanSnapshot decode(JsonElement json) {
                JsonObject root = json.getAsJsonObject();
                Map<String, Set<String>> dependencies = new LinkedHashMap<>();
                JsonObject dependencyJson = root.getAsJsonObject("textureDependenciesByAtlas");
                for (String atlasId : dependencyJson.keySet()) {
                    dependencies.put(atlasId, SnapshotJson.set(dependencyJson.getAsJsonArray(atlasId)));
                }
                return new AtlasPlanSnapshot(
                        SnapshotJson.set(root.getAsJsonArray("atlasSources")),
                        dependencies,
                        SnapshotJson.set(root.getAsJsonArray("unresolvedTextureReferences")),
                        SnapshotJson.set(root.getAsJsonArray("skippedModels")));
            }

            @Override
            public String entryType() {
                return "atlas_plan";
            }
        };
    }
}
