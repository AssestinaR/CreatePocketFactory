package com.modmake.createpocketfactory.block;

import com.modmake.createpocketfactory.block.entity.LinkedFluidPipeBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedPipeBindingHelper;
import com.modmake.createpocketfactory.block.entity.LinkedPipeEndpoint;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import java.util.List;
import javax.annotation.Nullable;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;

public class LinkedFluidPipeBlock extends FluidPipeBlock {
    public LinkedFluidPipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<FluidPipeBlockEntity> getBlockEntityClass() {
        return (Class<FluidPipeBlockEntity>) (Class<?>) LinkedFluidPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LinkedFluidPipeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_FLUID_PIPE.get();
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
        if (tryRemoveBracket(context)) {
            return InteractionResult.SUCCESS;
        }

        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        Axis axis = FluidPipeBlock.isCornerOrEndPipe(world, pos, state) ? null : com.simibubi.create.content.fluids.FluidPropagator.getStraightPipeAxis(state);
        if (axis == null) {
            axis = clickedFace.getAxis() == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
        }

        if (clickedFace.getAxis() == axis) {
            return InteractionResult.PASS;
        }
        if (!world.isClientSide) {
            LinkedPipeEndpoint linkedPipe = LinkedPipeBindingHelper.getLinkedPipe(world, pos);
            int bindingId = linkedPipe == null ? -1 : linkedPipe.getBindingId();
            int factoryId = linkedPipe == null ? -1 : linkedPipe.getFactoryId();
            boolean internalEndpoint = linkedPipe != null && linkedPipe.isInternalEndpoint();
            BlockState newState = ModBlocks.LINKED_GLASS_FLUID_PIPE.get().defaultBlockState()
                    .setValue(GlassFluidPipeBlock.AXIS, axis)
                    .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
            LinkedPipeBindingHelper.switchVariant(world, pos, newState, bindingId, factoryId, internalEndpoint);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (!AllBlocks.COPPER_CASING.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        LinkedPipeEndpoint linkedPipe = LinkedPipeBindingHelper.getLinkedPipe(level, pos);
        int bindingId = linkedPipe == null ? -1 : linkedPipe.getBindingId();
        int factoryId = linkedPipe == null ? -1 : linkedPipe.getFactoryId();
        boolean internalEndpoint = linkedPipe != null && linkedPipe.isInternalEndpoint();
        BlockState newState = com.simibubi.create.content.fluids.pipes.EncasedPipeBlock.transferSixWayProperties(
                state,
                ModBlocks.LINKED_ENCASED_FLUID_PIPE.get().defaultBlockState());
        LinkedPipeBindingHelper.switchVariant(level, pos, newState, bindingId, factoryId, internalEndpoint);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(AllBlocks.FLUID_PIPE.asStack());
    }
}
