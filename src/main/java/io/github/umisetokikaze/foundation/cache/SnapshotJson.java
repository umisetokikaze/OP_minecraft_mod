package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class SnapshotJson {
    private SnapshotJson() {
    }

    static JsonArray array(Iterable<String> values) {
        JsonArray array = new JsonArray();
        Set<String> sorted = new java.util.TreeSet<>();
        values.forEach(sorted::add);
        sorted.stream().map(JsonPrimitive::new).forEach(array::add);
        return array;
    }

    static Set<String> set(JsonArray array) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        array.forEach(element -> values.add(element.getAsString()));
        return values;
    }

    static JsonObject object(Map<String, String> values) {
        JsonObject json = new JsonObject();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> json.addProperty(entry.getKey(), entry.getValue()));
        return json;
    }

    static Map<String, String> stringMap(JsonObject json) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : json.keySet()) {
            values.put(key, json.get(key).getAsString());
        }
        return values;
    }
}
