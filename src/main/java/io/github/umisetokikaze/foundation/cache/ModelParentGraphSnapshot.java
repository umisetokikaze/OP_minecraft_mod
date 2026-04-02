package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ModelParentGraphSnapshot(
        Map<String, String> parentByModel,
        Map<String, List<String>> inheritanceChainByModel,
        Map<String, Map<String, String>> resolvedTexturesByModel,
        Set<String> rootModels,
        Set<String> unresolvedModels,
        Set<String> cyclicModels,
        Set<String> customLoaderModels) {

    public ModelParentGraphSnapshot {
        parentByModel = Map.copyOf(new LinkedHashMap<>(parentByModel));

        LinkedHashMap<String, List<String>> normalizedChains = new LinkedHashMap<>();
        inheritanceChainByModel.forEach((key, value) -> normalizedChains.put(key, List.copyOf(value)));
        inheritanceChainByModel = Map.copyOf(normalizedChains);

        LinkedHashMap<String, Map<String, String>> normalizedTextures = new LinkedHashMap<>();
        resolvedTexturesByModel.forEach((key, value) -> normalizedTextures.put(key, Map.copyOf(new LinkedHashMap<>(value))));
        resolvedTexturesByModel = Map.copyOf(normalizedTextures);

        rootModels = Set.copyOf(new LinkedHashSet<>(rootModels));
        unresolvedModels = Set.copyOf(new LinkedHashSet<>(unresolvedModels));
        cyclicModels = Set.copyOf(new LinkedHashSet<>(cyclicModels));
        customLoaderModels = Set.copyOf(new LinkedHashSet<>(customLoaderModels));
    }

    public static CachePayloadCodec<ModelParentGraphSnapshot> codec() {
        return new CachePayloadCodec<>() {
            @Override
            public JsonElement encode(ModelParentGraphSnapshot value) {
                JsonObject root = new JsonObject();

                JsonObject parents = new JsonObject();
                value.parentByModel().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> parents.addProperty(entry.getKey(), entry.getValue()));
                root.add("parentByModel", parents);

                JsonObject chains = new JsonObject();
                value.inheritanceChainByModel().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> chains.add(entry.getKey(), orderedArray(entry.getValue())));
                root.add("inheritanceChainByModel", chains);

                JsonObject textures = new JsonObject();
                value.resolvedTexturesByModel().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> textures.add(entry.getKey(), SnapshotJson.object(entry.getValue())));
                root.add("resolvedTexturesByModel", textures);

                root.add("rootModels", SnapshotJson.array(value.rootModels()));
                root.add("unresolvedModels", SnapshotJson.array(value.unresolvedModels()));
                root.add("cyclicModels", SnapshotJson.array(value.cyclicModels()));
                root.add("customLoaderModels", SnapshotJson.array(value.customLoaderModels()));
                return root;
            }

            @Override
            public ModelParentGraphSnapshot decode(JsonElement json) {
                JsonObject root = json.getAsJsonObject();

                Map<String, String> parents = SnapshotJson.stringMap(root.getAsJsonObject("parentByModel"));

                Map<String, List<String>> chains = new LinkedHashMap<>();
                JsonObject chainsJson = root.getAsJsonObject("inheritanceChainByModel");
                for (String key : chainsJson.keySet()) {
                    chains.put(key, orderedList(chainsJson.getAsJsonArray(key)));
                }

                Map<String, Map<String, String>> textures = new LinkedHashMap<>();
                JsonObject texturesJson = root.getAsJsonObject("resolvedTexturesByModel");
                for (String key : texturesJson.keySet()) {
                    textures.put(key, SnapshotJson.stringMap(texturesJson.getAsJsonObject(key)));
                }

                return new ModelParentGraphSnapshot(
                        parents,
                        chains,
                        textures,
                        SnapshotJson.set(root.getAsJsonArray("rootModels")),
                        SnapshotJson.set(root.getAsJsonArray("unresolvedModels")),
                        SnapshotJson.set(root.getAsJsonArray("cyclicModels")),
                        SnapshotJson.set(root.getAsJsonArray("customLoaderModels")));
            }

            @Override
            public String entryType() {
                return "model_parent_graph";
            }
        };
    }

    private static JsonArray orderedArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.stream().map(JsonPrimitive::new).forEach(array::add);
        return array;
    }

    private static List<String> orderedList(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(element -> values.add(element.getAsString()));
        return List.copyOf(values);
    }
}
