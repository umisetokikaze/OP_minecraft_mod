package io.github.umisetokikaze.foundation;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackFingerprintSnapshotTest {
    @Test
    void serializesFingerprintMetadataCollectionsAndConfigInputs() {
        JsonObject mod = new JsonObject();
        mod.addProperty("modId", "example");
        mod.addProperty("version", "1.0.0");

        JsonObject pack = new JsonObject();
        pack.addProperty("id", "vanilla");
        pack.addProperty("title", "Vanilla");

        PackFingerprintSnapshot snapshot = new PackFingerprintSnapshot(
                "abc123",
                "warm",
                "1.21.1",
                "21.1.122",
                List.of(mod),
                List.of(pack),
                Map.of("assets/example/models/item/a.json", "hash123"),
                Map.of("cache.enabled", "true"),
                Map.of(
                        "resource_index", 2,
                        "negative_lookup", 2,
                        "model_json_parse", 1,
                        "model_parent_graph", 1,
                        "blockstate_expansion", 1,
                        "atlas_plan", 1),
                "cfg123");

        JsonObject json = snapshot.toJson();

        assertEquals("abc123", json.get("fingerprint").getAsString());
        assertEquals("warm", json.get("executionTemperature").getAsString());
        assertEquals("1.21.1", json.get("minecraftVersion").getAsString());
        assertEquals("21.1.122", json.get("neoForgeVersion").getAsString());
        assertEquals("example", json.getAsJsonArray("mods").get(0).getAsJsonObject().get("modId").getAsString());
        assertEquals("vanilla", json.getAsJsonArray("resourcePacks").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("hash123", json.getAsJsonObject("relevantFileHashes").get("assets/example/models/item/a.json").getAsString());
        assertEquals("true", json.getAsJsonObject("configInputs").get("cache.enabled").getAsString());
        assertEquals(2, json.getAsJsonObject("cacheSchemaVersions").get("resource_index").getAsInt());
        assertEquals(1, json.getAsJsonObject("cacheSchemaVersions").get("atlas_plan").getAsInt());
        assertEquals("resource-manager-assets", json.get("relevantFileHashMode").getAsString());
        assertEquals("cfg123", json.get("configInputsDigest").getAsString());
    }
}
