package io.github.umisetokikaze.foundation.cache;

import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
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

    public ResourceIndexBundle loadOrBuild(PackFingerprintSnapshot snapshot, CacheResolution resolution, ResourceManager resourceManager) {
        ModuleCacheResolution resourceIndexResolution = resolution.resolutionFor(CacheModuleId.RESOURCE_INDEX);
        ModuleCacheResolution negativeLookupResolution = resolution.resolutionFor(CacheModuleId.NEGATIVE_LOOKUP);

        ResourceIndexSnapshot resourceIndexSnapshot = tryLoadResourceIndex(snapshot, resourceIndexResolution).orElse(null);
        NegativeLookupSnapshot negativeLookupSnapshot = tryLoadNegativeLookup(snapshot, negativeLookupResolution).orElse(null);

        if (resourceIndexSnapshot == null) {
            resourceIndexSnapshot = buildResourceIndex(snapshot, resourceManager);
            if (Config.CACHE_REBUILD_ON_MISS.get()) {
                cacheLayer.write(
                        snapshot,
                        CacheModuleId.RESOURCE_INDEX,
                        resourceIndexResolution.dependencyDigest(),
                        RESOURCE_INDEX_ENTRY,
                        ResourceIndexSnapshot.codec(),
                        resourceIndexSnapshot);
            }
        }

        if (negativeLookupSnapshot == null) {
            negativeLookupSnapshot = NegativeLookupSnapshot.fromResourceIndex(resourceIndexSnapshot);
            if (Config.CACHE_REBUILD_ON_MISS.get()) {
                cacheLayer.write(
                        snapshot,
                        CacheModuleId.NEGATIVE_LOOKUP,
                        negativeLookupResolution.dependencyDigest(),
                        NEGATIVE_LOOKUP_ENTRY,
                        NegativeLookupSnapshot.codec(),
                        negativeLookupSnapshot);
            }
        }

        return new ResourceIndexBundle(resourceIndexSnapshot, negativeLookupSnapshot);
    }

    private Optional<ResourceIndexSnapshot> tryLoadResourceIndex(PackFingerprintSnapshot snapshot, ModuleCacheResolution resolution) {
        if (!resolution.reuseAllowed()) {
            foundation.recordInvalidation(
                    snapshot,
                    CacheModuleId.RESOURCE_INDEX.id(),
                    resolution.primaryReason().name(),
                    resolution.reasonDetail());
            return Optional.empty();
        }

        CacheLookupResult<ResourceIndexSnapshot> result = cacheLayer.read(
                snapshot,
                CacheModuleId.RESOURCE_INDEX,
                resolution.dependencyDigest(),
                RESOURCE_INDEX_ENTRY,
                ResourceIndexSnapshot.codec());
        if (result.hit() && result.value() != null) {
            foundation.beginStage("foundation.cache.resource_index.warm").close();
            return Optional.of(result.value());
        }
        return Optional.empty();
    }

    private Optional<NegativeLookupSnapshot> tryLoadNegativeLookup(PackFingerprintSnapshot snapshot, ModuleCacheResolution resolution) {
        if (!resolution.reuseAllowed()) {
            foundation.recordInvalidation(
                    snapshot,
                    CacheModuleId.NEGATIVE_LOOKUP.id(),
                    resolution.primaryReason().name(),
                    resolution.reasonDetail());
            return Optional.empty();
        }

        CacheLookupResult<NegativeLookupSnapshot> result = cacheLayer.read(
                snapshot,
                CacheModuleId.NEGATIVE_LOOKUP,
                resolution.dependencyDigest(),
                NEGATIVE_LOOKUP_ENTRY,
                NegativeLookupSnapshot.codec());
        if (result.hit() && result.value() != null) {
            foundation.beginStage("foundation.cache.negative_lookup.warm").close();
            return Optional.of(result.value());
        }
        return Optional.empty();
    }

    private ResourceIndexSnapshot buildResourceIndex(PackFingerprintSnapshot snapshot, ResourceManager resourceManager) {
        try (var ignored = foundation.beginStage("foundation.cache.resource_index.cold_build")) {
            Set<String> namespaceIndex = new LinkedHashSet<>(resourceManager.getNamespaces());
            Map<Identifier, String> winnerOrigins = new LinkedHashMap<>();

            for (Map.Entry<Identifier, Resource> entry : resourceManager.listResources("", path -> true).entrySet()) {
                namespaceIndex.add(entry.getKey().getNamespace());
                winnerOrigins.put(entry.getKey(), resolveWinnerOrigin(entry.getValue()));
            }

            return createSnapshot(snapshot, namespaceIndex, winnerOrigins);
        }
    }

    static ResourceIndexSnapshot createSnapshot(
            PackFingerprintSnapshot snapshot,
            Set<String> namespaceIndex,
            Map<Identifier, String> winnerOrigins) {
        Map<String, String> byLookupKey = new LinkedHashMap<>();
        for (Map.Entry<Identifier, String> entry : winnerOrigins.entrySet()) {
            Identifier location = entry.getKey();
            byLookupKey.put(ResourceIndexSnapshot.toLookupKey(location.getNamespace(), location.getPath()), entry.getValue());
        }
        return createSnapshotFromLookupKeys(snapshot, namespaceIndex, byLookupKey);
    }

    static ResourceIndexSnapshot createSnapshotFromLookupKeys(
            PackFingerprintSnapshot snapshot,
            Set<String> namespaceIndex,
            Map<String, String> winnerOrigins) {
        Map<String, Set<String>> pathIndex = new LinkedHashMap<>();
        Set<String> fileExistenceMap = new LinkedHashSet<>();
        Map<String, String> winnerOriginIndex = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : winnerOrigins.entrySet()) {
            String lookupKey = entry.getKey();
            int separator = lookupKey.indexOf(':');
            if (separator <= 0 || separator == lookupKey.length() - 1) {
                continue;
            }
            String namespace = lookupKey.substring(0, separator);
            String relativePath = lookupKey.substring(separator + 1);
            pathIndex.computeIfAbsent(namespace, ignoredKey -> new LinkedHashSet<>()).add(relativePath);
            fileExistenceMap.add(lookupKey);
            winnerOriginIndex.put(lookupKey, entry.getValue());
        }

        String sourcePackOrderDigest = snapshot.resourcePacks().stream()
                .map(JsonUtil::stableJson)
                .reduce("", (left, right) -> left + "|" + right);
        return new ResourceIndexSnapshot(
                namespaceIndex,
                pathIndex,
                fileExistenceMap,
                winnerOriginIndex,
                sourcePackOrderDigest);
    }

    private String resolveWinnerOrigin(Resource resource) {
        return invokeString(resource, "sourcePackId")
                .or(() -> invokeString(resource, "getSourcePackId"))
                .or(() -> invokeString(resource, "sourcePackName"))
                .or(() -> invokeString(resource, "getSourcePackName"))
                .orElse("unknown");
    }

    private Optional<String> invokeString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? Optional.empty() : Optional.of(value.toString());
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }
}
