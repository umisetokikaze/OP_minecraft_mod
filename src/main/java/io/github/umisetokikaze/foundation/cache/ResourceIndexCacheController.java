package io.github.umisetokikaze.foundation.cache;

import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ResourceIndexCacheController {
    private static final String RESOURCE_INDEX_ENTRY = "assets-index";
    private static final String NEGATIVE_LOOKUP_ENTRY = "assets-negative";

    private final ProfilingFoundation foundation;
    private final SafeCacheLayer cacheLayer;

    public ResourceIndexCacheController(ProfilingFoundation foundation, SafeCacheLayer cacheLayer) {
        this.foundation = foundation;
        this.cacheLayer = cacheLayer;
    }

    public ResourceIndexSnapshot loadOrBuild(PackFingerprintSnapshot snapshot, CacheResolution resolution, ResourceManager resourceManager) {
        ModuleCacheResolution resourceIndexResolution = resolution.resolutionFor(CacheModuleId.RESOURCE_INDEX);
        CachePayloadCodec<ResourceIndexSnapshot> resourceIndexCodec = ResourceIndexSnapshot.codec("resource_index");
        if (resourceIndexResolution.reuseAllowed()) {
            CacheLookupResult<ResourceIndexSnapshot> resourceIndexResult = cacheLayer.read(
                    snapshot,
                    CacheModuleId.RESOURCE_INDEX,
                    resourceIndexResolution.dependencyDigest(),
                    RESOURCE_INDEX_ENTRY,
                    resourceIndexCodec);
            if (resourceIndexResult.hit() && resourceIndexResult.value() != null) {
                foundation.beginStage("foundation.cache.resource_index.warm").close();
                return resourceIndexResult.value();
            }
        } else {
            foundation.recordInvalidation(
                    snapshot,
                    CacheModuleId.RESOURCE_INDEX.id(),
                    resourceIndexResolution.primaryReason().name(),
                    resourceIndexResolution.reasonDetail());
        }

        ResourceIndexSnapshot built = buildSnapshot(snapshot, resourceManager);
        if (Config.CACHE_REBUILD_ON_MISS.get()) {
            cacheLayer.write(
                    snapshot,
                    CacheModuleId.RESOURCE_INDEX,
                    resourceIndexResolution.dependencyDigest(),
                    RESOURCE_INDEX_ENTRY,
                    resourceIndexCodec,
                    built);
            ModuleCacheResolution negativeLookupResolution = resolution.resolutionFor(CacheModuleId.NEGATIVE_LOOKUP);
            cacheLayer.write(
                    snapshot,
                    CacheModuleId.NEGATIVE_LOOKUP,
                    negativeLookupResolution.dependencyDigest(),
                    NEGATIVE_LOOKUP_ENTRY,
                    ResourceIndexSnapshot.codec("negative_lookup"),
                    built);
        }
        return built;
    }

    private ResourceIndexSnapshot buildSnapshot(PackFingerprintSnapshot snapshot, ResourceManager resourceManager) {
        try (var ignored = foundation.beginStage("foundation.cache.resource_index.cold_build")) {
            Set<String> namespaces = new LinkedHashSet<>(resourceManager.getNamespaces());
            Map<String, Set<String>> pathsByNamespace = new LinkedHashMap<>();
            Set<String> existence = new LinkedHashSet<>();

            for (Map.Entry<Identifier, net.minecraft.server.packs.resources.Resource> entry
                    : resourceManager.listResources("", path -> true).entrySet()) {
                Identifier location = entry.getKey();
                String relativePath = location.getPath();
                pathsByNamespace.computeIfAbsent(location.getNamespace(), ignoredKey -> new LinkedHashSet<>()).add(relativePath);
                existence.add(location.getNamespace() + ":" + relativePath);
            }

            Set<String> negativeLookup = deriveNegativeLookup(namespaces, pathsByNamespace);
            String sourcePackOrderDigest = snapshot.resourcePacks().stream()
                    .map(JsonUtil::stableJson)
                    .reduce("", (left, right) -> left + "|" + right);
            return new ResourceIndexSnapshot(namespaces, pathsByNamespace, existence, negativeLookup, sourcePackOrderDigest);
        }
    }

    private Set<String> deriveNegativeLookup(Set<String> namespaces, Map<String, Set<String>> pathsByNamespace) {
        Set<String> negative = new LinkedHashSet<>();
        for (String namespace : namespaces) {
            negative.add(namespace + ":models/missing.json");
            negative.add(namespace + ":blockstates/missing.json");
            if (!pathsByNamespace.containsKey(namespace)) {
                negative.add(namespace + ":__namespace_missing__");
            }
        }
        return negative;
    }
}
