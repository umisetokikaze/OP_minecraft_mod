package io.github.umisetokikaze;

import io.github.umisetokikaze.foundation.client.ClientProfilingController;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = momooptimizer.MODID, dist = Dist.CLIENT)
public final class momooptimizerClient {
    public momooptimizerClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        new ClientProfilingController(modEventBus);
    }
}
