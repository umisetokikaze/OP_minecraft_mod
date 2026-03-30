package io.github.umisetokikaze.foundation.client;

import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.PackFingerprintSnapshot;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import io.github.umisetokikaze.foundation.StageHandle;
import io.github.umisetokikaze.momooptimizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientProfilingController {
    private final ProfilingFoundation foundation = ProfilingFoundation.getInstance();

    private long lastTickNanos = -1L;
    private int observedTicks;
    private int stallCount;
    private long maxFrameDeltaNanos;
    private boolean observingWorldJoin;
    private boolean startupBenchmarkFinished;
    private boolean worldJoinTtfcfRecorded;
    private long worldJoinStartedAtNanos;
    private StageHandle worldJoinSession = () -> {
    };
    private StageHandle worldJoinWindow = () -> {
    };

    public ClientProfilingController(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!Config.FOUNDATION_ENABLED.get() || !Config.PACK_FINGERPRINT_ENABLED.get()) {
                return;
            }
            try (var ignored = foundation.beginStage("foundation.client_setup")) {
                PackFingerprintSnapshot snapshot = foundation.createFingerprintService().capture();
                foundation.updateFingerprint(snapshot);
                momooptimizer.LOGGER.info(
                        "Pack fingerprint={} warmCold={} mods={} packs={}",
                        snapshot.fingerprint(),
                        snapshot.executionTemperature(),
                        snapshot.mods().size(),
                        snapshot.resourcePacks().size());
            } catch (RuntimeException exception) {
                foundation.quarantine("foundation.pack_fingerprint", "capture-failed");
                momooptimizer.LOGGER.warn("Failed to capture pack fingerprint", exception);
            }
        });
    }

    private void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
                Identifier.parse(momooptimizer.MODID + ":profiling_foundation"),
                new ClientFoundationReloadListener(foundation));
    }

    @SubscribeEvent
    public void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        worldJoinSession = foundation.beginWorldJoinSession();
        worldJoinWindow = foundation.beginStage("foundation.world_join.window");
        observingWorldJoin = true;
        worldJoinTtfcfRecorded = false;
        observedTicks = 0;
        stallCount = 0;
        maxFrameDeltaNanos = 0L;
        worldJoinStartedAtNanos = System.nanoTime();
        lastTickNanos = System.nanoTime();
    }

    @SubscribeEvent
    public void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        flushWorldJoinWindow();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (!startupBenchmarkFinished
                && (foundation.benchmarkCaseId() == io.github.umisetokikaze.foundation.BenchmarkCaseId.STARTUP_COLD
                || foundation.benchmarkCaseId() == io.github.umisetokikaze.foundation.BenchmarkCaseId.STARTUP_WARM)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof TitleScreen) {
                startupBenchmarkFinished = true;
                foundation.finishStartupBenchmarkFromNow();
            }
        }

        if (!observingWorldJoin) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!worldJoinTtfcfRecorded && minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
            worldJoinTtfcfRecorded = true;
            foundation.noteWorldJoinTtfcf(System.nanoTime() - worldJoinStartedAtNanos);
        }

        long now = System.nanoTime();
        if (lastTickNanos > 0L) {
            long delta = now - lastTickNanos;
            maxFrameDeltaNanos = Math.max(maxFrameDeltaNanos, delta);
            if (delta >= Config.STALL_THRESHOLD_MS.get() * 1_000_000L) {
                stallCount++;
            }
        }
        lastTickNanos = now;
        observedTicks++;

        if (observedTicks >= Config.WORLD_ENTRY_OBSERVATION_TICKS.get()) {
            flushWorldJoinWindow();
        }
    }

    private void flushWorldJoinWindow() {
        if (!observingWorldJoin) {
            return;
        }
        observingWorldJoin = false;
        worldJoinWindow.close();
        foundation.recordWorldJoinWindow(observedTicks, stallCount, maxFrameDeltaNanos);
        foundation.finishWorldJoinSession(worldJoinSession, observedTicks, stallCount, maxFrameDeltaNanos);
        worldJoinWindow = () -> {
        };
        worldJoinSession = () -> {
        };
    }
}
