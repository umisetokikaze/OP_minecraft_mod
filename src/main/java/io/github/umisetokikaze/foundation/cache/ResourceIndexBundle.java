package io.github.umisetokikaze.foundation.cache;

public record ResourceIndexBundle(
        ResourceIndexSnapshot resourceIndex,
        NegativeLookupSnapshot negativeLookup) {
}
