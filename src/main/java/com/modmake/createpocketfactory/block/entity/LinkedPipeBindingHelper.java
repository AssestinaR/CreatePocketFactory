package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import javax.annotation.Nullable;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class LinkedPipeBindingHelper {
    private LinkedPipeBindingHelper() {
    }

    public static boolean isBindablePipe(BlockState state) {
        return AllBlocks.FLUID_PIPE.has(state);
    }

    public static boolean isLinkedPipe(BlockState state) {
        return state.is(ModBlocks.LINKED_FLUID_PIPE.get())
                || state.is(ModBlocks.LINKED_GLASS_FLUID_PIPE.get())
                || state.is(ModBlocks.LINKED_ENCASED_FLUID_PIPE.get());
    }

    public static @Nullable LinkedPipeEndpoint getLinkedPipe(LevelAccessor level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LinkedPipeEndpoint linkedPipe ? linkedPipe : null;
    }

    public static BlockState toLinkedState(BlockState vanillaState) {
        if (AllBlocks.FLUID_PIPE.has(vanillaState)) {
            BlockState target = ModBlocks.LINKED_FLUID_PIPE.get().defaultBlockState();
            for (Direction direction : Iterate.directions) {
                target = target.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                        vanillaState.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction)));
            }
            if (vanillaState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                target = target.setValue(BlockStateProperties.WATERLOGGED,
                        vanillaState.getValue(BlockStateProperties.WATERLOGGED));
            }
            return target;
        }

        if (AllBlocks.GLASS_FLUID_PIPE.has(vanillaState)) {
            return ModBlocks.LINKED_GLASS_FLUID_PIPE.get().defaultBlockState()
                    .setValue(AxisPipeBlock.AXIS, vanillaState.getValue(AxisPipeBlock.AXIS))
                    .setValue(GlassFluidPipeBlock.ALT, vanillaState.getValue(GlassFluidPipeBlock.ALT))
                    .setValue(BlockStateProperties.WATERLOGGED,
                            vanillaState.getValue(BlockStateProperties.WATERLOGGED));
        }

        if (AllBlocks.ENCASED_FLUID_PIPE.has(vanillaState)) {
            return EncasedPipeBlock.transferSixWayProperties(vanillaState,
                    ModBlocks.LINKED_ENCASED_FLUID_PIPE.get().defaultBlockState());
        }

        if (isLinkedPipe(vanillaState)) {
            return vanillaState;
        }

        throw new IllegalArgumentException("Unsupported pipe state: " + vanillaState);
    }

    public static BlockState toVanillaState(BlockState linkedState) {
        if (linkedState.is(ModBlocks.LINKED_FLUID_PIPE.get())) {
            BlockState target = AllBlocks.FLUID_PIPE.getDefaultState();
            for (Direction direction : Iterate.directions) {
                target = target.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                        linkedState.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction)));
            }
            if (linkedState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                target = target.setValue(BlockStateProperties.WATERLOGGED,
                        linkedState.getValue(BlockStateProperties.WATERLOGGED));
            }
            return target;
        }

        if (linkedState.is(ModBlocks.LINKED_GLASS_FLUID_PIPE.get())) {
            return AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
                    .setValue(AxisPipeBlock.AXIS, linkedState.getValue(AxisPipeBlock.AXIS))
                    .setValue(GlassFluidPipeBlock.ALT, linkedState.getValue(GlassFluidPipeBlock.ALT))
                    .setValue(BlockStateProperties.WATERLOGGED,
                            linkedState.getValue(BlockStateProperties.WATERLOGGED));
        }

        if (linkedState.is(ModBlocks.LINKED_ENCASED_FLUID_PIPE.get())) {
            return EncasedPipeBlock.transferSixWayProperties(linkedState,
                    AllBlocks.ENCASED_FLUID_PIPE.getDefaultState());
        }

        if (AllBlocks.FLUID_PIPE.has(linkedState)
                || AllBlocks.GLASS_FLUID_PIPE.has(linkedState)
                || AllBlocks.ENCASED_FLUID_PIPE.has(linkedState)) {
            return linkedState;
        }

        throw new IllegalArgumentException("Unsupported linked pipe state: " + linkedState);
    }

    public static BlockState toLinkedRegularState(LevelAccessor level, BlockPos pos, BlockState axisState) {
        Direction side = Direction.get(Direction.AxisDirection.POSITIVE, axisState.getValue(AxisPipeBlock.AXIS));
        BlockState openState = ModBlocks.LINKED_FLUID_PIPE.get().defaultBlockState();
        for (Direction direction : Iterate.directionsInAxis(axisState.getValue(AxisPipeBlock.AXIS))) {
            BooleanProperty property = FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction);
            openState = openState.setValue(property, true);
        }
        if (axisState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            openState = openState.setValue(BlockStateProperties.WATERLOGGED,
                    axisState.getValue(BlockStateProperties.WATERLOGGED));
        }
        return ModBlocks.LINKED_FLUID_PIPE.get().updateBlockState(openState, side, null, level, pos);
    }

    public static void switchVariant(Level level, BlockPos pos, BlockState newState,
                                     int bindingId, int factoryId, boolean internalEndpoint) {
        FluidTransportBehaviour.cacheFlows(level, pos);
        level.setBlockAndUpdate(pos, newState);
        LinkedPipeEndpoint linkedPipe = getLinkedPipe(level, pos);
        if (linkedPipe != null) {
            linkedPipe.setBinding(bindingId, factoryId, internalEndpoint);
        }
        FluidTransportBehaviour.loadFlows(level, pos);
    }

    public static void restoreOppositeEndpoint(Level level, BlockPos removedPos,
                                               @Nullable PocketFactorySavedData.BindingEndpoints endpoints) {
        if (endpoints == null || level.getServer() == null) {
            return;
        }

        String removedEndpointKey = BindingEndpointHelper.endpointKey(level, removedPos);
        String oppositeEndpointKey = BindingEndpointHelper.resolveOppositeEndpointKey(endpoints, removedEndpointKey);
        if (oppositeEndpointKey == null) {
            return;
        }

        BindingEndpointHelper.EndpointLocation endpointLocation = BindingEndpointHelper.parseEndpointKey(oppositeEndpointKey);
        if (endpointLocation == null) {
            return;
        }

        Level targetLevel = level.getServer().getLevel(endpointLocation.dimension());
        if (targetLevel == null) {
            return;
        }

        BlockState oppositeState = targetLevel.getBlockState(endpointLocation.pos());
        if (!isLinkedPipe(oppositeState)) {
            return;
        }

        targetLevel.setBlock(endpointLocation.pos(), toVanillaState(oppositeState), Block.UPDATE_ALL_IMMEDIATE);
    }
}
