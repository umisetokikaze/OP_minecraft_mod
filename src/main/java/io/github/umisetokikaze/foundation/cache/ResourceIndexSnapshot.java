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
        Set<String> namespaceIndex,
        Map<String, Set<String>> pathIndex,
        Set<String> fileExistenceMap,
        Map<String, String> winnerOriginIndex,
        String sourcePackOrderDigest) {

    public ResourceIndexSnapshot {
        namespaceIndex = Set.copyOf(namespaceIndex);
        LinkedHashMap<String, Set<String>> normalizedPaths = new LinkedHashMap<>();
        pathIndex.forEach((namespace, paths) -> normalizedPaths.put(namespace, Set.copyOf(paths)));
        pathIndex = Map.copyOf(normalizedPaths);
        fileExistenceMap = Set.copyOf(fileExistenceMap);
        winnerOriginIndex = Map.copyOf(new LinkedHashMap<>(winnerOriginIndex));
    }

    public boolean hasNamespace(String namespace) {
        return namespaceIndex.contains(namespace);
    }

    public boolean contains(String namespace, String path) {
        return fileExistenceMap.contains(toLookupKey(namespace, path));
    }

    public Set<String> pathsForNamespace(String namespace) {
        return pathIndex.getOrDefault(namespace, Set.of());
    }

    public String winnerOrigin(String namespace, String path) {
        return winnerOriginIndex.getOrDefault(toLookupKey(namespace, path), "unknown");
    }

    public static CachePayloadCodec<ResourceIndexSnapshot> codec() {
        return new CachePayloadCodec<>() {
            @Override
            public JsonElement encode(ResourceIndexSnapshot value) {
                JsonObject root = new JsonObject();
                root.add("namespaceIndex", toSortedArray(value.namespaceIndex()));
                JsonObject paths = new JsonObject();
                value.pathIndex().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> paths.add(entry.getKey(), toSortedArray(entry.getValue())));
                root.add("pathIndex", paths);
                root.add("fileExistenceMap", toSortedArray(value.fileExistenceMap()));
                JsonObject origins = new JsonObject();
                value.winnerOriginIndex().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> origins.addProperty(entry.getKey(), entry.getValue()));
                root.add("winnerOriginIndex", origins);
                root.addProperty("sourcePackOrderDigest", value.sourcePackOrderDigest());
                return root;
            }

            @Override
            public ResourceIndexSnapshot decode(JsonElement json) {
                JsonObject root = json.getAsJsonObject();
                Set<String> namespaceIndex = toSet(root.getAsJsonArray("namespaceIndex"));
                Map<String, Set<String>> pathIndex = new LinkedHashMap<>();
                JsonObject paths = root.getAsJsonObject("pathIndex");
                for (String key : paths.keySet()) {
                    pathIndex.put(key, toSet(paths.getAsJsonArray(key)));
                }
                Set<String> fileExistenceMap = toSet(root.getAsJsonArray("fileExistenceMap"));
                Map<String, String> winnerOriginIndex = new LinkedHashMap<>();
                JsonObject origins = root.getAsJsonObject("winnerOriginIndex");
                for (String key : origins.keySet()) {
                    winnerOriginIndex.put(key, origins.get(key).getAsString());
                }
                return new ResourceIndexSnapshot(
                        namespaceIndex,
                        pathIndex,
                        fileExistenceMap,
                        winnerOriginIndex,
                        root.get("sourcePackOrderDigest").getAsString());
            }

            @Override
            public String entryType() {
                return "resource_index";
            }
        };
    }

    static String toLookupKey(String namespace, String path) {
        return namespace + ":" + path;
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
