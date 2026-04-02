package io.github.umisetokikaze.foundation.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ModelPipelineCacheController {
    private static final String MODEL_JSON_ENTRY = "models-parsed";
    private static final String MODEL_PARENT_ENTRY = "models-parent-graph";
    private static final String BLOCKSTATE_ENTRY = "blockstates-expanded";
    private static final String ATLAS_PLAN_ENTRY = "atlas-plan";
    private static final String BLOCKS_ATLAS = "minecraft:blocks";

    private final ProfilingFoundation foundation;
    private final SafeCacheLayer cacheLayer;

    public ModelPipelineCacheController(ProfilingFoundation foundation, SafeCacheLayer cacheLayer) {
        this.foundation = foundation;
        this.cacheLayer = cacheLayer;
    }

    public ModelPipelineBundle loadOrBuild(
            PackFingerprintSnapshot snapshot,
            CacheResolution resolution,
            ResourceIndexBundle resourceIndexBundle,
            ResourceManager resourceManager) {
        ModuleCacheResolution modelJsonResolution = resolution.resolutionFor(CacheModuleId.MODEL_JSON_PARSE);
        ModuleCacheResolution parentGraphResolution = resolution.resolutionFor(CacheModuleId.MODEL_PARENT_GRAPH);
        ModuleCacheResolution blockstateResolution = resolution.resolutionFor(CacheModuleId.BLOCKSTATE_EXPANSION);
        ModuleCacheResolution atlasPlanResolution = resolution.resolutionFor(CacheModuleId.ATLAS_PLAN);

        ModelJsonParseSnapshot modelJsonParse = tryLoad(
                snapshot,
                CacheModuleId.MODEL_JSON_PARSE,
                modelJsonResolution,
                MODEL_JSON_ENTRY,
                ModelJsonParseSnapshot.codec(),
                "foundation.cache.model_json_parse.warm")
                .orElse(null);
        if (modelJsonParse == null) {
            modelJsonParse = buildModelJsonParse(snapshot, resourceIndexBundle.resourceIndex(), resourceManager);
            maybeWrite(snapshot, CacheModuleId.MODEL_JSON_PARSE, modelJsonResolution.dependencyDigest(), MODEL_JSON_ENTRY, ModelJsonParseSnapshot.codec(), modelJsonParse);
        }

        ModelParentGraphSnapshot parentGraph = tryLoad(
                snapshot,
                CacheModuleId.MODEL_PARENT_GRAPH,
                parentGraphResolution,
                MODEL_PARENT_ENTRY,
                ModelParentGraphSnapshot.codec(),
                "foundation.cache.model_parent_graph.warm")
                .orElse(null);
        if (parentGraph == null) {
            parentGraph = buildModelParentGraph(snapshot, modelJsonParse);
            maybeWrite(snapshot, CacheModuleId.MODEL_PARENT_GRAPH, parentGraphResolution.dependencyDigest(), MODEL_PARENT_ENTRY, ModelParentGraphSnapshot.codec(), parentGraph);
        }

        BlockstateExpansionSnapshot blockstateExpansion = tryLoad(
                snapshot,
                CacheModuleId.BLOCKSTATE_EXPANSION,
                blockstateResolution,
                BLOCKSTATE_ENTRY,
                BlockstateExpansionSnapshot.codec(),
                "foundation.cache.blockstate_expansion.warm")
                .orElse(null);
        if (blockstateExpansion == null) {
            blockstateExpansion = buildBlockstateExpansion(snapshot, resourceIndexBundle.resourceIndex(), resourceManager);
            maybeWrite(snapshot, CacheModuleId.BLOCKSTATE_EXPANSION, blockstateResolution.dependencyDigest(), BLOCKSTATE_ENTRY, BlockstateExpansionSnapshot.codec(), blockstateExpansion);
        }

        AtlasPlanSnapshot atlasPlan = tryLoad(
                snapshot,
                CacheModuleId.ATLAS_PLAN,
                atlasPlanResolution,
                ATLAS_PLAN_ENTRY,
                AtlasPlanSnapshot.codec(),
                "foundation.cache.atlas_plan.warm")
                .orElse(null);
        if (atlasPlan == null) {
            atlasPlan = buildAtlasPlan(snapshot, resourceIndexBundle.resourceIndex(), modelJsonParse, parentGraph, resourceManager);
            maybeWrite(snapshot, CacheModuleId.ATLAS_PLAN, atlasPlanResolution.dependencyDigest(), ATLAS_PLAN_ENTRY, AtlasPlanSnapshot.codec(), atlasPlan);
        }

        return new ModelPipelineBundle(modelJsonParse, parentGraph, blockstateExpansion, atlasPlan);
    }

    private <T> Optional<T> tryLoad(
            PackFingerprintSnapshot snapshot,
            CacheModuleId module,
            ModuleCacheResolution resolution,
            String entryKey,
            CachePayloadCodec<T> codec,
            String warmStage) {
        if (!resolution.reuseAllowed()) {
            foundation.recordInvalidation(snapshot, module.id(), resolution.primaryReason().name(), resolution.reasonDetail());
            return Optional.empty();
        }
        CacheLookupResult<T> result = cacheLayer.read(snapshot, module, resolution.dependencyDigest(), entryKey, codec);
        if (result.hit() && result.value() != null) {
            foundation.beginStage(warmStage).close();
            return Optional.of(result.value());
        }
        return Optional.empty();
    }

    private <T> void maybeWrite(
            PackFingerprintSnapshot snapshot,
            CacheModuleId module,
            String dependencyDigest,
            String entryKey,
            CachePayloadCodec<T> codec,
            T value) {
        if (Config.CACHE_REBUILD_ON_MISS.get()) {
            cacheLayer.write(snapshot, module, dependencyDigest, entryKey, codec, value);
        }
    }

    private ModelJsonParseSnapshot buildModelJsonParse(
            PackFingerprintSnapshot snapshot,
            ResourceIndexSnapshot resourceIndex,
            ResourceManager resourceManager) {
        try (var ignored = foundation.beginStage("foundation.cache.model_json_parse.cold_build")) {
            Map<String, JsonObject> modelsById = new LinkedHashMap<>();
            Map<String, String> sourcePathById = new LinkedHashMap<>();
            Set<String> customLoaderModels = new LinkedHashSet<>();

            resourceIndex.fileExistenceMap().stream()
                    .filter(lookupKey -> lookupKey.contains(":models/") && lookupKey.endsWith(".json"))
                    .sorted()
                    .forEach(lookupKey -> {
                        String modelId = toModelId(lookupKey);
                        if (modelId == null) {
                            return;
                        }
                        Optional<JsonObject> parsed = readJsonObject(resourceManager, lookupKey);
                        if (parsed.isEmpty()) {
                            foundation.recordInvalidation(snapshot, CacheModuleId.MODEL_JSON_PARSE.id(), "MODEL_JSON_READ_FAILED", modelId);
                            return;
                        }
                        JsonObject root = parsed.get();
                        modelsById.put(modelId, root);
                        sourcePathById.put(modelId, lookupKey);
                        if (root.has("loader")) {
                            customLoaderModels.add(modelId);
                        }
                    });

            return new ModelJsonParseSnapshot(modelsById, sourcePathById, customLoaderModels);
        }
    }

    private ModelParentGraphSnapshot buildModelParentGraph(
            PackFingerprintSnapshot snapshot,
            ModelJsonParseSnapshot parseSnapshot) {
        try (var ignored = foundation.beginStage("foundation.cache.model_parent_graph.cold_build")) {
            Map<String, String> parentByModel = new LinkedHashMap<>();
            parseSnapshot.modelsById().forEach((modelId, json) -> {
                String parent = optionalString(json, "parent");
                if (parent != null && !parent.isBlank()) {
                    parentByModel.put(modelId, normalizeModelId(parent, namespaceOfModel(modelId)));
                }
            });

            Map<String, List<String>> chains = new LinkedHashMap<>();
            Map<String, Map<String, String>> resolvedTextures = new LinkedHashMap<>();
            Set<String> rootModels = new LinkedHashSet<>();
            Set<String> unresolvedModels = new LinkedHashSet<>();
            Set<String> cyclicModels = new LinkedHashSet<>();
            Set<String> customLoaderModels = new LinkedHashSet<>(parseSnapshot.customLoaderModels());

            for (String modelId : parseSnapshot.modelsById().keySet()) {
                if (customLoaderModels.contains(modelId)) {
                    continue;
                }
                resolveModelNode(
                        modelId,
                        parseSnapshot,
                        parentByModel,
                        chains,
                        resolvedTextures,
                        rootModels,
                        unresolvedModels,
                        cyclicModels,
                        new ArrayDeque<>());
            }

            if (!cyclicModels.isEmpty()) {
                foundation.recordInvalidation(snapshot, CacheModuleId.MODEL_PARENT_GRAPH.id(), "MODEL_PARENT_CYCLE", String.join(",", cyclicModels));
            }
            return new ModelParentGraphSnapshot(
                    parentByModel,
                    chains,
                    resolvedTextures,
                    rootModels,
                    unresolvedModels,
                    cyclicModels,
                    customLoaderModels);
        }
    }

    private BlockstateExpansionSnapshot buildBlockstateExpansion(
            PackFingerprintSnapshot snapshot,
            ResourceIndexSnapshot resourceIndex,
            ResourceManager resourceManager) {
        try (var ignored = foundation.beginStage("foundation.cache.blockstate_expansion.cold_build")) {
            Map<String, Map<String, List<BlockstateExpansionSnapshot.ModelVariant>>> variants = new LinkedHashMap<>();
            Map<String, List<BlockstateExpansionSnapshot.MultipartCase>> multipart = new LinkedHashMap<>();

            resourceIndex.fileExistenceMap().stream()
                    .filter(lookupKey -> lookupKey.contains(":blockstates/") && lookupKey.endsWith(".json"))
                    .sorted()
                    .forEach(lookupKey -> {
                        Optional<JsonObject> parsed = readJsonObject(resourceManager, lookupKey);
                        if (parsed.isEmpty()) {
                            foundation.recordInvalidation(snapshot, CacheModuleId.BLOCKSTATE_EXPANSION.id(), "BLOCKSTATE_READ_FAILED", lookupKey);
                            return;
                        }
                        JsonObject root = parsed.get();
                        String blockstateId = toBlockstateId(lookupKey);

                        if (root.has("variants") && root.get("variants").isJsonObject()) {
                            Map<String, List<BlockstateExpansionSnapshot.ModelVariant>> expandedVariants = new LinkedHashMap<>();
                            JsonObject variantsObject = root.getAsJsonObject("variants");
                            variantsObject.keySet().stream().sorted().forEach(variantKey -> expandedVariants.put(
                                    variantKey,
                                    parseModelVariantList(variantsObject.get(variantKey), namespaceOfResourceKey(lookupKey))));
                            variants.put(blockstateId, expandedVariants);
                        }

                        if (root.has("multipart") && root.get("multipart").isJsonArray()) {
                            List<BlockstateExpansionSnapshot.MultipartCase> cases = new ArrayList<>();
                            for (JsonElement element : root.getAsJsonArray("multipart")) {
                                if (!element.isJsonObject()) {
                                    continue;
                                }
                                JsonObject part = element.getAsJsonObject();
                                String whenKey = part.has("when") ? canonicalWhen(part.get("when")) : "";
                                cases.add(new BlockstateExpansionSnapshot.MultipartCase(
                                        whenKey,
                                        parseModelVariantList(part.get("apply"), namespaceOfResourceKey(lookupKey))));
                            }
                            multipart.put(blockstateId, List.copyOf(cases));
                        }
                    });

            return new BlockstateExpansionSnapshot(variants, multipart);
        }
    }

    private AtlasPlanSnapshot buildAtlasPlan(
            PackFingerprintSnapshot snapshot,
            ResourceIndexSnapshot resourceIndex,
            ModelJsonParseSnapshot parseSnapshot,
            ModelParentGraphSnapshot graphSnapshot,
            ResourceManager resourceManager) {
        try (var ignored = foundation.beginStage("foundation.cache.atlas_plan.cold_build")) {
            Set<String> atlasSources = new LinkedHashSet<>();
            atlasSources.add(BLOCKS_ATLAS);
            resourceIndex.fileExistenceMap().stream()
                    .filter(lookupKey -> lookupKey.contains(":atlases/") && lookupKey.endsWith(".json"))
                    .sorted()
                    .forEach(atlasSources::add);

            Map<String, Set<String>> textureDependenciesByAtlas = new LinkedHashMap<>();
            Set<String> blockAtlasTextures = new LinkedHashSet<>();
            Set<String> unresolvedTextureReferences = new LinkedHashSet<>();
            Set<String> skippedModels = new LinkedHashSet<>(parseSnapshot.customLoaderModels());

            for (String modelId : parseSnapshot.modelsById().keySet()) {
                JsonObject modelJson = parseSnapshot.model(modelId);
                if (modelJson == null || parseSnapshot.customLoaderModels().contains(modelId)) {
                    continue;
                }

                Map<String, String> resolvedTextures = graphSnapshot.resolvedTexturesByModel().getOrDefault(modelId, Map.of());
                List<String> chain = graphSnapshot.inheritanceChainByModel().getOrDefault(modelId, List.of(modelId));
                boolean unresolved = graphSnapshot.unresolvedModels().contains(modelId);

                for (String chainModelId : chain) {
                    JsonObject chainModel = parseSnapshot.model(chainModelId);
                    if (chainModel == null) {
                        continue;
                    }
                    unresolved |= collectModelTextures(chainModel, chainModelId, resolvedTextures, blockAtlasTextures, unresolvedTextureReferences);
                }

                if (unresolved) {
                    foundation.recordInvalidation(snapshot, CacheModuleId.ATLAS_PLAN.id(), "ATLAS_TEXTURE_UNRESOLVED", modelId);
                }
            }

            textureDependenciesByAtlas.put(BLOCKS_ATLAS, Set.copyOf(blockAtlasTextures));
            resourceIndex.fileExistenceMap().stream()
                    .filter(lookupKey -> lookupKey.contains(":atlases/") && lookupKey.endsWith(".json"))
                    .sorted()
                    .forEach(lookupKey -> textureDependenciesByAtlas.put(lookupKey, readAtlasSourceDependencies(resourceManager, lookupKey)));

            return new AtlasPlanSnapshot(atlasSources, textureDependenciesByAtlas, unresolvedTextureReferences, skippedModels);
        }
    }

    private void resolveModelNode(
            String modelId,
            ModelJsonParseSnapshot parseSnapshot,
            Map<String, String> parentByModel,
            Map<String, List<String>> chains,
            Map<String, Map<String, String>> resolvedTextures,
            Set<String> rootModels,
            Set<String> unresolvedModels,
            Set<String> cyclicModels,
            Deque<String> visiting) {
        if (chains.containsKey(modelId) && resolvedTextures.containsKey(modelId)) {
            return;
        }
        if (!visiting.add(modelId)) {
            cyclicModels.add(modelId);
            unresolvedModels.add(modelId);
            chains.put(modelId, List.copyOf(visiting));
            resolvedTextures.put(modelId, extractTextureMap(parseSnapshot.model(modelId)));
            return;
        }

        JsonObject modelJson = parseSnapshot.model(modelId);
        Map<String, String> ownTextures = extractTextureMap(modelJson);
        String parentId = parentByModel.get(modelId);
        List<String> chain;
        Map<String, String> textures = new LinkedHashMap<>();

        if (parentId == null) {
            rootModels.add(modelId);
            chain = List.of(modelId);
            textures.putAll(ownTextures);
        } else if (parseSnapshot.customLoaderModels().contains(parentId)) {
            unresolvedModels.add(modelId);
            chain = List.of(parentId, modelId);
            textures.putAll(ownTextures);
        } else if (!parseSnapshot.modelsById().containsKey(parentId)) {
            unresolvedModels.add(modelId);
            chain = List.of(parentId, modelId);
            textures.putAll(ownTextures);
        } else {
            resolveModelNode(parentId, parseSnapshot, parentByModel, chains, resolvedTextures, rootModels, unresolvedModels, cyclicModels, visiting);
            chain = append(chains.getOrDefault(parentId, List.of(parentId)), modelId);
            textures.putAll(resolvedTextures.getOrDefault(parentId, Map.of()));
            textures.putAll(ownTextures);
            if (cyclicModels.contains(parentId) || unresolvedModels.contains(parentId)) {
                unresolvedModels.add(modelId);
            }
        }

        visiting.removeLastOccurrence(modelId);
        chains.put(modelId, chain);
        resolvedTextures.put(modelId, Map.copyOf(textures));
    }

    private boolean collectModelTextures(
            JsonObject modelJson,
            String modelId,
            Map<String, String> resolvedTextures,
            Set<String> output,
            Set<String> unresolved) {
        boolean unresolvedEncountered = false;
        String namespace = namespaceOfModel(modelId);

        if (modelJson.has("textures") && modelJson.get("textures").isJsonObject()) {
            JsonObject textures = modelJson.getAsJsonObject("textures");
            for (String key : textures.keySet()) {
                String raw = textures.get(key).getAsString();
                String resolvedTexture = resolveTextureReference(raw, resolvedTextures, namespace);
                if (resolvedTexture == null) {
                    unresolved.add(modelId + "#" + key + "=" + raw);
                    unresolvedEncountered = true;
                } else {
                    output.add(resolvedTexture);
                }
            }
        }

        if (modelJson.has("elements") && modelJson.get("elements").isJsonArray()) {
            for (JsonElement element : modelJson.getAsJsonArray("elements")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                if (faces == null) {
                    continue;
                }
                for (String face : faces.keySet()) {
                    JsonObject faceJson = faces.getAsJsonObject(face);
                    String raw = optionalString(faceJson, "texture");
                    if (raw == null) {
                        continue;
                    }
                    String resolvedTexture = resolveTextureReference(raw, resolvedTextures, namespace);
                    if (resolvedTexture == null) {
                        unresolved.add(modelId + "#" + face + "=" + raw);
                        unresolvedEncountered = true;
                    } else {
                        output.add(resolvedTexture);
                    }
                }
            }
        }
        return unresolvedEncountered;
    }

    private Set<String> readAtlasSourceDependencies(ResourceManager resourceManager, String lookupKey) {
        Optional<JsonObject> parsed = readJsonObject(resourceManager, lookupKey);
        if (parsed.isEmpty()) {
            return Set.of();
        }

        Set<String> dependencies = new LinkedHashSet<>();
        JsonArray sources = parsed.get().getAsJsonArray("sources");
        if (sources == null) {
            return Set.of();
        }
        String namespace = namespaceOfResourceKey(lookupKey);
        for (JsonElement element : sources) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject source = element.getAsJsonObject();
            for (String field : List.of("resource", "prefix", "sprite", "file", "source")) {
                String value = optionalString(source, field);
                if (value != null && !value.isBlank()) {
                    dependencies.add(normalizeTextureId(value, namespace));
                }
            }
        }
        return Set.copyOf(dependencies);
    }

    private Optional<JsonObject> readJsonObject(ResourceManager resourceManager, String lookupKey) {
        try {
            Resource resource = resourceManager.getResource(toIdentifier(lookupKey)).orElse(null);
            if (resource == null) {
                return Optional.empty();
            }
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject().deepCopy()) : Optional.empty();
            }
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private List<BlockstateExpansionSnapshot.ModelVariant> parseModelVariantList(JsonElement json, String defaultNamespace) {
        if (json == null || json.isJsonNull()) {
            return List.of();
        }
        List<BlockstateExpansionSnapshot.ModelVariant> variants = new ArrayList<>();
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                parseModelVariant(element, defaultNamespace).ifPresent(variants::add);
            }
        } else {
            parseModelVariant(json, defaultNamespace).ifPresent(variants::add);
        }
        return List.copyOf(variants);
    }

    private Optional<BlockstateExpansionSnapshot.ModelVariant> parseModelVariant(JsonElement json, String defaultNamespace) {
        if (!json.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = json.getAsJsonObject();
        String model = optionalString(object, "model");
        if (model == null || model.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new BlockstateExpansionSnapshot.ModelVariant(
                normalizeModelId(model, defaultNamespace),
                object.has("x") ? object.get("x").getAsInt() : 0,
                object.has("y") ? object.get("y").getAsInt() : 0,
                object.has("uvlock") && object.get("uvlock").getAsBoolean(),
                object.has("weight") ? object.get("weight").getAsInt() : 1));
    }

    private String canonicalWhen(JsonElement whenJson) {
        return JsonUtil.stableJson(whenJson);
    }

    private List<String> append(List<String> chain, String modelId) {
        List<String> copy = new ArrayList<>(chain);
        if (copy.isEmpty() || !Objects.equals(copy.get(copy.size() - 1), modelId)) {
            copy.add(modelId);
        }
        return List.copyOf(copy);
    }

    private Map<String, String> extractTextureMap(JsonObject modelJson) {
        Map<String, String> textures = new LinkedHashMap<>();
        if (modelJson == null || !modelJson.has("textures") || !modelJson.get("textures").isJsonObject()) {
            return Map.of();
        }
        JsonObject textureObject = modelJson.getAsJsonObject("textures");
        textureObject.keySet().forEach(key -> {
            JsonElement value = textureObject.get(key);
            if (value != null && value.isJsonPrimitive()) {
                textures.put(key, value.getAsString());
            }
        });
        return Map.copyOf(textures);
    }

    private String resolveTextureReference(String raw, Map<String, String> resolvedTextures, String defaultNamespace) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw;
        Set<String> seen = new LinkedHashSet<>();
        while (value.startsWith("#")) {
            String key = value.substring(1);
            if (!seen.add(key)) {
                return null;
            }
            value = resolvedTextures.get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
        }
        return normalizeTextureId(value, defaultNamespace);
    }

    private String normalizeTextureId(String value, String defaultNamespace) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.indexOf(':') >= 0 ? value : defaultNamespace + ":" + value;
    }

    private String normalizeModelId(String value, String defaultNamespace) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.indexOf(':') >= 0 ? value : defaultNamespace + ":" + value;
    }

    private Identifier toIdentifier(String lookupKey) {
        int separator = lookupKey.indexOf(':');
        return Identifier.parse(lookupKey.substring(0, separator) + ":" + lookupKey.substring(separator + 1));
    }

    private String toModelId(String lookupKey) {
        int separator = lookupKey.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        String namespace = lookupKey.substring(0, separator);
        String path = lookupKey.substring(separator + 1);
        if (!path.startsWith("models/") || !path.endsWith(".json")) {
            return null;
        }
        return namespace + ":" + path.substring("models/".length(), path.length() - ".json".length());
    }

    private String toBlockstateId(String lookupKey) {
        int separator = lookupKey.indexOf(':');
        String namespace = lookupKey.substring(0, separator);
        String path = lookupKey.substring(separator + 1);
        return namespace + ":" + path.substring("blockstates/".length(), path.length() - ".json".length());
    }

    private String namespaceOfModel(String modelId) {
        int separator = modelId.indexOf(':');
        return separator > 0 ? modelId.substring(0, separator) : "minecraft";
    }

    private String namespaceOfResourceKey(String lookupKey) {
        int separator = lookupKey.indexOf(':');
        return separator > 0 ? lookupKey.substring(0, separator) : "minecraft";
    }

    private String optionalString(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        return json.get(key).getAsString();
    }
}
