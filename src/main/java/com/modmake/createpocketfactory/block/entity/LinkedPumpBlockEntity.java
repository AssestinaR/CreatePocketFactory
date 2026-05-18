package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.CreatePocketFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlock;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class LinkedPumpBlockEntity extends PumpBlockEntity implements IHaveGoggleInformation, LinkedPumpEndpoint {
    private static final String BINDING_ID_TAG = "BindingId";
    private static final String FACTORY_ID_TAG = "FactoryId";
    private static final String INTERNAL_ENDPOINT_TAG = "InternalEndpoint";
    private static final int MIN_TRANSFER_PER_TICK = 25;
    private static final int MAX_TRANSFER_PER_TICK = 1000;
    private static final int BRIDGE_BUFFER_CAPACITY = 4000;

    private int bindingId = -1;
    private int factoryId = -1;
    private boolean internalEndpoint;
    private int debugLogCooldown;

    public LinkedPumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Linked pumps should keep behaving like Create pumps, not fluid handler endpoints.
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.removeIf(behaviour -> behaviour instanceof FluidTransportBehaviour);
        behaviours.add(new LinkedPumpFluidTransferBehaviour(this));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (bindingId > 0) {
            compound.putInt(BINDING_ID_TAG, bindingId);
        }
        if (factoryId > 0) {
            compound.putInt(FACTORY_ID_TAG, factoryId);
        }
        compound.putBoolean(INTERNAL_ENDPOINT_TAG, internalEndpoint);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        bindingId = compound.contains(BINDING_ID_TAG) ? compound.getInt(BINDING_ID_TAG) : -1;
        factoryId = compound.contains(FACTORY_ID_TAG) ? compound.getInt(FACTORY_ID_TAG) : -1;
        internalEndpoint = compound.getBoolean(INTERNAL_ENDPOINT_TAG);
        super.read(compound, registries, clientPacket);
    }

    @Override
    public void tick() {
        super.tick();

        if (!(level instanceof ServerLevel serverLevel) || !hasBinding() || getSpeed() == 0) {
            return;
        }

        bridgeLinkedTransfer(serverLevel);
    }

    @Override
    public int getBindingId() {
        return bindingId;
    }

    @Override
    public int getFactoryId() {
        return factoryId;
    }

    @Override
    public boolean hasBinding() {
        return bindingId > 0;
    }

    @Override
    public boolean isInternalEndpoint() {
        return internalEndpoint;
    }

    @Override
    public void setBinding(int bindingId, int factoryId, boolean internalEndpoint) {
        boolean changed = this.bindingId != bindingId || this.factoryId != factoryId || this.internalEndpoint != internalEndpoint;
        this.bindingId = bindingId;
        this.factoryId = factoryId;
        this.internalEndpoint = internalEndpoint;
        if (!changed) {
            return;
        }
        setChanged();
        sendData();
    }

    private FluidStack getRemoteProvidedFluid() {
        RemotePumpContext remoteContext = resolveRemotePumpContext().context();
        if (remoteContext == null) {
            return FluidStack.EMPTY;
        }

        if (remoteContext.level().getBlockEntity(remoteContext.pos()) instanceof LinkedPumpBlockEntity remotePump) {
            FluidTransportBehaviour remoteTransport = remotePump.getBehaviour(FluidTransportBehaviour.TYPE);
            if (remoteTransport != null) {
                PipeConnection remotePullConnection = remoteTransport.getConnection(remoteContext.pullSide());
                if (remotePullConnection != null) {
                    FluidStack provided = remotePullConnection.getProvidedFluid();
                    if (!provided.isEmpty()) {
                        return provided.copy();
                    }
                }
            }
        }

        IFluidHandler remoteSource = remoteContext.level().getCapability(
                Capabilities.FluidHandler.BLOCK,
                remoteContext.pos().relative(remoteContext.pullSide()),
                remoteContext.pullSide().getOpposite()
        );
        if (remoteSource == null) {
            return FluidStack.EMPTY;
        }
        return remoteSource.drain(1, IFluidHandler.FluidAction.SIMULATE);
    }

    private void bridgeLinkedTransfer(ServerLevel serverLevel) {
        RemotePumpContext remoteContext = resolveRemotePumpContext().context();
        Direction localPullSide = getPullSide();
        Direction localPushSide = getPushSide();
        if (remoteContext == null || localPullSide == null || localPushSide == null || serverLevel.getServer() == null) {
            emitDebugLog("bridge_missing_context", remoteContext, false, false);
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(serverLevel.getServer());
        String localEndpointKey = BindingEndpointHelper.endpointKey(serverLevel, worldPosition);
        FluidStack buffered = savedData.peekPipeBridgeFluid(bindingId);
        String sourceEndpointKey = savedData.getPipeBridgeSourceEndpointKey(bindingId);

        List<ResolvedFluidEndpoint> localSources = collectTransferEndpoints(serverLevel, worldPosition, localPullSide);
        List<ResolvedFluidEndpoint> localTargets = collectTransferEndpoints(serverLevel, worldPosition, localPushSide);

        boolean transferred = false;
        if (!buffered.isEmpty() && sourceEndpointKey != null && !sourceEndpointKey.equals(localEndpointKey) && !localTargets.isEmpty()) {
            FluidStack toInsert = buffered.copy();
            toInsert.setAmount(Math.min(toInsert.getAmount(), getMaxTransferPerTick()));
            int filled = executeFill(localTargets, toInsert);
            if (filled > 0) {
                savedData.drainPipeBridgeFluid(bindingId, localEndpointKey, filled);
                transferred = true;
                emitDebugLog("bridge_transfer", remoteContext, !localSources.isEmpty(), true);
            }
        }

        int remainingCapacity = savedData.getPipeBridgeRemainingCapacity(bindingId, BRIDGE_BUFFER_CAPACITY);
        boolean canSourceIntoBridge = remainingCapacity > 0
                && (sourceEndpointKey == null || sourceEndpointKey.equals(localEndpointKey) || buffered.isEmpty());

        if (canSourceIntoBridge && !localSources.isEmpty()) {
            int maxTransfer = Math.min(getMaxTransferPerTick(), remainingCapacity);
            for (ResolvedFluidEndpoint source : localSources) {
                FluidStack simulated = source.handler().drain(maxTransfer, FluidAction.SIMULATE);
                if (simulated.isEmpty()) {
                    continue;
                }

                int accepted = savedData.getPipeBridgeAcceptedAmount(bindingId, simulated, BRIDGE_BUFFER_CAPACITY);
                if (accepted <= 0) {
                    continue;
                }

                FluidStack requested = simulated.copy();
                requested.setAmount(Math.min(simulated.getAmount(), accepted));
                FluidStack drained = source.handler().drain(requested, FluidAction.EXECUTE);
                if (drained.isEmpty()) {
                    continue;
                }

                int stored = savedData.fillPipeBridge(bindingId, localEndpointKey, drained, BRIDGE_BUFFER_CAPACITY);
                if (stored > 0) {
                    transferred = true;
                    emitDebugLog("bridge_transfer", remoteContext, true, !localTargets.isEmpty());
                    break;
                }
            }
        }

        if (!transferred) {
            emitDebugLog("bridge_no_transfer", remoteContext, !localSources.isEmpty(), !localTargets.isEmpty());
        }
    }

    private int getMaxTransferPerTick() {
        int bySpeed = Math.max(MIN_TRANSFER_PER_TICK, Math.round(Math.abs(getSpeed())));
        return Math.min(MAX_TRANSFER_PER_TICK, bySpeed);
    }

    private int simulateFill(List<ResolvedFluidEndpoint> targets, FluidStack stack) {
        int accepted = 0;
        int remaining = stack.getAmount();
        for (ResolvedFluidEndpoint target : targets) {
            if (remaining <= 0) {
                break;
            }
            FluidStack attempt = stack.copy();
            attempt.setAmount(remaining);
            int filled = target.handler().fill(attempt, FluidAction.SIMULATE);
            if (filled <= 0) {
                continue;
            }
            accepted += filled;
            remaining -= filled;
        }
        return accepted;
    }

    private int executeFill(List<ResolvedFluidEndpoint> targets, FluidStack stack) {
        int filledTotal = 0;
        int remaining = stack.getAmount();
        for (ResolvedFluidEndpoint target : targets) {
            if (remaining <= 0) {
                break;
            }
            FluidStack attempt = stack.copy();
            attempt.setAmount(remaining);
            int filled = target.handler().fill(attempt, FluidAction.EXECUTE);
            if (filled <= 0) {
                continue;
            }
            filledTotal += filled;
            remaining -= filled;
        }
        return filledTotal;
    }

    private List<ResolvedFluidEndpoint> collectTransferEndpoints(ServerLevel level, BlockPos pumpPos, Direction side) {
        List<ResolvedFluidEndpoint> endpoints = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();

        collectEndpointAtFace(level, new BlockFace(pumpPos, side), endpoints);
        BlockPos adjacentPos = pumpPos.relative(side);
        BlockState adjacentState = level.getBlockState(adjacentPos);
        FluidTransportBehaviour adjacentPipe = FluidPropagator.getPipe(level, adjacentPos);
        if (adjacentPipe != null && adjacentPipe.canHaveFlowToward(adjacentState, side.getOpposite())) {
            frontier.add(adjacentPos);
        }

        while (!frontier.isEmpty()) {
            BlockPos currentPos = frontier.removeFirst();
            if (!visited.add(currentPos) || !level.isLoaded(currentPos)) {
                continue;
            }

            BlockState currentState = level.getBlockState(currentPos);
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
            if (pipe == null) {
                continue;
            }

            for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
                BlockFace blockFace = new BlockFace(currentPos, face);
                collectEndpointAtFace(level, blockFace, endpoints);

                BlockPos connectedPos = blockFace.getConnectedPos();
                if (visited.contains(connectedPos) || !level.isLoaded(connectedPos)) {
                    continue;
                }

                BlockState connectedState = level.getBlockState(connectedPos);
                FluidTransportBehaviour connectedPipe = FluidPropagator.getPipe(level, connectedPos);
                if (connectedPipe != null && connectedPipe.canHaveFlowToward(connectedState, face.getOpposite())) {
                    frontier.addLast(connectedPos);
                }
            }
        }

        return endpoints;
    }

    private void collectEndpointAtFace(ServerLevel level, BlockFace blockFace, List<ResolvedFluidEndpoint> endpoints) {
        BlockPos connectedPos = blockFace.getConnectedPos();
        Direction face = blockFace.getFace();
        BlockEntity blockEntity = level.getBlockEntity(connectedPos);
        if (blockEntity != null) {
            IFluidHandler capability = level.getCapability(Capabilities.FluidHandler.BLOCK, connectedPos, face.getOpposite());
            if (capability != null) {
                endpoints.add(new ResolvedFluidEndpoint(capability, connectedPos, "handler"));
                return;
            }
        }

        if (!FluidPropagator.isOpenEnd(level, blockFace.getPos(), face)) {
            return;
        }

        OpenEndedPipe openEndedPipe = new OpenEndedPipe(blockFace);
        openEndedPipe.manageSource(level, this);
        if (openEndedPipe.provideHandler() == null) {
            return;
        }

        IFluidHandler capability = openEndedPipe.provideHandler().getCapability();
        if (capability != null) {
            endpoints.add(new ResolvedFluidEndpoint(capability, connectedPos, "open_end"));
        }
    }

    private void emitDebugLog(String stage, @Nullable RemotePumpContext remoteContext, boolean hasLocalSource, boolean hasRemoteDestination) {
        if (level == null || level.isClientSide || --debugLogCooldown > 0) {
            return;
        }
        debugLogCooldown = 40;

        Direction front = getFront();
        FluidStack remoteProvided = getRemoteProvidedFluid();
        PipeConnection frontConnection = getBehaviour(FluidTransportBehaviour.TYPE) == null || front == null
                ? null
                : getBehaviour(FluidTransportBehaviour.TYPE).getConnection(front);
        boolean frontHasFlow = frontConnection != null && frontConnection.hasFlow();
        FluidStack outward = front == null || getBehaviour(FluidTransportBehaviour.TYPE) == null
                ? FluidStack.EMPTY
                : getBehaviour(FluidTransportBehaviour.TYPE).getProvidedOutwardFluid(front);

        CreatePocketFactory.LOGGER.info(
            "LinkedPump debug stage={} pos={} binding={} speed={} remoteCtx={} remoteFluid={} front={} frontHasFlow={} outward={} hasLocalSource={} hasRemoteDestination={}",
            stage,
                worldPosition,
                bindingId,
                getSpeed(),
                remoteContext != null,
                remoteProvided,
                front,
                frontHasFlow,
                outward,
            hasLocalSource,
                hasRemoteDestination
        );
    }

    private RemotePumpResolution resolveRemotePumpContext() {
        if (level == null || level.isClientSide || level.getServer() == null || !hasBinding()) {
            return RemotePumpResolution.failed("no_server_or_binding");
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        PocketFactorySavedData.BindingEndpoints endpoints = savedData.getBindingEndpoints(bindingId, PocketFactorySavedData.BindingChannel.LINKED_PUMP);
        if (endpoints == null) {
            return RemotePumpResolution.failed("missing_binding_record");
        }

        String localEndpointKey = BindingEndpointHelper.endpointKey(level, worldPosition);
        String oppositeEndpointKey = BindingEndpointHelper.resolveOppositeEndpointKey(endpoints, localEndpointKey);
        if (oppositeEndpointKey == null) {
            return RemotePumpResolution.failed("missing_opposite_endpoint");
        }

        BindingEndpointHelper.EndpointLocation endpointLocation = BindingEndpointHelper.parseEndpointKey(oppositeEndpointKey);
        if (endpointLocation == null) {
            return RemotePumpResolution.failed("invalid_opposite_endpoint");
        }

        ServerLevel remoteLevel = level.getServer().getLevel(endpointLocation.dimension());
        if (remoteLevel == null) {
            return RemotePumpResolution.failed("missing_remote_level");
        }

        if (!(remoteLevel.getBlockEntity(endpointLocation.pos()) instanceof LinkedPumpBlockEntity remotePump)
                || !remotePump.hasBinding()
                || remotePump.getBindingId() != bindingId) {
            return RemotePumpResolution.failed("remote_block_entity_mismatch");
        }

        Direction remotePullSide = remotePump.getPullSide();
        Direction remotePushSide = remotePump.getPushSide();
        if (remotePullSide == null || remotePushSide == null) {
            return RemotePumpResolution.failed("missing_remote_front");
        }

        return RemotePumpResolution.resolved(new RemotePumpContext(remoteLevel, endpointLocation.pos(), remotePullSide, remotePushSide));
    }

    public @Nullable Direction getPullSide() {
        Direction front = getFront();
        if (front == null) {
            return null;
        }
        return isPullingOnSide(true) ? front : front.getOpposite();
    }

    public @Nullable Direction getPushSide() {
        Direction pullSide = getPullSide();
        return pullSide == null ? null : pullSide.getOpposite();
    }

    public @Nullable RemoteSourceSpec resolveRemoteSourceSpec() {
        RemotePumpContext remoteContext = resolveRemotePumpContext().context();
        if (remoteContext == null) {
            return null;
        }
        return new RemoteSourceSpec(remoteContext.level(), remoteContext.pos(), remoteContext.pullSide(), remoteContext.pushSide());
    }

    private record RemotePumpContext(ServerLevel level, BlockPos pos, Direction pullSide, Direction pushSide) {
    }

    public record RemoteSourceSpec(ServerLevel level, BlockPos pos, Direction pullSide, Direction pushSide) {
    }

    private record ResolvedFluidEndpoint(IFluidHandler handler, BlockPos pos, String kind) {
    }

    private record RemotePumpResolution(@Nullable RemotePumpContext context, String stage) {
        private static RemotePumpResolution failed(String stage) {
            return new RemotePumpResolution(null, stage);
        }

        private static RemotePumpResolution resolved(RemotePumpContext context) {
            return new RemotePumpResolution(context, "resolved");
        }
    }

    private static final class LinkedPumpFluidTransferBehaviour extends FluidTransportBehaviour {
        private final LinkedPumpBlockEntity linkedPump;

        private LinkedPumpFluidTransferBehaviour(LinkedPumpBlockEntity blockEntity) {
            super(blockEntity);
            this.linkedPump = blockEntity;
        }

        private void ensureLinkedConnections() {
            Direction pullSide = linkedPump.getPullSide();
            if (interfaces == null || pullSide == null) {
                return;
            }
            PipeConnection connection = interfaces.get(pullSide);
            if (!(connection instanceof LinkedPumpSourceConnection)) {
                interfaces.put(pullSide, new LinkedPumpSourceConnection(linkedPump, pullSide));
            }
        }

        @Override
        public void tick() {
            Level world = getWorld();
            BlockPos pos = getPos();
            boolean onServer = !world.isClientSide || blockEntity.isVirtual();

            if (interfaces == null) {
                return;
            }
            ensureLinkedConnections();

            Collection<PipeConnection> connections = interfaces.values();
            PipeConnection singleSource = null;

            if (phase == UpdatePhase.WAIT_FOR_PUMPS) {
                phase = UpdatePhase.FLIP_FLOWS;
                return;
            }

            if (onServer) {
                boolean sendUpdate = false;
                for (PipeConnection connection : connections) {
                    sendUpdate |= connection.flipFlowsIfPressureReversed();
                    connection.manageSource(world, pos, blockEntity);
                }
                if (sendUpdate) {
                    blockEntity.notifyUpdate();
                }
            }

            if (phase == UpdatePhase.FLIP_FLOWS) {
                phase = UpdatePhase.IDLE;
                return;
            }

            for (Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
                boolean pull = linkedPump.isPullingOnSide(linkedPump.isFront(entry.getKey()));
                PipeConnection connection = entry.getValue();
                connection.getPressure().set(pull, Math.abs(linkedPump.getSpeed()));
                connection.getPressure().set(!pull, 0f);
            }

            if (onServer) {
                FluidStack availableFlow = FluidStack.EMPTY;
                FluidStack collidingFlow = FluidStack.EMPTY;
                Direction pullSide = linkedPump.hasBinding() ? linkedPump.getPullSide() : null;

                for (PipeConnection connection : connections) {
                    if (pullSide != null && connection.side != pullSide) {
                        continue;
                    }
                    FluidStack fluidInFlow = connection.getProvidedFluid();
                    if (fluidInFlow.isEmpty()) {
                        continue;
                    }
                    if (availableFlow.isEmpty()) {
                        singleSource = connection;
                        availableFlow = fluidInFlow;
                        continue;
                    }
                    if (FluidStack.isSameFluidSameComponents(availableFlow, fluidInFlow)) {
                        singleSource = null;
                        availableFlow = fluidInFlow;
                        continue;
                    }
                    collidingFlow = fluidInFlow;
                    break;
                }

                if (!collidingFlow.isEmpty()) {
                    com.simibubi.create.content.fluids.FluidReactions.handlePipeFlowCollision(world, pos, availableFlow, collidingFlow);
                    return;
                }

                boolean sendUpdate = false;
                for (PipeConnection connection : connections) {
                    FluidStack internalFluid = singleSource != connection ? availableFlow : FluidStack.EMPTY;
                    Predicate<FluidStack> extractionPredicate = extracted -> canPullFluidFrom(extracted, blockEntity.getBlockState(), connection.side);
                    sendUpdate |= connection.manageFlows(world, pos, internalFluid, extractionPredicate);
                }

                if (sendUpdate) {
                    blockEntity.notifyUpdate();
                }
            }

            for (PipeConnection connection : connections) {
                connection.tickFlowProgress(world, pos);
            }
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return linkedPump.isSideAccessible(direction);
        }

        @Override
        public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                                        Direction direction) {
            AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
            if (attachment == AttachmentTypes.RIM) {
                return AttachmentTypes.NONE;
            }
            return attachment;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(getBlockState().getBlock().getName().copy().withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("goggles.create_pocket_factory.common.binding")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(hasBinding()
                                ? "goggles.create_pocket_factory.common.bound"
                                : "goggles.create_pocket_factory.common.unbound")
                        .withStyle(hasBinding() ? ChatFormatting.GREEN : ChatFormatting.RED)));
        if (hasBinding()) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.binding_id", bindingId)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (factoryId > 0) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.factory_id", factoryId)
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(internalEndpoint ? "Endpoint: internal" : "Endpoint: external")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        return true;
    }
}
