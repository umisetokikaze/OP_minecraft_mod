package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonElement;

public interface CachePayloadCodec<T> {
    JsonElement encode(T value);

    T decode(JsonElement json);

    String entryType();
}
