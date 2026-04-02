package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashSet;
import java.util.Set;

public record NegativeLookupSnapshot(
        Set<String> namespaceIndex,
        Set<String> existingResources,
        String sourcePackOrderDigest) {

    public NegativeLookupSnapshot {
        namespaceIndex = Set.copyOf(namespaceIndex);
        existingResources = Set.copyOf(existingResources);
    }

    public boolean isKnownMissing(String namespace, String path) {
        return !namespaceIndex.contains(namespace)
                || !existingResources.contains(ResourceIndexSnapshot.toLookupKey(namespace, path));
    }

    public static NegativeLookupSnapshot fromResourceIndex(ResourceIndexSnapshot snapshot) {
        return new NegativeLookupSnapshot(
                snapshot.namespaceIndex(),
                snapshot.fileExistenceMap(),
                snapshot.sourcePackOrderDigest());
    }

    public static CachePayloadCodec<NegativeLookupSnapshot> codec() {
        return new CachePayloadCodec<>() {
            @Override
            public JsonElement encode(NegativeLookupSnapshot value) {
                JsonObject root = new JsonObject();
                root.add("namespaceIndex", toSortedArray(value.namespaceIndex()));
                root.add("existingResources", toSortedArray(value.existingResources()));
                root.addProperty("sourcePackOrderDigest", value.sourcePackOrderDigest());
                return root;
            }

            @Override
            public NegativeLookupSnapshot decode(JsonElement json) {
                JsonObject root = json.getAsJsonObject();
                return new NegativeLookupSnapshot(
                        toSet(root.getAsJsonArray("namespaceIndex")),
                        toSet(root.getAsJsonArray("existingResources")),
                        root.get("sourcePackOrderDigest").getAsString());
            }

            @Override
            public String entryType() {
                return "negative_lookup";
            }
        };
    }

    private static JsonArray toSortedArray(Set<String> values) {
        JsonArray array = new JsonArray();
        values.stream().sorted().map(JsonPrimitive::new).forEach(array::add);
        return array;
    }

    private static Set<String> toSet(JsonArray array) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }
}
