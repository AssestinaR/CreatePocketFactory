package com.modmake.createpocketfactory.item;

import java.util.OptionalInt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import java.util.List;

public final class PocketFactoryEntranceBlockItem extends BlockItem {
    private static final String FACTORY_ID_TAG = "PocketFactoryId";
    private static final Style TOOLTIP_STYLE = Style.EMPTY.withColor(TextColor.fromRgb(0xB7BFD0));

    public PocketFactoryEntranceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        OptionalInt factoryId = getFactoryId(stack);
        MutableComponent line = factoryId.isPresent()
                ? Component.translatable("tooltip.create_pocket_factory.pocket_factory_entrance.factory_id", factoryId.getAsInt())
                : Component.translatable("tooltip.create_pocket_factory.pocket_factory_entrance.unbound");
        tooltipComponents.add(line.withStyle(TOOLTIP_STYLE));
    }

    public static OptionalInt getFactoryId(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(FACTORY_ID_TAG, Tag.TAG_INT)) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(tag.getInt(FACTORY_ID_TAG));
    }

    public static void setFactoryId(ItemStack stack, int factoryId) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(FACTORY_ID_TAG, factoryId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack createBoundStack(Block block, int factoryId) {
        ItemStack stack = new ItemStack(block.asItem());
        setFactoryId(stack, factoryId);
        return stack;
    }
}