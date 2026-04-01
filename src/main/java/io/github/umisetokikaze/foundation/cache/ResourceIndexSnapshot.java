package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record ResourceIndexSnapshot(
        Set<String> namespaces,
        Map<String, Set<String>> pathsByNamespace,
        Set<String> existenceSet,
        Set<String> negativeLookupSet,
        String sourcePackOrderDigest) {

    public ResourceIndexSnapshot {
        namespaces = Set.copyOf(namespaces);
        LinkedHashMap<String, Set<String>> normalized = new LinkedHashMap<>();
        pathsByNamespace.forEach((namespace, paths) -> normalized.put(namespace, Set.copyOf(paths)));
        pathsByNamespace = Map.copyOf(normalized);
        existenceSet = Set.copyOf(existenceSet);
        negativeLookupSet = Set.copyOf(negativeLookupSet);
    }

    public boolean contains(String namespace, String path) {
        return existenceSet.contains(namespace + ":" + path);
    }

    public boolean isKnownMissing(String namespace, String path) {
        return negativeLookupSet.contains(namespace + ":" + path);
    }

    public static CachePayloadCodec<ResourceIndexSnapshot> codec(String entryType) {
        return new CachePayloadCodec<>() {
            @Override
            public JsonElement encode(ResourceIndexSnapshot value) {
                JsonObject root = new JsonObject();
                root.add("namespaces", toSortedArray(value.namespaces()));
                JsonObject paths = new JsonObject();
                value.pathsByNamespace().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> paths.add(entry.getKey(), toSortedArray(entry.getValue())));
                root.add("pathsByNamespace", paths);
                root.add("existenceSet", toSortedArray(value.existenceSet()));
                root.add("negativeLookupSet", toSortedArray(value.negativeLookupSet()));
                root.addProperty("sourcePackOrderDigest", value.sourcePackOrderDigest());
                return root;
            }

            @Override
            public ResourceIndexSnapshot decode(JsonElement json) {
                JsonObject root = json.getAsJsonObject();
                Set<String> namespaces = toSet(root.getAsJsonArray("namespaces"));
                Map<String, Set<String>> pathsByNamespace = new LinkedHashMap<>();
                JsonObject paths = root.getAsJsonObject("pathsByNamespace");
                for (String key : paths.keySet()) {
                    pathsByNamespace.put(key, toSet(paths.getAsJsonArray(key)));
                }
                Set<String> existenceSet = toSet(root.getAsJsonArray("existenceSet"));
                Set<String> negativeLookupSet = toSet(root.getAsJsonArray("negativeLookupSet"));
                String sourcePackOrderDigest = root.get("sourcePackOrderDigest").getAsString();
                return new ResourceIndexSnapshot(namespaces, pathsByNamespace, existenceSet, negativeLookupSet, sourcePackOrderDigest);
            }

            @Override
            public String entryType() {
                return entryType;
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
