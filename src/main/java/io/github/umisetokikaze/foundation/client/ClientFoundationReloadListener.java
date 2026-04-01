package io.github.umisetokikaze.foundation.client;

import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import io.github.umisetokikaze.foundation.StageHandle;
import io.github.umisetokikaze.foundation.cache.ResourceIndexCacheController;
import io.github.umisetokikaze.foundation.cache.ResourceIndexSnapshot;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

final class ClientFoundationReloadListener extends SimplePreparableReloadListener<ClientFoundationReloadListener.ReloadObservation> {
    private final ProfilingFoundation foundation;
    private final ResourceIndexCacheController cacheController;
    private StageHandle reloadSession = () -> {
    };

    ClientFoundationReloadListener(ProfilingFoundation foundation) {
        this.foundation = foundation;
        this.cacheController = new ResourceIndexCacheController(foundation, foundation.getSafeCacheLayer());
    }

    @Override
    protected ReloadObservation prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        reloadSession = foundation.beginReloadSession();
        try (var ignored = foundation.beginStage("foundation.client_reload.prepare")) {
            PackFingerprintSnapshot snapshot = foundation.updateFingerprint(foundation.createFingerprintService().capture());
            ResourceIndexSnapshot resourceIndexSnapshot = cacheController.loadOrBuild(snapshot, resourceManager);
            return new ReloadObservation(resourceManager.getNamespaces().size(), System.nanoTime(), snapshot, resourceIndexSnapshot);
        }
    }

    @Override
    protected void apply(ReloadObservation observation, ResourceManager resourceManager, ProfilerFiller profiler) {
        try (var ignored = foundation.beginStage("foundation.client_reload.apply")) {
            long durationNanos = System.nanoTime() - observation.startedAtNanos();
            foundation.recordCacheUsage(
                    observation.snapshot(),
                    "foundation.cache.resource_index.snapshot",
                    observation.resourceIndexSnapshot().existenceSet().size(),
                    observation.resourceIndexSnapshot().pathsByNamespace().size(),
                    io.github.umisetokikaze.Config.CACHE_MAX_MIB.get());
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
            ResourceIndexSnapshot resourceIndexSnapshot) {
    }
}
