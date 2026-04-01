package io.github.umisetokikaze.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

public record PackFingerprintSnapshot(
        String fingerprint,
        String executionTemperature,
        String minecraftVersion,
        String neoForgeVersion,
        List<JsonObject> mods,
        List<JsonObject> resourcePacks,
        Map<String, String> configInputs,
        String configInputsDigest) {

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("fingerprint", fingerprint);
        json.addProperty("executionTemperature", executionTemperature);
        json.addProperty("minecraftVersion", minecraftVersion);
        json.addProperty("neoForgeVersion", neoForgeVersion);
        json.addProperty("relevantFileHashMode", "phase1-pack-level-only");

        JsonArray modsJson = new JsonArray();
        mods.forEach(modsJson::add);
        json.add("mods", modsJson);

        JsonArray packsJson = new JsonArray();
        resourcePacks.forEach(packsJson::add);
        json.add("resourcePacks", packsJson);

        JsonObject configJson = new JsonObject();
        configInputs.forEach((key, value) -> configJson.addProperty(key, value));
        json.add("configInputs", configJson);
        json.addProperty("configInputsDigest", configInputsDigest);
        return json;
    }
}
