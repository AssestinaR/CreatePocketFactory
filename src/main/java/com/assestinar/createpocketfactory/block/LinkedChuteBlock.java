package com.assestinar.createpocketfactory.block;

import com.assestinar.createpocketfactory.block.entity.ModBlockEntities;
import com.assestinar.createpocketfactory.block.entity.BindingEndpointHelper;
import com.assestinar.createpocketfactory.block.entity.LinkedChuteBlockEntity;
import com.assestinar.createpocketfactory.block.entity.LinkedStorageBindingHelper;
import com.assestinar.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.chute.ChuteBlock;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public final class LinkedChuteBlock extends ChuteBlock {
    private static final Set<String> RESTORING_ENDPOINTS = Collections.synchronizedSet(new HashSet<>());

    public LinkedChuteBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<ChuteBlockEntity> getBlockEntityClass() {
        return (Class<ChuteBlockEntity>) (Class<?>) LinkedChuteBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChuteBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_CHUTE.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LinkedChuteBlockEntity chute)) {
            return;
        }

        if (!chute.hasBinding()) {
            markRestoringEndpoint(level, pos);
            try {
                level.setBlock(pos, toVanillaChuteState(state), Block.UPDATE_ALL_IMMEDIATE);
            } finally {
                consumeRestoringEndpoint(level, pos);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getServer() != null) {
            if (consumeRestoringEndpoint(level, pos)) {
                super.onRemove(state, level, pos, newState, movedByPiston);
                return;
            }

            if (level.getBlockEntity(pos) instanceof LinkedChuteBlockEntity chute && chute.hasBinding()) {
                PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
                PocketFactorySavedData.BindingEndpoints endpoints = savedData.getBindingEndpoints(
                        chute.getBindingId(),
                        PocketFactorySavedData.BindingChannel.LINKED_CHUTE
                );
                savedData.disposeBinding(chute.getBindingId(), PocketFactorySavedData.BindingChannel.LINKED_CHUTE);
                restoreOppositeEndpoint(level, pos, endpoints);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(AllBlocks.CHUTE.get()));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return com.assestinar.createpocketfactory.item.ModEnchantments.applyLinkedEnchantment(
                new ItemStack(asItem()),
                player.level().registryAccess()
        );
    }

    private static void restoreOppositeEndpoint(Level level, BlockPos removedPos,
                                                @Nullable PocketFactorySavedData.BindingEndpoints endpoints) {
        if (endpoints == null || level.getServer() == null) {
            return;
        }

        String removedEndpointKey = LinkedStorageBindingHelper.endpointKey(level, removedPos);
        String oppositeEndpointKey = BindingEndpointHelper.resolveOppositeEndpointKey(endpoints, removedEndpointKey);
        if (oppositeEndpointKey == null) {
            return;
        }

        LinkedStorageBindingHelper.EndpointLocation endpointLocation = LinkedStorageBindingHelper.parseEndpointKey(oppositeEndpointKey);
        if (endpointLocation == null) {
            return;
        }

        Level targetLevel = level.getServer().getLevel(endpointLocation.dimension());
        if (targetLevel == null || !(targetLevel.getBlockEntity(endpointLocation.pos()) instanceof LinkedChuteBlockEntity)) {
            return;
        }

        markRestoringEndpoint(targetLevel, endpointLocation.pos());
        try {
            targetLevel.setBlock(endpointLocation.pos(), toVanillaChuteState(targetLevel.getBlockState(endpointLocation.pos())), Block.UPDATE_ALL_IMMEDIATE);
        } finally {
            consumeRestoringEndpoint(targetLevel, endpointLocation.pos());
        }
    }

    private static void markRestoringEndpoint(Level level, BlockPos pos) {
        RESTORING_ENDPOINTS.add(restoringKey(level, pos));
    }

    private static boolean consumeRestoringEndpoint(Level level, BlockPos pos) {
        return RESTORING_ENDPOINTS.remove(restoringKey(level, pos));
    }

    private static String restoringKey(Level level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockState toVanillaChuteState(BlockState state) {
        return AllBlocks.CHUTE.getDefaultState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(SHAPE, state.getValue(SHAPE))
                .setValue(WATERLOGGED, state.getValue(WATERLOGGED));
    }
}