package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class LinkedFluidPipeBlockEntity extends FluidPipeBlockEntity implements IHaveGoggleInformation, LinkedPipeEndpoint {
    private static final String BINDING_ID_TAG = "BindingId";
    private static final String FACTORY_ID_TAG = "FactoryId";
    private static final String INTERNAL_ENDPOINT_TAG = "InternalEndpoint";

    private int bindingId = -1;
    private int factoryId = -1;
    private boolean internalEndpoint;
    private LinkedPipeTransportHelper.BridgeMode bridgeMode = LinkedPipeTransportHelper.BridgeMode.PIPE;
    private @Nullable Direction bridgeInputFace;
    private float mirroredBridgePressure;

    public LinkedFluidPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.LINKED_FLUID_PIPE.get(),
                (be, context) -> be.getFluidHandler(context)
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.LINKED_ENCASED_FLUID_PIPE.get(),
                (be, context) -> be.getFluidHandler(context)
        );
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new LinkedPipeFluidTransportBehaviour(this));
        behaviours.add(new BracketedBlockEntityBehaviour(this, this::canHaveBracket));
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide && level.getServer() != null && hasBinding()) {
            LinkedPipeTransportHelper.preTick((net.minecraft.server.level.ServerLevel) level, worldPosition, getBlockState(), this);
        }
        super.tick();
        if (level != null && !level.isClientSide && level.getServer() != null && hasBinding()) {
            LinkedPipeTransportHelper.postTick((net.minecraft.server.level.ServerLevel) level, worldPosition, getBlockState(), this);
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
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

    @Override
    public LinkedPipeTransportHelper.BridgeMode getBridgeMode() {
        return bridgeMode;
    }

    @Override
    public @Nullable Direction getBridgeInputFace() {
        return bridgeInputFace;
    }

    @Override
    public float getMirroredBridgePressure() {
        return mirroredBridgePressure;
    }

    @Override
    public void updateBridgeState(LinkedPipeTransportHelper.BridgeMode mode, @Nullable Direction inputFace, float mirroredPressure) {
        boolean topologyChanged = bridgeMode != mode || bridgeInputFace != inputFace;
        boolean pressureChanged = Float.compare(mirroredBridgePressure, mirroredPressure) != 0;
        bridgeMode = mode;
        bridgeInputFace = inputFace;
        mirroredBridgePressure = mirroredPressure;
        if (!topologyChanged && !pressureChanged) {
            return;
        }
        setChanged();
        if (level != null && !level.isClientSide && topologyChanged) {
            FluidPropagator.propagateChangedPipe(level, worldPosition, getBlockState());
            notifyUpdate();
        } else {
            sendData();
        }
    }

    @Override
    public FluidTransportBehaviour getTransportBehaviour() {
        return getBehaviour(FluidTransportBehaviour.TYPE);
    }

    public @Nullable IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (!hasBinding()) {
            return null;
        }
        return LinkedPipeTransportHelper.createFluidHandler(this, side);
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

    private boolean canHaveBracket(BlockState state) {
        return !(state.getBlock() instanceof EncasedPipeBlock);
    }

    private boolean canUseNativePipeFlow() {
        return bridgeMode == LinkedPipeTransportHelper.BridgeMode.PIPE;
    }

    private static class LinkedPipeFluidTransportBehaviour extends FluidTransportBehaviour {
        private final LinkedFluidPipeBlockEntity linkedPipe;

        public LinkedPipeFluidTransportBehaviour(LinkedFluidPipeBlockEntity blockEntity) {
            super(blockEntity);
            this.linkedPipe = blockEntity;
        }

        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return linkedPipe.canUseNativePipeFlow()
                    && (FluidPipeBlock.isPipe(state) || state.getBlock() instanceof EncasedPipeBlock)
                    && state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction));
        }

        @Override
        public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                                        Direction direction) {
            AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
            BlockState otherState = world.getBlockState(pos.relative(direction));

            if (state.getBlock() instanceof EncasedPipeBlock && attachment != AttachmentTypes.DRAIN) {
                return AttachmentTypes.NONE;
            }

            if (attachment == AttachmentTypes.RIM) {
                if (!FluidPipeBlock.isPipe(otherState) && !(otherState.getBlock() instanceof EncasedPipeBlock)
                        && !(otherState.getBlock() instanceof GlassFluidPipeBlock)) {
                    FluidTransportBehaviour pipeBehaviour =
                            BlockEntityBehaviour.get(world, pos.relative(direction), FluidTransportBehaviour.TYPE);
                    if (pipeBehaviour != null && pipeBehaviour.canHaveFlowToward(otherState, direction.getOpposite())) {
                        return AttachmentTypes.DETAILED_CONNECTION;
                    }
                }

                if (!FluidPipeBlock.shouldDrawRim(world, pos, state, direction)) {
                    return FluidPropagator.getStraightPipeAxis(state) == direction.getAxis()
                            ? AttachmentTypes.CONNECTION
                            : AttachmentTypes.DETAILED_CONNECTION;
                }
            }

            if (attachment == AttachmentTypes.NONE
                    && state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                return AttachmentTypes.DETAILED_CONNECTION;
            }

            return attachment;
        }
    }
}
