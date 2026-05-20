package com.assestinar.createpocketfactory.item;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class SelectedEndpointTooltipHelper {
    private SelectedEndpointTooltipHelper() {
    }

    public static void appendSelectedEndpointTooltip(List<Component> tooltip,
                                                     Component endpointLabel,
                                                     ResourceKey<Level> dimension,
                                                     BlockPos pos,
                                                     Style style) {
        tooltip.add(endpointLabel.copy().withStyle(style));
        tooltip.add(Component.literal(dimension.location().toString()).withStyle(style));
        tooltip.add(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(style));
    }
}