package io.github.umisetokikaze.foundation.cache;

import java.util.List;

public record ModuleCacheResolution(
        CacheModuleId module,
        String dependencyDigest,
        boolean reuseAllowed,
        List<InvalidationReason> invalidationReasons) {

    public ModuleCacheResolution {
        invalidationReasons = List.copyOf(invalidationReasons);
    }

    public InvalidationReason primaryReason() {
        return invalidationReasons.isEmpty() ? InvalidationReason.HIT : invalidationReasons.get(0);
    }

    public String reasonDetail() {
        return invalidationReasons.stream().map(Enum::name).reduce((left, right) -> left + "," + right).orElse("");
    }
}
