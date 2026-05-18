package com.modmake.createpocketfactory.block.entity;

import javax.annotation.Nullable;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class LinkedPipeTransportHelper {
    public enum BridgeMode {
        PIPE,
        SINK,
        SOURCE
    }

    private static final int PIPE_BUFFER_CAPACITY = 4000;
    private static final int MIN_SOURCE_PRESSURE = 16;
    private static final float MIN_ACTIVE_PRESSURE = 0.001f;

    private LinkedPipeTransportHelper() {
    }

    public static void preTick(ServerLevel level, BlockPos pos, BlockState state, LinkedPipeEndpoint endpoint) {
        if (!endpoint.hasBinding() || level.getServer() == null) {
            endpoint.updateBridgeState(BridgeMode.PIPE, null, 0);
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        String endpointKey = BindingEndpointHelper.endpointKey(level, pos);
        if (!savedData.isEndpointBoundToBinding(endpoint.getBindingId(), PocketFactorySavedData.BindingChannel.LINKED_PIPE, endpointKey)) {
            level.setBlock(pos, LinkedPipeBindingHelper.toVanillaState(state), Block.UPDATE_ALL_IMMEDIATE);
            return;
        }

        PocketFactorySavedData.BindingEndpoints bindingEndpoints = savedData.getBindingEndpoints(endpoint.getBindingId(), PocketFactorySavedData.BindingChannel.LINKED_PIPE);
        if (bindingEndpoints == null) {
            endpoint.updateBridgeState(BridgeMode.PIPE, null, 0);
            return;
        }

        String oppositeEndpointKey = BindingEndpointHelper.resolveOppositeEndpointKey(bindingEndpoints, endpointKey);
        if (oppositeEndpointKey == null) {
            endpoint.updateBridgeState(BridgeMode.PIPE, null, 0);
            return;
        }

        LinkedPipeEndpoint oppositeEndpoint = resolveEndpoint(level, oppositeEndpointKey);
        EndpointPressure localInput = getStrongestInput(level, pos, state);
        EndpointPressure oppositeInput = getStrongestInput(oppositeEndpoint);
        FluidStack buffered = savedData.peekPipeBridgeFluid(endpoint.getBindingId());
        String sourceEndpointKey = savedData.getPipeBridgeSourceEndpointKey(endpoint.getBindingId());
        boolean hasBufferedFluid = !buffered.isEmpty();
        boolean bridgeHasRoom = savedData.getPipeBridgeRemainingCapacity(endpoint.getBindingId(), PIPE_BUFFER_CAPACITY) > 0;

        boolean localWinsSinkSelection = localInput.face() != null
            && localInput.pressure() > MIN_ACTIVE_PRESSURE
            && (oppositeInput.pressure() <= MIN_ACTIVE_PRESSURE
            || localInput.pressure() > oppositeInput.pressure()
            || (Float.compare(localInput.pressure(), oppositeInput.pressure()) == 0 && endpointKey.compareTo(oppositeEndpointKey) <= 0));

        BridgeMode nextMode = BridgeMode.PIPE;
        Direction inputFace = null;
        float mirroredPressure = 0;

        if (bridgeHasRoom && localWinsSinkSelection && (sourceEndpointKey == null || sourceEndpointKey.equals(endpointKey) || !hasBufferedFluid)) {
            nextMode = BridgeMode.SINK;
            inputFace = localInput.face();
        } else if (hasBufferedFluid && sourceEndpointKey != null && !sourceEndpointKey.equals(endpointKey)) {
            nextMode = BridgeMode.SOURCE;
            mirroredPressure = Math.max(oppositeInput.pressure(), MIN_SOURCE_PRESSURE);
        }

        endpoint.updateBridgeState(nextMode, inputFace, mirroredPressure);
        if (nextMode == BridgeMode.SOURCE && mirroredPressure > MIN_ACTIVE_PRESSURE) {
            distributeSourcePressure(level, pos, state, mirroredPressure);
        }
    }

    public static void postTick(ServerLevel level, BlockPos pos, BlockState state, LinkedPipeEndpoint endpoint) {
    }

    private static void distributeSourcePressure(ServerLevel level, BlockPos sourcePos, BlockState sourceState, float pressure) {
        for (Direction sourceSide : Iterate.directions) {
            if (!canPhysicallyConnect(sourceState, sourceSide)) {
                continue;
            }

            BlockFace start = new BlockFace(sourcePos, sourceSide);
            if (hasReachedValidEndpoint(level, start, false)) {
                BlockPos targetPos = start.getConnectedPos();
                FluidTransportBehaviour targetPipe = FluidPropagator.getPipe(level, targetPos);
                if (targetPipe != null) {
                    targetPipe.addPressure(sourceSide.getOpposite(), true, pressure);
                }
                continue;
            }

            Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();
            Set<BlockFace> targets = new HashSet<>();
            List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
            Set<BlockPos> visited = new HashSet<>();

            pipeGraph.computeIfAbsent(sourcePos, $ -> Pair.of(0, new IdentityHashMap<>()))
                    .getSecond()
                    .put(sourceSide, false);
            pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>()))
                    .getSecond()
                    .put(sourceSide.getOpposite(), true);
            frontier.add(Pair.of(1, start.getConnectedPos()));

            while (!frontier.isEmpty()) {
                Pair<Integer, BlockPos> entry = frontier.remove(0);
                int distance = entry.getFirst();
                BlockPos currentPos = entry.getSecond();

                if (!level.isLoaded(currentPos) || !visited.add(currentPos)) {
                    continue;
                }

                BlockState currentState = level.getBlockState(currentPos);
                FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
                if (pipe == null) {
                    continue;
                }

                for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
                    BlockFace blockFace = new BlockFace(currentPos, face);
                    BlockPos connectedPos = blockFace.getConnectedPos();

                    if (!level.isLoaded(connectedPos) || blockFace.isEquivalent(start)) {
                        continue;
                    }

                    if (hasReachedValidEndpoint(level, blockFace, false)) {
                        pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                                .getSecond()
                                .put(face, false);
                        targets.add(blockFace);
                        continue;
                    }

                    FluidTransportBehaviour connectedPipe = FluidPropagator.getPipe(level, connectedPos);
                    if (connectedPipe == null || visited.contains(connectedPos)) {
                        continue;
                    }

                    pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                            .getSecond()
                            .put(face, false);
                    pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>()))
                            .getSecond()
                            .put(face.getOpposite(), true);
                    frontier.add(Pair.of(distance + 1, connectedPos));
                }
            }

            Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
            searchForEndpointRecursively(pipeGraph, targets, validFaces,
                    new BlockFace(start.getPos(), start.getOppositeFace()), false);

            for (Set<BlockFace> set : validFaces.values()) {
                int parallelBranches = Math.max(1, set.size() - 1);
                for (BlockFace face : set) {
                    BlockPos pipePos = face.getPos();
                    Direction pipeSide = face.getFace();

                    if (pipePos.equals(sourcePos)) {
                        continue;
                    }

                    Pair<Integer, Map<Direction, Boolean>> graphEntry = pipeGraph.get(pipePos);
                    if (graphEntry == null || !graphEntry.getSecond().containsKey(pipeSide)) {
                        continue;
                    }

                    boolean inbound = graphEntry.getSecond().get(pipeSide);
                    FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
                    if (pipeBehaviour == null) {
                        continue;
                    }

                    pipeBehaviour.addPressure(pipeSide, inbound, pressure / parallelBranches);
                }
            }
        }
    }

    private static boolean searchForEndpointRecursively(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
                                                        Set<BlockFace> targets,
                                                        Map<Integer, Set<BlockFace>> validFaces,
                                                        BlockFace currentFace,
                                                        boolean pull) {
        BlockPos currentPos = currentFace.getPos();
        if (!pipeGraph.containsKey(currentPos)) {
            return false;
        }

        Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
        int distance = pair.getFirst();
        boolean atLeastOneBranchSuccessful = false;

        for (Direction nextFacing : Iterate.directions) {
            if (nextFacing == currentFace.getFace()) {
                continue;
            }

            Map<Direction, Boolean> map = pair.getSecond();
            if (!map.containsKey(nextFacing)) {
                continue;
            }

            BlockFace localTarget = new BlockFace(currentPos, nextFacing);
            if (targets.contains(localTarget)) {
                validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
                atLeastOneBranchSuccessful = true;
                continue;
            }

            if (map.get(nextFacing) != pull) {
                continue;
            }

            if (!searchForEndpointRecursively(pipeGraph, targets, validFaces,
                    new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull)) {
                continue;
            }

            validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
            atLeastOneBranchSuccessful = true;
        }

        if (atLeastOneBranchSuccessful) {
            validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(currentFace);
        }

        return atLeastOneBranchSuccessful;
    }

    private static boolean hasReachedValidEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
        BlockPos connectedPos = blockFace.getConnectedPos();
        BlockState connectedState = world.getBlockState(connectedPos);
        BlockEntity blockEntity = world.getBlockEntity(connectedPos);
        Direction face = blockFace.getFace();

        if (PumpBlock.isPump(connectedState) && connectedState.getValue(PumpBlock.FACING).getAxis() == face.getAxis()
                && blockEntity instanceof PumpBlockEntity pumpBE) {
            boolean front = connectedState.getValue(PumpBlock.FACING) == blockFace.getOppositeFace();
            return pumpBE.isPullingOnSide(front) != pull;
        }

        FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
        if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace())) {
            return false;
        }

        if (blockEntity != null) {
            IFluidHandler capability = blockEntity.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    blockEntity.getBlockPos(),
                    face.getOpposite());
            if (capability != null) {
                return true;
            }
        }

        return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
    }

    public static @Nullable IFluidHandler createFluidHandler(LinkedPipeEndpoint endpoint, @Nullable Direction side) {
        BlockEntity blockEntity = (BlockEntity) endpoint;
        Level level = blockEntity.getLevel();
        if (level == null || !endpoint.hasBinding()) {
            return null;
        }
        if (endpoint.getBridgeMode() != BridgeMode.SINK && endpoint.getBridgeMode() != BridgeMode.SOURCE) {
            return null;
        }
        return new LinkedPipeFluidHandler(endpoint, side);
    }

    private static boolean canPhysicallyConnect(BlockState state, Direction direction) {
        if (state.hasProperty(AxisPipeBlock.AXIS)) {
            return state.getValue(AxisPipeBlock.AXIS) == direction.getAxis();
        }
        return state.hasProperty(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction))
                && state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction));
    }

    private static EndpointPressure getStrongestInput(@Nullable LinkedPipeEndpoint endpoint) {
        if (!(endpoint instanceof BlockEntity blockEntity) || blockEntity.getLevel() == null) {
            return EndpointPressure.NONE;
        }
        return getStrongestInput(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState());
    }

    private static EndpointPressure getStrongestInput(Level level, BlockPos pos, BlockState state) {
        Direction bestFace = null;
        float bestPressure = 0;

        for (Direction direction : Iterate.directions) {
            if (!canPhysicallyConnect(state, direction)) {
                continue;
            }

            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            FluidTransportBehaviour neighborBehaviour = FluidPropagator.getPipe(level, neighborPos);
            if (neighborBehaviour == null || !neighborBehaviour.canHaveFlowToward(neighborState, direction.getOpposite())) {
                continue;
            }

            PipeConnection connection = neighborBehaviour.getConnection(direction.getOpposite());
            if (connection == null) {
                continue;
            }

            float pressure = connection.getPressure().getSecond();
            if (pressure > bestPressure) {
                bestPressure = pressure;
                bestFace = direction;
            }
        }

        return bestFace == null ? EndpointPressure.NONE : new EndpointPressure(bestFace, bestPressure);
    }

    private static @Nullable LinkedPipeEndpoint resolveEndpoint(ServerLevel level, String endpointKey) {
        BindingEndpointHelper.EndpointLocation location = BindingEndpointHelper.parseEndpointKey(endpointKey);
        if (location == null || level.getServer() == null) {
            return null;
        }
        ServerLevel targetLevel = level.getServer().getLevel(location.dimension());
        if (targetLevel == null) {
            return null;
        }
        BlockEntity blockEntity = targetLevel.getBlockEntity(location.pos());
        return blockEntity instanceof LinkedPipeEndpoint endpoint ? endpoint : null;
    }

    private static final class LinkedPipeFluidHandler implements IFluidHandler {
        private final LinkedPipeEndpoint endpoint;
        private final @Nullable Direction queriedSide;

        private LinkedPipeFluidHandler(LinkedPipeEndpoint endpoint, @Nullable Direction queriedSide) {
            this.endpoint = endpoint;
            this.queriedSide = queriedSide;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank != 0) {
                return FluidStack.EMPTY;
            }
            PocketFactorySavedData savedData = getSavedData();
            return savedData == null ? FluidStack.EMPTY : savedData.peekPipeBridgeFluid(endpoint.getBindingId());
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? PIPE_BUFFER_CAPACITY : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || endpoint.getBridgeMode() != BridgeMode.SINK) {
                return 0;
            }
            Direction inputFace = endpoint.getBridgeInputFace();
            if (inputFace == null || (queriedSide != null && queriedSide != inputFace)) {
                return 0;
            }
            PocketFactorySavedData savedData = getSavedData();
            BlockEntity blockEntity = (BlockEntity) endpoint;
            if (savedData == null || blockEntity.getLevel() == null) {
                return 0;
            }
            String endpointKey = BindingEndpointHelper.endpointKey(blockEntity.getLevel(), blockEntity.getBlockPos());
            if (action.simulate()) {
                return savedData.getPipeBridgeAcceptedAmount(endpoint.getBindingId(), resource, PIPE_BUFFER_CAPACITY);
            }
            return savedData.fillPipeBridge(endpoint.getBindingId(), endpointKey, resource, PIPE_BUFFER_CAPACITY);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidStack contained = getFluidInTank(0);
            if (contained.isEmpty() || !FluidStack.isSameFluidSameComponents(contained, resource)) {
                return FluidStack.EMPTY;
            }
            return drain(Math.min(resource.getAmount(), contained.getAmount()), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0 || endpoint.getBridgeMode() != BridgeMode.SOURCE) {
                return FluidStack.EMPTY;
            }
            PocketFactorySavedData savedData = getSavedData();
            BlockEntity blockEntity = (BlockEntity) endpoint;
            if (savedData == null || blockEntity.getLevel() == null) {
                return FluidStack.EMPTY;
            }
            String endpointKey = BindingEndpointHelper.endpointKey(blockEntity.getLevel(), blockEntity.getBlockPos());
            if (action.simulate()) {
                FluidStack simulated = savedData.peekPipeBridgeFluid(endpoint.getBindingId());
                if (simulated.isEmpty()) {
                    return FluidStack.EMPTY;
                }
                simulated.setAmount(Math.min(maxDrain, simulated.getAmount()));
                return simulated;
            }
            return savedData.drainPipeBridgeFluid(endpoint.getBindingId(), endpointKey, maxDrain);
        }

        private @Nullable PocketFactorySavedData getSavedData() {
            BlockEntity blockEntity = (BlockEntity) endpoint;
            if (blockEntity.getLevel() == null || blockEntity.getLevel().getServer() == null) {
                return null;
            }
            return PocketFactorySavedData.get(blockEntity.getLevel().getServer());
        }
    }

    private record EndpointPressure(@Nullable Direction face, float pressure) {
        private static final EndpointPressure NONE = new EndpointPressure(null, 0);
    }
}
