package io.github.umisetokikaze.foundation.client;

import io.github.umisetokikaze.foundation.ProfilingFoundation;
import io.github.umisetokikaze.foundation.StageHandle;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

final class ClientFoundationReloadListener extends SimplePreparableReloadListener<ClientFoundationReloadListener.ReloadObservation> {
    private final ProfilingFoundation foundation;
    private StageHandle reloadSession = () -> {
    };

    ClientFoundationReloadListener(ProfilingFoundation foundation) {
        this.foundation = foundation;
    }

    @Override
    protected ReloadObservation prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        reloadSession = foundation.beginReloadSession();
        try (var ignored = foundation.beginStage("foundation.client_reload.prepare")) {
            return new ReloadObservation(resourceManager.getNamespaces().size(), System.nanoTime());
        }
    }

    @Override
    protected void apply(ReloadObservation observation, ResourceManager resourceManager, ProfilerFiller profiler) {
        try (var ignored = foundation.beginStage("foundation.client_reload.apply")) {
            long durationNanos = System.nanoTime() - observation.startedAtNanos();
            foundation.recordReloadObservation(observation.namespaceCount(), durationNanos);
            foundation.finishReloadSession(
                    reloadSession,
                    observation.namespaceCount(),
                    durationNanos);
            reloadSession = () -> {
            };
        }
    }

    record ReloadObservation(int namespaceCount, long startedAtNanos) {
    }
}
