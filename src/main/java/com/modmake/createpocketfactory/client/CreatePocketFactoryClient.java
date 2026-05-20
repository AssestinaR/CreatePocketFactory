package com.assestinar.createpocketfactory.client;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.config.ModConfigs;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreatePocketFactory.MOD_ID, dist = Dist.CLIENT)
public final class CreatePocketFactoryClient {
    public CreatePocketFactoryClient(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ModConfigs.CLIENT_SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (game, previousScreen) -> new BaseConfigScreen(previousScreen, CreatePocketFactory.MOD_ID));

        BaseConfigScreen.setDefaultActionFor(CreatePocketFactory.MOD_ID, base -> base
            .withButtonLabels(null, "Visual Settings", "Gameplay Settings")
            .withSpecs(null, ModConfigs.CLIENT_SPEC, ModConfigs.SERVER_SPEC));
    }
}