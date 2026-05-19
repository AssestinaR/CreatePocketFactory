package com.modmake.createpocketfactory.client;

import com.modmake.createpocketfactory.CreatePocketFactory;
import com.modmake.createpocketfactory.config.ModConfigs;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreatePocketFactory.MOD_ID, dist = Dist.CLIENT)
public final class CreatePocketFactoryClient {
    public CreatePocketFactoryClient(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (game, previousScreen) -> new BaseConfigScreen(previousScreen, CreatePocketFactory.MOD_ID));

        BaseConfigScreen.setDefaultActionFor(CreatePocketFactory.MOD_ID, base -> base
                .withButtonLabels(null, null, "Gameplay Settings")
                .withSpecs(null, null, ModConfigs.SERVER_SPEC));
    }
}