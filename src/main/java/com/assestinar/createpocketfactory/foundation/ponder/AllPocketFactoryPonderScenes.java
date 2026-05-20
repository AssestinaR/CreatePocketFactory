package com.assestinar.createpocketfactory.foundation.ponder;

import com.assestinar.createpocketfactory.block.ModBlocks;
import com.assestinar.createpocketfactory.foundation.ponder.scenes.PocketFactoryScenes;
import com.assestinar.createpocketfactory.item.ModItems;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class AllPocketFactoryPonderScenes {
    private AllPocketFactoryPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModItems.POCKET_FACTORY_INTERNAL_EYE.getId())
            .addStoryBoard("pocket_factory/core_expand", PocketFactoryScenes::coreExpand,
                AllPocketFactoryPonderTags.POCKET_FACTORY)
            .addStoryBoard("pocket_factory/core_link", PocketFactoryScenes::coreLink,
                AllPocketFactoryPonderTags.POCKET_FACTORY);

        helper.forComponents(ModBlocks.POCKET_FACTORY_ENTRANCE.getId())
            .addStoryBoard("pocket_factory/entrance_overview", PocketFactoryScenes::entranceOverview,
                AllPocketFactoryPonderTags.POCKET_FACTORY)
            .addStoryBoard("pocket_factory/entrance_exit", PocketFactoryScenes::entranceExitOverview,
                AllPocketFactoryPonderTags.POCKET_FACTORY);

        helper.forComponents(ModBlocks.LINKED_CHUTE.getId())
            .addStoryBoard("pocket_factory/linked_chute", PocketFactoryScenes::linkedChuteOverview,
                AllPocketFactoryPonderTags.POCKET_FACTORY)
            .addStoryBoard("pocket_factory/linked_chute_fan", PocketFactoryScenes::linkedChuteFanOverview,
                AllPocketFactoryPonderTags.POCKET_FACTORY);

        helper.forComponents(ModBlocks.LINKED_MECHANICAL_PUMP.getId())
            .addStoryBoard("pocket_factory/linked_pump", PocketFactoryScenes::linkedPumpOverview,
                AllPocketFactoryPonderTags.POCKET_FACTORY);

        helper.forComponents(ModBlocks.LINKED_CLUTCH.getId())
            .addStoryBoard("pocket_factory/linked_clutch", PocketFactoryScenes::linkedClutchOverview,
                AllPocketFactoryPonderTags.POCKET_FACTORY);
    }
}