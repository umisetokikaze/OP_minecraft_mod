package io.github.umisetokikaze.foundation.cache;

public record CacheLookupResult<T>(
        T value,
        boolean hit,
        InvalidationReason reason,
        String detail,
        CacheEntryMetadata metadata) {

    public static <T> CacheLookupResult<T> hit(T value, CacheEntryMetadata metadata) {
        return new CacheLookupResult<>(value, true, InvalidationReason.HIT, "", metadata);
    }

    public static <T> CacheLookupResult<T> miss(InvalidationReason reason, String detail) {
        return new CacheLookupResult<>(null, false, reason, detail == null ? "" : detail, null);
    }
}
