package com.modmake.createpocketfactory.item;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class ModEnchantments {
    public static final ResourceKey<Enchantment> LINKED = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath("create_pocket_factory", "linked")
    );

    private ModEnchantments() {
    }

    public static boolean isLinkedEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(LINKED);
    }

    public static ItemStack applyLinkedEnchantment(ItemStack stack, HolderLookup.Provider registries) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(stack.getTagEnchantments());
        enchantments.set(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(LINKED), 1);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }
}