package com.assestinar.createpocketfactory.block;

import com.assestinar.createpocketfactory.item.ModItems;
import com.assestinar.createpocketfactory.world.PocketFactoryDimensions;
import com.assestinar.createpocketfactory.world.PocketFactorySavedData;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class PocketFactoryBoundaryBlock extends Block {
    public static final MapCodec<PocketFactoryBoundaryBlock> CODEC = simpleCodec(PocketFactoryBoundaryBlock::new);

    public PocketFactoryBoundaryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModItems.POCKET_FACTORY_INTERNAL_EYE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.dimension() != PocketFactoryDimensions.LEVEL_KEY) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(serverPlayer.server);
        PocketFactorySavedData.FactoryRecord factory = PocketFactoryDimensions.findFactoryAt(savedData, pos);
        if (factory == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (hitResult.getDirection().getAxis().isHorizontal()) {
            Direction expansionDirection = hitResult.getDirection().getOpposite();
            if (!PocketFactoryDimensions.isValidHorizontalExpansionFace(factory, pos, expansionDirection)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            PocketFactorySavedData.FactoryChunkOffset currentChunk = PocketFactoryDimensions.getChunkOffsetAt(factory, pos);
            int targetChunkX = currentChunk.x() + expansionDirection.getStepX();
            int targetChunkZ = currentChunk.z() + expansionDirection.getStepZ();

            if (Math.abs(targetChunkX) > PocketFactoryDimensions.MAX_HORIZONTAL_RADIUS_CHUNKS
                    || Math.abs(targetChunkZ) > PocketFactoryDimensions.MAX_HORIZONTAL_RADIUS_CHUNKS) {
                serverPlayer.sendSystemMessage(Component.translatable("message.create_pocket_factory.expand.out_of_range"));
                return ItemInteractionResult.FAIL;
            }

            PocketFactorySavedData.FactoryRecord updatedFactory = factory.withAddedChunk(targetChunkX, targetChunkZ);
            savedData.updateFactory(updatedFactory);
            PocketFactoryDimensions.applyExpansion(serverLevel, factory, updatedFactory);

            if (!serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
            }

            serverPlayer.sendSystemMessage(Component.translatable("message.create_pocket_factory.expand.horizontal_success"));
            return ItemInteractionResult.SUCCESS;
        }

        int verticalStep = 1;
        if (hitResult.getDirection() == Direction.UP && PocketFactoryDimensions.isBottomBoundary(factory, pos)) {
            int updatedMinY = factory.minY() - verticalStep;
            if (updatedMinY < level.getMinBuildHeight()) {
                serverPlayer.sendSystemMessage(Component.translatable("message.create_pocket_factory.expand.height_limit"));
                return ItemInteractionResult.FAIL;
            }

            PocketFactorySavedData.FactoryRecord updatedFactory = factory.withVerticalBounds(updatedMinY, factory.maxY());
            savedData.updateFactory(updatedFactory);
            PocketFactoryDimensions.applyExpansion(serverLevel, factory, updatedFactory);
            serverPlayer.sendSystemMessage(Component.translatable("message.create_pocket_factory.expand.vertical_success", verticalStep));
            return ItemInteractionResult.SUCCESS;
        }

        if (hitResult.getDirection() == Direction.DOWN && PocketFactoryDimensions.isTopBoundary(factory, pos)) {
            int updatedMaxY = factory.maxY() + verticalStep;
            if (updatedMaxY >= level.getMaxBuildHeight()) {
                serverPlayer.sendSystemMessage(Component.translatable("message.create_pocket_factory.expand.height_limit"));
                return ItemInteractionResult.FAIL;
            }

            PocketFactorySavedData.FactoryRecord updatedFactory = factory.withVerticalBounds(factory.minY(), updatedMaxY);
            savedData.updateFactory(updatedFactory);
            PocketFactoryDimensions.applyExpansion(serverLevel, factory, updatedFactory);
            serverPlayer.sendSystemMessage(Component.translatable("message.create_pocket_factory.expand.vertical_success", verticalStep));
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}