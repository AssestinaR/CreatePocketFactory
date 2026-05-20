package com.assestinar.createpocketfactory.client;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.block.ModBlocks;
import com.assestinar.createpocketfactory.block.entity.ModBlockEntities;
import com.assestinar.createpocketfactory.client.render.PocketFactoryEntranceRenderer;
import com.assestinar.createpocketfactory.foundation.ponder.PocketFactoryPonderPlugin;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.fluids.pump.PumpRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import com.simibubi.create.content.fluids.tank.FluidTankModel;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import com.simibubi.create.content.logistics.chute.ChuteRenderer;
import com.simibubi.create.foundation.block.connected.CTModel;
import net.createmod.ponder.foundation.PonderIndex;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CreatePocketFactory.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.POCKET_FACTORY_ENTRANCE.get(), PocketFactoryEntranceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LINKED_CHUTE.get(), ChuteRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LINKED_CLUTCH.get(), SplitShaftRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LINKED_MECHANICAL_PUMP.get(), PumpRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LINKED_FLUID_TANK.get(), FluidTankRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PonderIndex.addPlugin(new PocketFactoryPonderPlugin());
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.LINKED_CLUTCH.get())
                .factory(SplitShaftVisual::new)
                .skipVanillaRender(blockEntity -> false)
                .apply();
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.LINKED_MECHANICAL_PUMP.get())
                .factory(SingleAxisRotatingVisual.ofZ(AllPartialModels.MECHANICAL_PUMP_COG))
                .skipVanillaRender(blockEntity -> false)
                .apply();
            CreateClient.MODEL_SWAPPER.getCustomBlockModels().register(
                    BuiltInRegistries.BLOCK.getKey(ModBlocks.LINKED_ITEM_VAULT.get()),
                    model -> new CTModel(model, new LinkedItemVaultCTBehaviour())
            );
            CreateClient.MODEL_SWAPPER.getCustomBlockModels().register(
                    BuiltInRegistries.BLOCK.getKey(ModBlocks.LINKED_FLUID_TANK.get()),
                    FluidTankModel::standard
            );
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LINKED_CLUTCH.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LINKED_CHUTE.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LINKED_FLUID_TANK.get(), RenderType.cutoutMipped());
        });
    }
}
