package com.modmake.createpocketfactory.foundation.ponder;

import com.modmake.createpocketfactory.CreatePocketFactory;
import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.item.ModItems;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class AllPocketFactoryPonderTags {
    public static final ResourceLocation POCKET_FACTORY = loc("pocket_factory");

    private AllPocketFactoryPonderTags() {
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(POCKET_FACTORY)
            .addToIndex()
            .item(ModItems.POCKET_FACTORY_INTERNAL_EYE.get(), true, false)
            .title("口袋工厂")
            .description("进入独立维度工厂，并使用关联装置跨维度传输物品、流体与动力")
            .register();

        helper.addTagToComponent(ModItems.POCKET_FACTORY_INTERNAL_EYE.getId(), POCKET_FACTORY);
        helper.addTagToComponent(ModBlocks.POCKET_FACTORY_ENTRANCE.getId(), POCKET_FACTORY);
        helper.addTagToComponent(ModBlocks.LINKED_CHUTE.getId(), POCKET_FACTORY);
        helper.addTagToComponent(ModBlocks.LINKED_MECHANICAL_PUMP.getId(), POCKET_FACTORY);
        helper.addTagToComponent(ModBlocks.LINKED_CLUTCH.getId(), POCKET_FACTORY);
        helper.addTagToComponent(ModBlocks.LINKED_ITEM_VAULT.getId(), POCKET_FACTORY);
        helper.addTagToComponent(ModBlocks.LINKED_FLUID_TANK.getId(), POCKET_FACTORY);
    }

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID, path);
    }
}