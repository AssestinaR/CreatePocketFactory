package com.modmake.createpocketfactory.block;

import com.modmake.createpocketfactory.world.PocketFactoryDimensions;
import com.modmake.createpocketfactory.world.PocketFactoryTeleporter;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PocketFactoryExitBlock extends Block {
    public static final MapCodec<PocketFactoryExitBlock> CODEC = simpleCodec(PocketFactoryExitBlock::new);

    public PocketFactoryExitBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.dimension() != PocketFactoryDimensions.LEVEL_KEY) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        return PocketFactoryTeleporter.returnFromFactory(serverPlayer) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return asItemInteractionResult(useWithoutItem(state, level, pos, player, hitResult));
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    private static ItemInteractionResult asItemInteractionResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS, CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.SUCCESS;
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }
}