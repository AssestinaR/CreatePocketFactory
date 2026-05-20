package com.assestinar.createpocketfactory;

import com.assestinar.createpocketfactory.data.ModAttachments;
import com.assestinar.createpocketfactory.network.ModNetworking;
import com.assestinar.createpocketfactory.block.ModBlocks;
import com.assestinar.createpocketfactory.block.entity.ModBlockEntities;
import com.assestinar.createpocketfactory.config.ModConfigs;
import com.assestinar.createpocketfactory.debug.PocketFactoryDebugCommands;
import com.assestinar.createpocketfactory.item.ModItems;
import com.assestinar.createpocketfactory.world.PocketFactoryDimensions;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(CreatePocketFactory.MOD_ID)
public final class CreatePocketFactory {
    public static final String MOD_ID = "create_pocket_factory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreatePocketFactory(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModItems.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, ModConfigs.SERVER_SPEC);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        LOGGER.info("Create: Pocket Factory initialized");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModBlocks::registerStressDefaults);
    }

    private void onServerStarted(ServerStartedEvent event) {
        PocketFactoryDimensions.initializeFactoryDimension(event.getServer());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        PocketFactoryDebugCommands.register(event);
    }
}