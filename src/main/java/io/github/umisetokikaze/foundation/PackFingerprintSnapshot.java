package io.github.umisetokikaze.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PackFingerprintSnapshot(
        String fingerprint,
        String executionTemperature,
        String minecraftVersion,
        String neoForgeVersion,
        List<JsonObject> mods,
        List<JsonObject> resourcePacks,
        Map<String, String> relevantFileHashes,
        Map<String, String> configInputs,
        Map<String, Integer> cacheSchemaVersions,
        String configInputsDigest) {

    public PackFingerprintSnapshot {
        mods = List.copyOf(mods);
        resourcePacks = List.copyOf(resourcePacks);
        relevantFileHashes = Map.copyOf(new LinkedHashMap<>(relevantFileHashes));
        configInputs = Map.copyOf(new LinkedHashMap<>(configInputs));
        cacheSchemaVersions = Map.copyOf(new LinkedHashMap<>(cacheSchemaVersions));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("fingerprint", fingerprint);
        json.addProperty("executionTemperature", executionTemperature);
        json.addProperty("minecraftVersion", minecraftVersion);
        json.addProperty("neoForgeVersion", neoForgeVersion);
        json.addProperty("relevantFileHashMode", relevantFileHashes.isEmpty() ? "disabled" : "resource-manager-assets");

        JsonArray modsJson = new JsonArray();
        mods.forEach(modsJson::add);
        json.add("mods", modsJson);

        JsonArray packsJson = new JsonArray();
        resourcePacks.forEach(packsJson::add);
        json.add("resourcePacks", packsJson);

        JsonObject configJson = new JsonObject();
        configInputs.forEach((key, value) -> configJson.addProperty(key, value));
        json.add("configInputs", configJson);

        JsonObject relevantJson = new JsonObject();
        relevantFileHashes.forEach(relevantJson::addProperty);
        json.add("relevantFileHashes", relevantJson);

        JsonObject schemaJson = new JsonObject();
        cacheSchemaVersions.forEach(schemaJson::addProperty);
        json.add("cacheSchemaVersions", schemaJson);
        json.addProperty("configInputsDigest", configInputsDigest);
        return json;
    }

    public static PackFingerprintSnapshot fromJson(JsonObject json) {
        List<JsonObject> mods = json.has("mods")
                ? json.getAsJsonArray("mods").asList().stream().map(element -> element.getAsJsonObject().deepCopy()).toList()
                : List.of();
        List<JsonObject> resourcePacks = json.has("resourcePacks")
                ? json.getAsJsonArray("resourcePacks").asList().stream().map(element -> element.getAsJsonObject().deepCopy()).toList()
                : List.of();
        Map<String, String> relevantFileHashes = json.has("relevantFileHashes")
                ? json.getAsJsonObject("relevantFileHashes").entrySet().stream()
                        .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue().getAsString()), Map::putAll)
                : Map.of();
        Map<String, String> configInputs = json.has("configInputs")
                ? json.getAsJsonObject("configInputs").entrySet().stream()
                        .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue().getAsString()), Map::putAll)
                : Map.of();
        Map<String, Integer> cacheSchemaVersions = json.has("cacheSchemaVersions")
                ? json.getAsJsonObject("cacheSchemaVersions").entrySet().stream()
                        .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue().getAsInt()), Map::putAll)
                : Map.of();

        return new PackFingerprintSnapshot(
                json.get("fingerprint").getAsString(),
                json.get("executionTemperature").getAsString(),
                json.get("minecraftVersion").getAsString(),
                json.get("neoForgeVersion").getAsString(),
                mods,
                resourcePacks,
                relevantFileHashes,
                configInputs,
                cacheSchemaVersions,
                json.has("configInputsDigest") ? json.get("configInputsDigest").getAsString() : "");
    }
}
