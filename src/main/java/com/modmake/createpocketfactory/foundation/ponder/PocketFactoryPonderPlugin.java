package com.assestinar.createpocketfactory.foundation.ponder;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class PocketFactoryPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreatePocketFactory.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        AllPocketFactoryPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        AllPocketFactoryPonderTags.register(helper);
    }
}