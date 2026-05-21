package com.assestinar.createpocketfactory.block;

import com.assestinar.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.mojang.serialization.MapCodec;
import com.assestinar.createpocketfactory.world.PocketFactoryDimensions;
import com.assestinar.createpocketfactory.world.PocketFactorySavedData;
import com.assestinar.createpocketfactory.world.PocketFactoryTeleporter;
import com.assestinar.createpocketfactory.item.PocketFactoryEntranceBlockItem;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PocketFactoryEntranceBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<PocketFactoryEntranceBlock> CODEC = simpleCodec(PocketFactoryEntranceBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 4.0D),
        Block.box(0.0D, 0.0D, 12.0D, 16.0D, 4.0D, 16.0D),
        Block.box(0.0D, 0.0D, 4.0D, 4.0D, 4.0D, 12.0D),
        Block.box(12.0D, 0.0D, 4.0D, 16.0D, 4.0D, 12.0D)
    );

    public PocketFactoryEntranceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        if (level.dimension() == PocketFactoryDimensions.LEVEL_KEY) {
            serverPlayer.sendSystemMessage(Component.translatable("message.create_pocket_factory.entrance_block.disabled_in_factory"));
            return InteractionResult.CONSUME;
        }

        PocketFactoryEntranceBlockEntity blockEntity = getEntranceBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.FAIL;
        }

        int factoryId = ensureFactoryBinding(level, pos, blockEntity, serverPlayer, player.getMainHandItem());
        return PocketFactoryTeleporter.enterFactory(serverPlayer, factoryId) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return asItemInteractionResult(useWithoutItem(state, level, pos, player, hitResult));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PocketFactoryEntranceBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || level.getServer() == null) {
            return;
        }

        PocketFactoryEntranceBlockEntity blockEntity = getEntranceBlockEntity(level, pos);
        if (blockEntity == null) {
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        int factoryId = resolveFactoryId(savedData, stack, placer != null ? placer.getUUID() : null);

        blockEntity.setFactoryId(factoryId);
        blockEntity.setProjectionAnchor(PocketFactoryEntranceBlockItem.getProjectionAnchor(stack).orElse(null));
        blockEntity.setProjectionTransform(
            PocketFactoryEntranceBlockItem.getProjectionRotationQuarterTurns(stack),
            PocketFactoryEntranceBlockItem.isProjectionFlipX(stack),
            PocketFactoryEntranceBlockItem.isProjectionFlipZ(stack)
        );
        blockEntity.refreshPreviewSnapshot();
        savedData.bindEntrance(factoryId, level.dimension(), pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getServer() != null) {
            PocketFactoryEntranceBlockEntity blockEntity = getEntranceBlockEntity(level, pos);
            if (blockEntity != null && blockEntity.hasFactoryId()) {
                PocketFactorySavedData.get(level.getServer()).clearEntranceIfMatches(blockEntity.getFactoryId(), level.dimension(), pos);
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof PocketFactoryEntranceBlockEntity entranceBlockEntity && entranceBlockEntity.hasFactoryId()) {
            return List.of(PocketFactoryEntranceBlockItem.createBoundStack(
                    this,
                    entranceBlockEntity.getFactoryId(),
                    entranceBlockEntity.getProjectionAnchor(),
                    entranceBlockEntity.getProjectionRotationQuarterTurns(),
                    entranceBlockEntity.isProjectionFlipX(),
                    entranceBlockEntity.isProjectionFlipZ()
            ));
        }

        return super.getDrops(state, params);
    }

    private static @Nullable PocketFactoryEntranceBlockEntity getEntranceBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof PocketFactoryEntranceBlockEntity entranceBlockEntity ? entranceBlockEntity : null;
    }

    private static int ensureFactoryBinding(Level level, BlockPos pos, PocketFactoryEntranceBlockEntity blockEntity, ServerPlayer player, ItemStack stack) {
        if (blockEntity.hasFactoryId()) {
            return blockEntity.getFactoryId();
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(player.server);
        int factoryId = resolveFactoryId(savedData, stack, player.getUUID());
        blockEntity.setFactoryId(factoryId);
        savedData.bindEntrance(factoryId, level.dimension(), pos);
        return factoryId;
    }

    private static int resolveFactoryId(PocketFactorySavedData savedData, ItemStack stack, @Nullable java.util.UUID owner) {
        java.util.OptionalInt existingFactoryId = PocketFactoryEntranceBlockItem.getFactoryId(stack);
        if (existingFactoryId.isPresent() && savedData.getFactory(existingFactoryId.getAsInt()) != null) {
            return existingFactoryId.getAsInt();
        }

        return savedData.createFactory(owner).id();
    }

    private static ItemInteractionResult asItemInteractionResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS, CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.SUCCESS;
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }
}