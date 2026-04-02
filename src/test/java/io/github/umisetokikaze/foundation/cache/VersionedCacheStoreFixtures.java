package io.github.umisetokikaze.foundation.cache;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class VersionedCacheStoreFixtures {
    private VersionedCacheStoreFixtures() {
    }

    static ResourceIndexSnapshot sampleResourceIndexSnapshot() {
        return sampleResourceIndexSnapshot("example:item/a.json");
    }

    static ResourceIndexSnapshot sampleResourceIndexSnapshot(String lookupKey) {
        Map<String, Set<String>> pathIndex = new LinkedHashMap<>();
        pathIndex.put("example", new LinkedHashSet<>(Set.of("models/item/a.json")));
        return new ResourceIndexSnapshot(
                Set.of("example"),
                pathIndex,
                Set.of(lookupKey),
                Map.of(lookupKey, "vanilla"),
                "packs");
    }

    static NegativeLookupSnapshot sampleNegativeLookupSnapshot() {
        return new NegativeLookupSnapshot(
                Set.of("example"),
                Set.of("example:models/item/a.json"),
                "packs");
    }
}
