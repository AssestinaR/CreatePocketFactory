package com.assestinar.createpocketfactory.item;

import java.util.List;
import java.util.OptionalInt;
import com.simibubi.create.content.logistics.chute.ChuteItem;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;

public final class LinkedChuteBlockItem extends ChuteItem {
    private static final String BINDING_ID_TAG = "PocketFactoryBindingId";
    private static final String FACTORY_ID_TAG = "PocketFactoryId";
    private static final Style TOOLTIP_STYLE = Style.EMPTY.withColor(TextColor.fromRgb(0xB7BFD0));

    public LinkedChuteBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return ModEnchantments.isLinkedEnchantment(enchantment);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        OptionalInt bindingId = getBindingId(stack);
        if (bindingId.isPresent()) {
            tooltipComponents.add(Component.translatable("goggles.create_pocket_factory.common.binding_id", bindingId.getAsInt())
                    .withStyle(TOOLTIP_STYLE));
        }

        OptionalInt factoryId = getFactoryId(stack);
        MutableComponent line = factoryId.isPresent()
                ? Component.translatable("tooltip.create_pocket_factory.linked_chute.factory_id", factoryId.getAsInt())
                : Component.translatable("tooltip.create_pocket_factory.linked_chute.unbound");
        tooltipComponents.add(line.withStyle(TOOLTIP_STYLE));
    }

    public static OptionalInt getBindingId(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(BINDING_ID_TAG, Tag.TAG_INT)) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(tag.getInt(BINDING_ID_TAG));
    }

    public static void setBindingId(ItemStack stack, int bindingId) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(BINDING_ID_TAG, bindingId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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

    public static ItemStack createBoundStack(Block block, int bindingId, int factoryId) {
        ItemStack stack = new ItemStack(block.asItem());
        setBindingId(stack, bindingId);
        setFactoryId(stack, factoryId);
        return stack;
    }
}