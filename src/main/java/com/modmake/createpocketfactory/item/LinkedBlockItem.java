package com.modmake.createpocketfactory.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;

public class LinkedBlockItem extends BlockItem {
    public LinkedBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return ModEnchantments.isLinkedEnchantment(enchantment);
    }
}