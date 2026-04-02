package io.github.umisetokikaze.foundation.client;

import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import io.github.umisetokikaze.foundation.PackFingerprintService;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import io.github.umisetokikaze.foundation.StageHandle;
import io.github.umisetokikaze.foundation.cache.CacheModuleId;
import io.github.umisetokikaze.foundation.cache.CacheResolution;
import io.github.umisetokikaze.foundation.cache.CacheResolver;
import io.github.umisetokikaze.foundation.cache.ModelPipelineBundle;
import io.github.umisetokikaze.foundation.cache.ModelPipelineCacheController;
import io.github.umisetokikaze.foundation.cache.ResourceIndexCacheController;
import io.github.umisetokikaze.foundation.cache.ResourceIndexBundle;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

final class ClientFoundationReloadListener extends SimplePreparableReloadListener<ClientFoundationReloadListener.ReloadObservation> {
    private final ProfilingFoundation foundation;
    private final ResourceIndexCacheController cacheController;
    private final ModelPipelineCacheController modelPipelineCacheController;
    private StageHandle reloadSession = () -> {
    };

    ClientFoundationReloadListener(ProfilingFoundation foundation) {
        this.foundation = foundation;
        this.cacheController = new ResourceIndexCacheController(foundation, foundation.getSafeCacheLayer());
        this.modelPipelineCacheController = new ModelPipelineCacheController(foundation, foundation.getSafeCacheLayer());
    }

    @Override
    protected ReloadObservation prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        reloadSession = foundation.beginReloadSession();
        try (var ignored = foundation.beginStage("foundation.client_reload.prepare")) {
            PackFingerprintService fingerprintService = foundation.createFingerprintService();
            PackFingerprintSnapshot previousSnapshot = fingerprintService.loadLatestSnapshot().orElse(null);
            PackFingerprintSnapshot snapshot = foundation.updateFingerprint(fingerprintService.capture(resourceManager));
            CacheResolution resolution = new CacheResolver().resolve(previousSnapshot, snapshot);
            foundation.updateCacheResolution(resolution);
            ResourceIndexBundle resourceIndexBundle = cacheController.loadOrBuild(snapshot, resolution, resourceManager);
            ModelPipelineBundle modelPipelineBundle = modelPipelineCacheController.loadOrBuild(snapshot, resolution, resourceIndexBundle, resourceManager);
            return new ReloadObservation(resourceManager.getNamespaces().size(), System.nanoTime(), snapshot, resolution, resourceIndexBundle, modelPipelineBundle);
        }
    }

    @Override
    protected void apply(ReloadObservation observation, ResourceManager resourceManager, ProfilerFiller profiler) {
        try (var ignored = foundation.beginStage("foundation.client_reload.apply")) {
            long durationNanos = System.nanoTime() - observation.startedAtNanos();
            foundation.recordCacheUsage(
                    observation.snapshot(),
                    "foundation.cache.resource_index.snapshot",
                    observation.resourceIndexBundle().resourceIndex().fileExistenceMap().size(),
                    observation.resourceIndexBundle().resourceIndex().pathIndex().size(),
                    foundation.getSafeCacheLayer().effectiveBudgetMiB(CacheModuleId.RESOURCE_INDEX));
            foundation.recordCacheUsage(
                    observation.snapshot(),
                    "foundation.cache.negative_lookup.snapshot",
                    observation.resourceIndexBundle().negativeLookup().existingResources().size(),
                    observation.resourceIndexBundle().negativeLookup().namespaceIndex().size(),
                    foundation.getSafeCacheLayer().effectiveBudgetMiB(CacheModuleId.NEGATIVE_LOOKUP));
            foundation.recordCacheUsage(
                    observation.snapshot(),
                    "foundation.cache.model_json_parse.snapshot",
                    observation.modelPipelineBundle().modelJsonParse().modelsById().size(),
                    observation.modelPipelineBundle().modelJsonParse().customLoaderModels().size(),
                    foundation.getSafeCacheLayer().effectiveBudgetMiB(CacheModuleId.MODEL_JSON_PARSE));
            foundation.recordCacheUsage(
                    observation.snapshot(),
                    "foundation.cache.model_parent_graph.snapshot",
                    observation.modelPipelineBundle().modelParentGraph().inheritanceChainByModel().size(),
                    observation.modelPipelineBundle().modelParentGraph().unresolvedModels().size(),
                    foundation.getSafeCacheLayer().effectiveBudgetMiB(CacheModuleId.MODEL_PARENT_GRAPH));
            foundation.recordCacheUsage(
                    observation.snapshot(),
                    "foundation.cache.blockstate_expansion.snapshot",
                    observation.modelPipelineBundle().blockstateExpansion().totalVariantKeys(),
                    observation.modelPipelineBundle().blockstateExpansion().totalMultipartCases(),
                    foundation.getSafeCacheLayer().effectiveBudgetMiB(CacheModuleId.BLOCKSTATE_EXPANSION));
            foundation.recordCacheUsage(
                    observation.snapshot(),
                    "foundation.cache.atlas_plan.snapshot",
                    observation.modelPipelineBundle().atlasPlan().totalTextureDependencies(),
                    observation.modelPipelineBundle().atlasPlan().atlasSources().size(),
                    foundation.getSafeCacheLayer().effectiveBudgetMiB(CacheModuleId.ATLAS_PLAN));
            foundation.recordReloadObservation(observation.namespaceCount(), durationNanos);
            foundation.finishReloadSession(
                    reloadSession,
                    observation.namespaceCount(),
                    durationNanos);
            reloadSession = () -> {
            };
        }
    }

    record ReloadObservation(
            int namespaceCount,
            long startedAtNanos,
            PackFingerprintSnapshot snapshot,
            CacheResolution resolution,
            ResourceIndexBundle resourceIndexBundle,
            ModelPipelineBundle modelPipelineBundle) {
    }
}
