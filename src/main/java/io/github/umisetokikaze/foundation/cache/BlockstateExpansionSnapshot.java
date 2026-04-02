package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BlockstateExpansionSnapshot(
        Map<String, Map<String, List<ModelVariant>>> variantExpansionsByBlockstate,
        Map<String, List<MultipartCase>> multipartExpansionsByBlockstate) {

    public BlockstateExpansionSnapshot {
        variantExpansionsByBlockstate = normalizeVariantMap(variantExpansionsByBlockstate);
        multipartExpansionsByBlockstate = normalizeMultipartMap(multipartExpansionsByBlockstate);
    }

    public int totalVariantKeys() {
        return variantExpansionsByBlockstate.values().stream().mapToInt(Map::size).sum();
    }

    public int totalMultipartCases() {
        return multipartExpansionsByBlockstate.values().stream().mapToInt(List::size).sum();
    }

    public static CachePayloadCodec<BlockstateExpansionSnapshot> codec() {
        return new CachePayloadCodec<>() {
            @Override
            public JsonElement encode(BlockstateExpansionSnapshot value) {
                JsonObject root = new JsonObject();

                JsonObject variants = new JsonObject();
                value.variantExpansionsByBlockstate().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            JsonObject variantObject = new JsonObject();
                            entry.getValue().entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .forEach(variant -> variantObject.add(variant.getKey(), encodeVariants(variant.getValue())));
                            variants.add(entry.getKey(), variantObject);
                        });
                root.add("variantExpansionsByBlockstate", variants);

                JsonObject multipart = new JsonObject();
                value.multipartExpansionsByBlockstate().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            JsonArray array = new JsonArray();
                            entry.getValue().forEach(item -> array.add(item.toJson()));
                            multipart.add(entry.getKey(), array);
                        });
                root.add("multipartExpansionsByBlockstate", multipart);
                return root;
            }

            @Override
            public BlockstateExpansionSnapshot decode(JsonElement json) {
                JsonObject root = json.getAsJsonObject();

                Map<String, Map<String, List<ModelVariant>>> variants = new LinkedHashMap<>();
                JsonObject variantsJson = root.getAsJsonObject("variantExpansionsByBlockstate");
                for (String blockstateId : variantsJson.keySet()) {
                    JsonObject variantObject = variantsJson.getAsJsonObject(blockstateId);
                    Map<String, List<ModelVariant>> variantEntries = new LinkedHashMap<>();
                    for (String variantKey : variantObject.keySet()) {
                        variantEntries.put(variantKey, decodeVariants(variantObject.getAsJsonArray(variantKey)));
                    }
                    variants.put(blockstateId, variantEntries);
                }

                Map<String, List<MultipartCase>> multipart = new LinkedHashMap<>();
                JsonObject multipartJson = root.getAsJsonObject("multipartExpansionsByBlockstate");
                for (String blockstateId : multipartJson.keySet()) {
                    List<MultipartCase> cases = new ArrayList<>();
                    for (JsonElement element : multipartJson.getAsJsonArray(blockstateId)) {
                        cases.add(MultipartCase.fromJson(element.getAsJsonObject()));
                    }
                    multipart.put(blockstateId, cases);
                }

                return new BlockstateExpansionSnapshot(variants, multipart);
            }

            @Override
            public String entryType() {
                return "blockstate_expansion";
            }
        };
    }

    public record ModelVariant(
            String modelId,
            int xRotation,
            int yRotation,
            boolean uvLock,
            int weight) {

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("modelId", modelId);
            json.addProperty("xRotation", xRotation);
            json.addProperty("yRotation", yRotation);
            json.addProperty("uvLock", uvLock);
            json.addProperty("weight", weight);
            return json;
        }

        static ModelVariant fromJson(JsonObject json) {
            return new ModelVariant(
                    json.get("modelId").getAsString(),
                    json.get("xRotation").getAsInt(),
                    json.get("yRotation").getAsInt(),
                    json.get("uvLock").getAsBoolean(),
                    json.get("weight").getAsInt());
        }
    }

    public record MultipartCase(
            String whenKey,
            List<ModelVariant> applyVariants) {

        public MultipartCase {
            applyVariants = List.copyOf(applyVariants);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("whenKey", whenKey);
            json.add("applyVariants", encodeVariants(applyVariants));
            return json;
        }

        static MultipartCase fromJson(JsonObject json) {
            return new MultipartCase(
                    json.get("whenKey").getAsString(),
                    decodeVariants(json.getAsJsonArray("applyVariants")));
        }
    }

    private static Map<String, Map<String, List<ModelVariant>>> normalizeVariantMap(
            Map<String, Map<String, List<ModelVariant>>> input) {
        LinkedHashMap<String, Map<String, List<ModelVariant>>> normalized = new LinkedHashMap<>();
        input.forEach((blockstateId, variants) -> {
            LinkedHashMap<String, List<ModelVariant>> variantEntries = new LinkedHashMap<>();
            variants.forEach((variantKey, values) -> variantEntries.put(variantKey, List.copyOf(values)));
            normalized.put(blockstateId, Map.copyOf(variantEntries));
        });
        return Map.copyOf(normalized);
    }

    private static Map<String, List<MultipartCase>> normalizeMultipartMap(
            Map<String, List<MultipartCase>> input) {
        LinkedHashMap<String, List<MultipartCase>> normalized = new LinkedHashMap<>();
        input.forEach((blockstateId, values) -> normalized.put(blockstateId, List.copyOf(values)));
        return Map.copyOf(normalized);
    }

    private static JsonArray encodeVariants(List<ModelVariant> values) {
        JsonArray array = new JsonArray();
        values.forEach(value -> array.add(value.toJson()));
        return array;
    }

    private static List<ModelVariant> decodeVariants(JsonArray array) {
        List<ModelVariant> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(ModelVariant.fromJson(element.getAsJsonObject()));
        }
        return List.copyOf(values);
    }
}
