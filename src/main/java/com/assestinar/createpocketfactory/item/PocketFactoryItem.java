package com.assestinar.createpocketfactory.item;

import com.assestinar.createpocketfactory.debug.PocketFactoryDebugMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class PocketFactoryItem extends Item {
    public PocketFactoryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (!PocketFactoryDebugMenu.canUsePortable(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        PocketFactoryDebugMenu.openPortable(serverPlayer);
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_eye.menu"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_eye.teleport"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_eye.prototype"));
    }
}