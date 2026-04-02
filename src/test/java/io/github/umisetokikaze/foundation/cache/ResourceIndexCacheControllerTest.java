package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonObject;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceIndexCacheControllerTest {
    @Test
    void createsMergedIndexesAndWinnerOrigins() {
        PackFingerprintSnapshot snapshot = sampleSnapshot();
        Map<String, String> winnerOrigins = new LinkedHashMap<>();
        winnerOrigins.put("minecraft:models/block/stone.json", "mod_resources");
        winnerOrigins.put("minecraft:textures/block/stone.png", "high_contrast");
        winnerOrigins.put("example:lang/en_us.json", "example_pack");

        ResourceIndexSnapshot index = ResourceIndexCacheController.createSnapshotFromLookupKeys(
                snapshot,
                Set.of("minecraft", "example"),
                winnerOrigins);
        NegativeLookupSnapshot negative = NegativeLookupSnapshot.fromResourceIndex(index);

        assertTrue(index.hasNamespace("minecraft"));
        assertTrue(index.contains("minecraft", "models/block/stone.json"));
        assertEquals("high_contrast", index.winnerOrigin("minecraft", "textures/block/stone.png"));
        assertTrue(index.pathsForNamespace("example").contains("lang/en_us.json"));
        assertTrue(negative.isKnownMissing("example", "models/item/missing.json"));
        assertEquals(index.sourcePackOrderDigest(), negative.sourcePackOrderDigest());
    }

    private static PackFingerprintSnapshot sampleSnapshot() {
        JsonObject pack0 = new JsonObject();
        pack0.addProperty("id", "mod_resources");
        pack0.addProperty("title", "Mod resources");
        pack0.addProperty("order", 0);

        JsonObject pack1 = new JsonObject();
        pack1.addProperty("id", "high_contrast");
        pack1.addProperty("title", "High Contrast");
        pack1.addProperty("order", 1);

        return new PackFingerprintSnapshot(
                "fp",
                "cold",
                "1.21.1",
                "21.1.122",
                List.of(),
                List.of(pack0, pack1),
                Map.of(),
                Map.of("compatibilityMode", "standard"),
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
