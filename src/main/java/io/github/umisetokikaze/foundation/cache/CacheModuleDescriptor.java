package io.github.umisetokikaze.foundation.cache;

import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CacheModuleDescriptor(
        CacheModuleId module,
        int schemaVersion,
        boolean includeMods,
        boolean includeResourcePacks,
        boolean includeRelevantFiles,
        Set<String> configKeys) {

    private static final Map<CacheModuleId, CacheModuleDescriptor> DEFAULTS = Map.of(
            CacheModuleId.RESOURCE_INDEX,
            new CacheModuleDescriptor(
                    CacheModuleId.RESOURCE_INDEX,
                    2,
                    true,
                    true,
                    true,
                    Set.of(
                            "compatibilityMode",
                            "cacheDebugLogging",
                            "cacheMaxMiB",
                            "evictionPolicy",
                            "cacheResourceIndexEnabled",
                            "cacheResourceIndexMaxMiB",
                            "cacheResourceIndexEvictionPolicy",
                            "cacheResourceIndexDebugLogging",
                            "cacheResourceIndexCompatibilityMode",
                            "relevantFingerprintPaths")),
            CacheModuleId.NEGATIVE_LOOKUP,
            new CacheModuleDescriptor(
                    CacheModuleId.NEGATIVE_LOOKUP,
                    2,
                    true,
                    true,
                    true,
                    Set.of(
                            "compatibilityMode",
                            "cacheDebugLogging",
                            "cacheMaxMiB",
                            "evictionPolicy",
                            "cacheNegativeLookupEnabled",
                            "cacheNegativeLookupMaxMiB",
                            "cacheNegativeLookupEvictionPolicy",
                            "cacheNegativeLookupDebugLogging",
                            "cacheNegativeLookupCompatibilityMode",
                            "relevantFingerprintPaths")));

    public static CacheModuleDescriptor forModule(CacheModuleId module) {
        return DEFAULTS.get(module);
    }

    public static List<CacheModuleDescriptor> defaults() {
        return DEFAULTS.values().stream().sorted((left, right) -> left.module().id().compareTo(right.module().id())).toList();
    }

    public static Map<String, Integer> schemaVersionsByModuleId() {
        LinkedHashMap<String, Integer> versions = new LinkedHashMap<>();
        defaults().forEach(descriptor -> versions.put(descriptor.module().id(), descriptor.schemaVersion()));
        return Map.copyOf(versions);
    }

    public String dependencyDigest(PackFingerprintSnapshot snapshot) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("module=").append(module.id()).append('\n');
        builder.append("schemaVersion=").append(schemaVersion).append('\n');
        if (includeMods) {
            snapshot.mods().stream().map(JsonUtil::stableJson).forEach(value -> builder.append("mod=").append(value).append('\n'));
        }
        if (includeResourcePacks) {
            snapshot.resourcePacks().stream().map(JsonUtil::stableJson).forEach(value -> builder.append("pack=").append(value).append('\n'));
        }
        if (includeRelevantFiles) {
            snapshot.relevantFileHashes().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append("relevantFile[").append(entry.getKey()).append("]=").append(entry.getValue()).append('\n'));
        }
        snapshot.configInputs().entrySet().stream()
                .filter(entry -> configKeys.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.append("config[").append(entry.getKey()).append("]=").append(entry.getValue()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(builder.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }
}
