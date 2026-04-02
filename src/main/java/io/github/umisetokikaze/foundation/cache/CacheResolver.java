package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonObject;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CacheResolver {
    public CacheResolution resolve(PackFingerprintSnapshot previous, PackFingerprintSnapshot current) {
        List<InvalidationReason> changedInputs = detectChangedInputs(previous, current);
        Map<CacheModuleId, ModuleCacheResolution> modules = new EnumMap<>(CacheModuleId.class);
        for (CacheModuleDescriptor descriptor : CacheModuleDescriptor.defaults()) {
            List<InvalidationReason> reasons = previous == null
                    ? List.of(InvalidationReason.FIRST_LOAD)
                    : detectModuleReasons(descriptor, previous, current);
            modules.put(
                    descriptor.module(),
                    new ModuleCacheResolution(
                            descriptor.module(),
                            descriptor.dependencyDigest(current),
                            reasons.isEmpty(),
                            reasons));
        }
        return new CacheResolution(changedInputs, modules);
    }

    private List<InvalidationReason> detectChangedInputs(PackFingerprintSnapshot previous, PackFingerprintSnapshot current) {
        if (previous == null) {
            return List.of(InvalidationReason.FIRST_LOAD);
        }
        List<InvalidationReason> reasons = new ArrayList<>();
        if (!stableJsonList(previous.mods()).equals(stableJsonList(current.mods()))) {
            reasons.add(InvalidationReason.MODS_CHANGED);
        }
        if (!resourcePackSet(previous).equals(resourcePackSet(current))) {
            reasons.add(InvalidationReason.RESOURCE_PACKS_CHANGED);
        } else if (!stableJsonList(previous.resourcePacks()).equals(stableJsonList(current.resourcePacks()))) {
            reasons.add(InvalidationReason.RESOURCE_PACK_ORDER_CHANGED);
        }
        if (!previous.relevantFileHashes().equals(current.relevantFileHashes())) {
            reasons.add(InvalidationReason.RELEVANT_FILES_CHANGED);
        }
        if (!previous.configInputs().equals(current.configInputs())) {
            reasons.add(InvalidationReason.SETTINGS_CHANGED);
        }
        return List.copyOf(reasons);
    }

    private List<InvalidationReason> detectModuleReasons(
            CacheModuleDescriptor descriptor,
            PackFingerprintSnapshot previous,
            PackFingerprintSnapshot current) {
        List<InvalidationReason> reasons = new ArrayList<>();
        if (descriptor.includeMods() && !stableJsonList(previous.mods()).equals(stableJsonList(current.mods()))) {
            reasons.add(InvalidationReason.MODS_CHANGED);
        }
        if (descriptor.includeResourcePacks()) {
            if (!resourcePackSet(previous).equals(resourcePackSet(current))) {
                reasons.add(InvalidationReason.RESOURCE_PACKS_CHANGED);
            } else if (!stableJsonList(previous.resourcePacks()).equals(stableJsonList(current.resourcePacks()))) {
                reasons.add(InvalidationReason.RESOURCE_PACK_ORDER_CHANGED);
            }
        }
        if (descriptor.includeRelevantFiles() && !previous.relevantFileHashes().equals(current.relevantFileHashes())) {
            reasons.add(InvalidationReason.RELEVANT_FILES_CHANGED);
        }
        if (hasRelevantConfigChanges(descriptor, previous.configInputs(), current.configInputs())) {
            reasons.add(InvalidationReason.SETTINGS_CHANGED);
        }
        Integer previousSchema = previous.cacheSchemaVersions().get(descriptor.module().id());
        Integer currentSchema = current.cacheSchemaVersions().get(descriptor.module().id());
        if (previousSchema != null && currentSchema != null && !previousSchema.equals(currentSchema)) {
            reasons.add(InvalidationReason.SCHEMA_CHANGED);
        }
        return List.copyOf(reasons);
    }

    private boolean hasRelevantConfigChanges(
            CacheModuleDescriptor descriptor,
            Map<String, String> previousConfig,
            Map<String, String> currentConfig) {
        for (String key : descriptor.configKeys()) {
            if (!String.valueOf(previousConfig.get(key)).equals(String.valueOf(currentConfig.get(key)))) {
                return true;
            }
        }
        return false;
    }

    private List<String> stableJsonList(List<JsonObject> objects) {
        return objects.stream().map(JsonUtil::stableJson).toList();
    }

    private Set<String> resourcePackSet(PackFingerprintSnapshot snapshot) {
        Set<String> values = new LinkedHashSet<>();
        snapshot.resourcePacks().stream().map(JsonUtil::stableJson).sorted().forEach(values::add);
        return values;
    }
}
