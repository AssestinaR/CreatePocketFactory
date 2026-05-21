package com.assestinar.createpocketfactory.item;

import java.util.OptionalInt;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
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
    private static final String PROJECTION_ANCHOR_TAG = "ProjectionAnchor";
    private static final String PROJECTION_ROTATION_TAG = "ProjectionRotation";
    private static final String PROJECTION_FLIP_X_TAG = "ProjectionFlipX";
    private static final String PROJECTION_FLIP_Z_TAG = "ProjectionFlipZ";
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

    public static Optional<BlockPos> getProjectionAnchor(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(PROJECTION_ANCHOR_TAG, Tag.TAG_LONG)) {
            return Optional.empty();
        }

        return Optional.of(BlockPos.of(tag.getLong(PROJECTION_ANCHOR_TAG)));
    }

    public static void setProjectionAnchor(ItemStack stack, BlockPos anchor) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putLong(PROJECTION_ANCHOR_TAG, anchor.asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearProjectionAnchor(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.remove(PROJECTION_ANCHOR_TAG);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getProjectionRotationQuarterTurns(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return Math.floorMod(tag.getInt(PROJECTION_ROTATION_TAG), 4);
    }

    public static boolean isProjectionFlipX(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean(PROJECTION_FLIP_X_TAG);
    }

    public static boolean isProjectionFlipZ(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean(PROJECTION_FLIP_Z_TAG);
    }

    public static void setProjectionTransform(ItemStack stack, int rotationQuarterTurns, boolean flipX, boolean flipZ) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(PROJECTION_ROTATION_TAG, Math.floorMod(rotationQuarterTurns, 4));
        tag.putBoolean(PROJECTION_FLIP_X_TAG, flipX);
        tag.putBoolean(PROJECTION_FLIP_Z_TAG, flipZ);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack createBoundStack(Block block, int factoryId) {
        return createBoundStack(block, factoryId, null, 0, false, false);
    }

    public static ItemStack createBoundStack(Block block, int factoryId, BlockPos projectionAnchor) {
        return createBoundStack(block, factoryId, projectionAnchor, 0, false, false);
    }

    public static ItemStack createBoundStack(Block block, int factoryId, BlockPos projectionAnchor,
                                             int rotationQuarterTurns, boolean flipX, boolean flipZ) {
        ItemStack stack = new ItemStack(block.asItem());
        setFactoryId(stack, factoryId);
        if (projectionAnchor != null) {
            setProjectionAnchor(stack, projectionAnchor);
        }
        setProjectionTransform(stack, rotationQuarterTurns, flipX, flipZ);
        return stack;
    }
}