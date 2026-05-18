package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

public final class LinkedStorageBindingHelper {
    private LinkedStorageBindingHelper() {
    }

    public static String endpointKey(Level level, BlockPos controllerPos) {
        return BindingEndpointHelper.endpointKey(level, controllerPos);
    }

    public static @Nullable EndpointLocation parseEndpointKey(String endpointKey) {
        BindingEndpointHelper.EndpointLocation endpointLocation = BindingEndpointHelper.parseEndpointKey(endpointKey);
        if (endpointLocation == null) {
            return null;
        }
        return new EndpointLocation(endpointLocation.dimension(), endpointLocation.pos());
    }

    public static void forEachBox(BlockPos origin, int sizeX, int sizeY, int sizeZ, Consumer<BlockPos> consumer) {
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    consumer.accept(origin.offset(x, y, z));
                }
            }
        }
    }

    public record BindingTarget(int bindingId, int factoryId, @Nullable PocketFactorySavedData.FactoryChunkOffset chunkOffset) {
    }

    public record EndpointLocation(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
