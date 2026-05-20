package com.assestinar.createpocketfactory.world;

import com.assestinar.createpocketfactory.data.ModAttachments;
import com.assestinar.createpocketfactory.data.ReturnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PocketFactoryTeleporter {
    private PocketFactoryTeleporter() {
    }

    public static boolean enterFactory(ServerPlayer player) {
        PocketFactorySavedData.FactoryRecord factory = PocketFactorySavedData.get(player.server).getOrCreateDebugFactory(player.getUUID());
        return enterFactory(player, factory.id());
    }

    public static boolean enterFactory(ServerPlayer player, int factoryId) {
        ServerLevel targetLevel = player.server.getLevel(PocketFactoryDimensions.LEVEL_KEY);
        if (targetLevel == null) {
            player.sendSystemMessage(Component.translatable("message.create_pocket_factory.factory_missing"));
            return false;
        }

        PocketFactorySavedData.FactoryRecord factory = PocketFactorySavedData.get(player.server).getFactory(factoryId);
        if (factory == null) {
            player.sendSystemMessage(Component.translatable("message.create_pocket_factory.factory_missing"));
            return false;
        }

        player.setData(ModAttachments.RETURN_POINT.get(), ReturnPointData.capture(player));
        PocketFactoryDimensions.prepareFactory(targetLevel, factory);
        PocketFactoryDimensions.teleportToFactory(player, targetLevel, factory);
        player.sendSystemMessage(Component.translatable("message.create_pocket_factory.entered"));
        return true;
    }

    public static boolean returnFromFactory(ServerPlayer player) {
        ReturnPointData returnPoint = player.getData(ModAttachments.RETURN_POINT.get());
        ServerLevel returnLevel = returnPoint.valid() ? player.server.getLevel(returnPoint.dimensionKey()) : null;

        if (returnLevel != null && PocketFactoryDimensions.isReturnPointUsable(returnLevel, returnPoint)) {
            player.teleportTo(returnLevel, returnPoint.x(), returnPoint.y(), returnPoint.z(), returnPoint.yRot(), returnPoint.xRot());
            player.sendSystemMessage(Component.translatable("message.create_pocket_factory.returned"));
        } else {
            ServerLevel overworld = player.server.overworld();
            BlockPos sharedSpawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, sharedSpawn.getX() + 0.5D, sharedSpawn.getY() + 0.1D, sharedSpawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.translatable("message.create_pocket_factory.return_fallback"));
        }

        player.setData(ModAttachments.RETURN_POINT.get(), ReturnPointData.empty());
        return true;
    }
}