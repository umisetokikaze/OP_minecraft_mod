package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record ModelJsonParseSnapshot(
        Map<String, JsonObject> modelsById,
        Map<String, String> sourcePathById,
        Set<String> customLoaderModels) {

    public ModelJsonParseSnapshot {
        LinkedHashMap<String, JsonObject> normalizedModels = new LinkedHashMap<>();
        modelsById.forEach((key, value) -> normalizedModels.put(key, value == null ? new JsonObject() : value.deepCopy()));
        modelsById = Map.copyOf(normalizedModels);
        sourcePathById = Map.copyOf(new LinkedHashMap<>(sourcePathById));
        customLoaderModels = Set.copyOf(new LinkedHashSet<>(customLoaderModels));
    }

    public JsonObject model(String modelId) {
        JsonObject model = modelsById.get(modelId);
        return model == null ? null : model.deepCopy();
    }

    public static CachePayloadCodec<ModelJsonParseSnapshot> codec() {
        return new CachePayloadCodec<>() {
            @Override
            public JsonElement encode(ModelJsonParseSnapshot value) {
                JsonObject root = new JsonObject();
                JsonObject models = new JsonObject();
                value.modelsById().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> models.add(entry.getKey(), entry.getValue().deepCopy()));
                root.add("modelsById", models);

                JsonObject sources = new JsonObject();
                value.sourcePathById().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> sources.addProperty(entry.getKey(), entry.getValue()));
                root.add("sourcePathById", sources);
                root.add("customLoaderModels", SnapshotJson.array(value.customLoaderModels()));
                return root;
            }

            @Override
            public ModelJsonParseSnapshot decode(JsonElement json) {
                JsonObject root = json.getAsJsonObject();
                Map<String, JsonObject> models = new LinkedHashMap<>();
                JsonObject modelsJson = root.getAsJsonObject("modelsById");
                for (String key : modelsJson.keySet()) {
                    models.put(key, modelsJson.getAsJsonObject(key).deepCopy());
                }

                Map<String, String> sources = new LinkedHashMap<>();
                JsonObject sourcesJson = root.getAsJsonObject("sourcePathById");
                for (String key : sourcesJson.keySet()) {
                    sources.put(key, sourcesJson.get(key).getAsString());
                }

                return new ModelJsonParseSnapshot(
                        models,
                        sources,
                        SnapshotJson.set(root.getAsJsonArray("customLoaderModels")));
            }

            @Override
            public String entryType() {
                return "model_json_parse";
            }
        };
    }
}
