package com.assestinar.createpocketfactory.block.entity;

import javax.annotation.Nullable;

import com.assestinar.createpocketfactory.world.PocketFactorySavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class BindingEndpointHelper {
    private BindingEndpointHelper() {
    }

    public static String endpointKey(Level level, BlockPos endpointPos) {
        return level.dimension().location() + "@" + endpointPos.getX() + "," + endpointPos.getY() + "," + endpointPos.getZ();
    }

    public static @Nullable EndpointLocation parseEndpointKey(String endpointKey) {
        int separatorIndex = endpointKey.indexOf('@');
        if (separatorIndex <= 0 || separatorIndex >= endpointKey.length() - 1) {
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(endpointKey.substring(0, separatorIndex));
        if (dimensionId == null) {
            return null;
        }

        String[] coordinateParts = endpointKey.substring(separatorIndex + 1).split(",", 3);
        if (coordinateParts.length != 3) {
            return null;
        }

        try {
            BlockPos pos = new BlockPos(
                    Integer.parseInt(coordinateParts[0]),
                    Integer.parseInt(coordinateParts[1]),
                    Integer.parseInt(coordinateParts[2])
            );
            return new EndpointLocation(ResourceKey.create(Registries.DIMENSION, dimensionId), pos);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static @Nullable String resolveOppositeEndpointKey(PocketFactorySavedData.BindingEndpoints endpoints, String localEndpointKey) {
        if (localEndpointKey.equals(endpoints.internalEndpointKey())) {
            return endpoints.externalEndpointKey();
        }
        if (localEndpointKey.equals(endpoints.externalEndpointKey())) {
            return endpoints.internalEndpointKey();
        }
        if (endpoints.internalEndpointKey() != null && !endpoints.internalEndpointKey().equals(localEndpointKey)) {
            return endpoints.internalEndpointKey();
        }
        if (endpoints.externalEndpointKey() != null && !endpoints.externalEndpointKey().equals(localEndpointKey)) {
            return endpoints.externalEndpointKey();
        }
        return null;
    }

    public record EndpointLocation(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
