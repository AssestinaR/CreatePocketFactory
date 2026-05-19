package com.modmake.createpocketfactory.block;

import com.modmake.createpocketfactory.block.entity.PocketFactoryPortalBlockEntity;
import com.modmake.createpocketfactory.world.PocketFactoryDimensions;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

public final class PocketFactoryPortalBlock extends BaseEntityBlock {
    public static final MapCodec<PocketFactoryPortalBlock> CODEC = simpleCodec(PocketFactoryPortalBlock::new);

    public PocketFactoryPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PocketFactoryPortalBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || level.getServer() == null) {
            return;
        }

        PocketFactoryPortalBlockEntity blockEntity = getPortalBlockEntity(level, pos);
        if (blockEntity == null) {
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        if (PocketFactoryDimensions.LEVEL_KEY.equals(level.dimension())) {
            PocketFactorySavedData.FactoryRecord factory = PocketFactoryDimensions.findFactoryAt(savedData, pos);
            if (factory != null) {
                blockEntity.setBinding(factory.id(), true);
                savedData.registerPortalEndpoint(factory.id(), PocketFactorySavedData.PortalEndpoint.INTERNAL, level.dimension(), pos);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getServer() != null) {
            PocketFactoryPortalBlockEntity blockEntity = getPortalBlockEntity(level, pos);
            if (blockEntity != null && blockEntity.hasFactoryId()) {
                PocketFactorySavedData.get(level.getServer()).clearPortalEndpointIfMatches(
                        blockEntity.getFactoryId(),
                        blockEntity.isInternalEndpoint() ? PocketFactorySavedData.PortalEndpoint.INTERNAL : PocketFactorySavedData.PortalEndpoint.EXTERNAL,
                        level.dimension(),
                        pos
                );
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide() && entity instanceof ItemEntity itemEntity) {
            PocketFactoryPortalBlockEntity blockEntity = getPortalBlockEntity(level, pos);
            if (blockEntity != null) {
                blockEntity.tryTransportItemEntity(itemEntity);
            }
        }

        super.entityInside(state, level, pos, entity);
    }

    private static @Nullable PocketFactoryPortalBlockEntity getPortalBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof PocketFactoryPortalBlockEntity portalBlockEntity ? portalBlockEntity : null;
    }
}