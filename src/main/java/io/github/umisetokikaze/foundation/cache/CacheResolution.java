package io.github.umisetokikaze.foundation.cache;

import java.util.List;
import java.util.Map;

public record CacheResolution(
        List<InvalidationReason> changedInputs,
        Map<CacheModuleId, ModuleCacheResolution> modules) {

    public CacheResolution {
        changedInputs = List.copyOf(changedInputs);
        modules = Map.copyOf(modules);
    }

    public ModuleCacheResolution resolutionFor(CacheModuleId module) {
        ModuleCacheResolution resolution = modules.get(module);
        if (resolution == null) {
            throw new IllegalArgumentException("Missing cache resolution for module " + module.id());
        }
        return resolution;
    }
}
