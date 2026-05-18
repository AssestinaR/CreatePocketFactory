package com.modmake.createpocketfactory.block;

import com.modmake.createpocketfactory.block.entity.LinkedFluidPipeBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedPipeBindingHelper;
import com.modmake.createpocketfactory.block.entity.LinkedPipeEndpoint;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class LinkedEncasedPipeBlock extends EncasedPipeBlock {
    public LinkedEncasedPipeBlock(BlockBehaviour.Properties properties, Supplier<Block> casing) {
        super(properties, casing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<FluidPipeBlockEntity> getBlockEntityClass() {
        return (Class<FluidPipeBlockEntity>) (Class<?>) LinkedFluidPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LinkedFluidPipeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_ENCASED_FLUID_PIPE.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && !(level.getBlockEntity(pos) instanceof LinkedPipeEndpoint linkedPipe && linkedPipe.hasBinding())) {
            level.setBlock(pos, LinkedPipeBindingHelper.toVanillaState(state), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !world.isClientSide && world.getServer() != null
                && !LinkedPipeBindingHelper.isLinkedPipe(newState)
                && world.getBlockEntity(pos) instanceof LinkedPipeEndpoint linkedPipe
                && linkedPipe.hasBinding()) {
            PocketFactorySavedData savedData = PocketFactorySavedData.get(world.getServer());
            PocketFactorySavedData.BindingEndpoints endpoints = savedData.getBindingEndpoints(
                    linkedPipe.getBindingId(),
                    PocketFactorySavedData.BindingChannel.LINKED_PIPE);
            savedData.disposeBinding(linkedPipe.getBindingId(), PocketFactorySavedData.BindingChannel.LINKED_PIPE);
            LinkedPipeBindingHelper.restoreOppositeEndpoint(world, pos, endpoints);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        LinkedPipeEndpoint linkedPipe = LinkedPipeBindingHelper.getLinkedPipe(world, pos);
        int bindingId = linkedPipe == null ? -1 : linkedPipe.getBindingId();
        int factoryId = linkedPipe == null ? -1 : linkedPipe.getFactoryId();
        boolean internalEndpoint = linkedPipe != null && linkedPipe.isInternalEndpoint();
        BlockState newState = EncasedPipeBlock.transferSixWayProperties(state, ModBlocks.LINKED_FLUID_PIPE.get().defaultBlockState());
        LinkedPipeBindingHelper.switchVariant(world, pos, newState, bindingId, factoryId, internalEndpoint);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(AllBlocks.FLUID_PIPE.asStack());
    }
}
