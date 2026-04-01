package io.github.umisetokikaze;

import com.mojang.logging.LogUtils;
import io.github.umisetokikaze.foundation.CacheCommands;
import io.github.umisetokikaze.foundation.ProfilingFoundation;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(momooptimizer.MODID)
public final class momooptimizer {
    public static final String MODID = "momooptimizer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public momooptimizer(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ProfilingFoundation.getInstance().onCommonSetup());
    }

    private void onServerStarting(ServerStartingEvent event) {
        ProfilingFoundation.getInstance().onServerStarting();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CacheCommands.register(event.getDispatcher(), ProfilingFoundation.getInstance());
    }
}
