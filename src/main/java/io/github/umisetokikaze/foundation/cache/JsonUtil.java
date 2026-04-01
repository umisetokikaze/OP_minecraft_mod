package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class JsonUtil {
    private JsonUtil() {
    }

    static String stableJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (!element.isJsonObject()) {
            return element.toString();
        }
        JsonObject object = element.getAsJsonObject();
        StringBuilder builder = new StringBuilder();
        object.keySet().stream().sorted().forEach(key -> builder
                .append(key)
                .append('=')
                .append(object.get(key))
                .append(';'));
        return builder.toString();
    }
}
