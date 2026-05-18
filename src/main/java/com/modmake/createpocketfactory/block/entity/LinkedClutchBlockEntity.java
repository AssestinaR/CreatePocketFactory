package com.modmake.createpocketfactory.block.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class LinkedClutchBlockEntity extends ClutchBlockEntity implements IHaveGoggleInformation, LinkedClutchEndpoint {
    private static final String BINDING_ID_TAG = "BindingId";
    private static final String FACTORY_ID_TAG = "FactoryId";
    private static final String INTERNAL_ENDPOINT_TAG = "InternalEndpoint";
    private static final String CROSS_MODE_TAG = "CrossMode";
    private static final String OPERATING_MODE_TAG = "OperatingMode";
    private static final String BRIDGED_INPUT_FACE_TAG = "BridgedInputFace";
    private static final String BRIDGED_AVAILABLE_STRESS_TAG = "BridgedAvailableStress";
    private static final long BRIDGED_NETWORK_MASK = 0x4000_0000_0000_0000L;

    private int bindingId = -1;
    private int factoryId = -1;
    private boolean internalEndpoint;
    private boolean crossMode;
    private OperatingMode operatingMode = OperatingMode.LOCAL_PASSIVE;
    private float bridgedGeneratedSpeed;
    private float bridgedAvailableStress;
    private @Nullable Direction bridgedInputFace;
    private int pendingBootstrapTicks;

    public LinkedClutchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
        compound.putBoolean(CROSS_MODE_TAG, crossMode);
        compound.putString(OPERATING_MODE_TAG, operatingMode.name());
        compound.putFloat(BRIDGED_AVAILABLE_STRESS_TAG, bridgedAvailableStress);
        if (bridgedInputFace != null) {
            compound.putInt(BRIDGED_INPUT_FACE_TAG, bridgedInputFace.ordinal());
        }
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        bindingId = compound.contains(BINDING_ID_TAG) ? compound.getInt(BINDING_ID_TAG) : -1;
        factoryId = compound.contains(FACTORY_ID_TAG) ? compound.getInt(FACTORY_ID_TAG) : -1;
        internalEndpoint = compound.getBoolean(INTERNAL_ENDPOINT_TAG);
        crossMode = compound.getBoolean(CROSS_MODE_TAG);
        operatingMode = readOperatingMode(compound.getString(OPERATING_MODE_TAG));
        bridgedAvailableStress = compound.getFloat(BRIDGED_AVAILABLE_STRESS_TAG);
        bridgedInputFace = compound.contains(BRIDGED_INPUT_FACE_TAG)
            ? Direction.from3DDataValue(compound.getInt(BRIDGED_INPUT_FACE_TAG))
            : null;
        super.read(compound, registries, clientPacket);
        pendingBootstrapTicks = hasBinding() ? 2 : 0;
    }

    @Override
    public void initialize() {
        if (pendingBootstrapTicks > 0) {
            resetLoadedKinetics();
        }
        super.initialize();
    }

    @Override
    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            super.tick();
            return;
        }

        if (pendingBootstrapTicks > 0) {
            pendingBootstrapTicks--;
            if (pendingBootstrapTicks > 0) {
                updateSpeed = false;
                super.tick();
                return;
            }

            updateSpeed = true;
        }

        if (hasBinding() && needsSpeedUpdate()) {
            reconcileBridgeState(serverLevel);
        }

        super.tick();
        reconcileBridgeState(serverLevel);
    }

    private void reconcileBridgeState(ServerLevel serverLevel) {
        if (!hasBinding()) {
            return;
        }

        OperatingMode previousMode = operatingMode;
        float previousBridgedSpeed = bridgedGeneratedSpeed;
        float previousBridgedAvailableStress = bridgedAvailableStress;
        Direction previousBridgedInputFace = bridgedInputFace;
        boolean previousCrossMode = crossMode;

        refreshBridgeMode(serverLevel);

        boolean modeChanged = previousMode != operatingMode;
        boolean bridgeChanged = Float.compare(previousBridgedSpeed, bridgedGeneratedSpeed) != 0
            || Float.compare(previousBridgedAvailableStress, bridgedAvailableStress) != 0
            || previousBridgedInputFace != bridgedInputFace;

        if (previousCrossMode != crossMode || modeChanged || bridgeChanged) {
            updateGeneratedBridgeRotation();

            if (modeChanged && source != null) {
                detachKinetics();
                attachKinetics();
                sendData();
            }
        }
    }

    @Override
    public void remove() {
        clearBridgeRegistration();
        super.remove();
    }

    @Override
    public float getGeneratedSpeed() {
        return isGeneratedBridgeMode() ? bridgedGeneratedSpeed : 0;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!isGeneratedBridgeMode()) {
            return super.calculateAddedStressCapacity();
        }

        float generatedSpeed = Math.abs(getGeneratedSpeed());
        if (generatedSpeed == 0) {
            return 0;
        }

        float capacity = bridgedAvailableStress / generatedSpeed;
        lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    public Direction getSourceFacing() {
        if (source != null) {
            return super.getSourceFacing();
        }
        if (isGeneratedBridgeMode() && bridgedInputFace != null) {
            return bridgedInputFace;
        }
        return Direction.fromAxisAndDirection(getBlockState().getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (isGeneratedBridgeMode() && bridgedInputFace != null) {
            return face == bridgedInputFace ? 0 : 1;
        }

        if (operatingMode == OperatingMode.CROSS_SOURCE && source != null) {
            Direction sourceFacing = super.getSourceFacing();
            return face == sourceFacing ? 1 : 0;
        }

        return 1;
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
    public @Nullable DriveInput describeLocalDriveInput() {
        if (level == null) {
            return null;
        }

        Direction.Axis axis = getBlockState().getValue(BlockStateProperties.AXIS);
        DriveInput selectedInput = null;

        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != axis) {
                continue;
            }

            DriveInput candidate = sampleDriveInput(direction);
            if (candidate == null) {
                continue;
            }

            if (selectedInput != null && selectedInput.axisDirection() != candidate.axisDirection()) {
                return null;
            }

            selectedInput = candidate;
        }

        return selectedInput;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.translatable("goggles.create_pocket_factory.common.binding")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.translatable(hasBinding()
                    ? "goggles.create_pocket_factory.common.bound"
                    : "goggles.create_pocket_factory.common.unbound")
                .withStyle(hasBinding() ? ChatFormatting.GREEN : ChatFormatting.RED)));

        if (hasBinding()) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.binding_id", bindingId)
                .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.factory_id", factoryId)
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable(crossMode
                ? "goggles.create_pocket_factory.linked_clutch.cross_mode"
                : "goggles.create_pocket_factory.linked_clutch.local_mode")
            .withStyle(crossMode ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        return true;
    }

    private void refreshBridgeMode(ServerLevel serverLevel) {
        if (!hasBinding() || serverLevel.getServer() == null) {
            clearBridgeState();
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(serverLevel.getServer());
        DriveInput localPhysicalInput = describeLocalDriveInput();
        PocketFactorySavedData.ClutchDriveState localDriveState = toBridgeDriveState(localPhysicalInput);
        PocketFactorySavedData.ClutchBridgeSnapshot snapshot = savedData.updateClutchEndpointState(
            bindingId,
            localRole(),
            getBlockState().getValue(BlockStateProperties.POWERED),
            localDriveState);

        PocketFactorySavedData.ClutchEndpointSnapshot localSnapshot = getSnapshotForRole(snapshot, localRole());
        PocketFactorySavedData.ClutchEndpointSnapshot remoteSnapshot = getSnapshotForRole(snapshot, oppositeRole());
        if (localSnapshot == null || remoteSnapshot == null) {
            clearBridgeState();
            return;
        }

        boolean localPowered = localSnapshot.powered();
        boolean remotePowered = remoteSnapshot.powered();
        PocketFactorySavedData.ClutchDriveState remoteBridgeDrive = remoteSnapshot.driveState();
        DriveInput localInput = fromBridgeDriveState(localSnapshot.driveState());
        DriveInput remoteInput = fromBridgeDriveState(remoteSnapshot.driveState());

        if (localPowered == remotePowered) {
            crossMode = true;

            if (localInput != null && remoteInput == null) {
                operatingMode = OperatingMode.CROSS_SOURCE;
                bridgedGeneratedSpeed = 0;
                bridgedAvailableStress = 0;
                bridgedInputFace = null;
                return;
            }

            if (localInput == null && remoteInput != null) {
                operatingMode = OperatingMode.CROSS_REMOTE;
                bridgedGeneratedSpeed = remoteInput.speed();
                bridgedAvailableStress = remoteBridgeDrive == null ? 0 : remoteBridgeDrive.availableStress();
                bridgedInputFace = Direction.fromAxisAndDirection(
                    getBlockState().getValue(BlockStateProperties.AXIS),
                    remoteInput.axisDirection());
                return;
            }

            if (localInput == null && remoteInput == null) {
                operatingMode = OperatingMode.CROSS_IDLE;
                bridgedGeneratedSpeed = 0;
                bridgedAvailableStress = 0;
                bridgedInputFace = null;
                return;
            }

            operatingMode = OperatingMode.CROSS_BIDIRECTIONAL;
            bridgedGeneratedSpeed = remoteInput.speed();
            bridgedAvailableStress = remoteBridgeDrive == null ? 0 : remoteBridgeDrive.availableStress();
            bridgedInputFace = Direction.fromAxisAndDirection(
                getBlockState().getValue(BlockStateProperties.AXIS),
                remoteInput.axisDirection());
            return;
        }

        crossMode = false;
        bridgedGeneratedSpeed = 0;
        bridgedAvailableStress = 0;
        bridgedInputFace = null;
        operatingMode = localPhysicalInput != null ? OperatingMode.LOCAL_DIRECT : OperatingMode.LOCAL_PASSIVE;
    }

    private void clearBridgeState() {
        crossMode = false;
        operatingMode = getLocalOperatingMode();
        bridgedGeneratedSpeed = 0;
        bridgedAvailableStress = 0;
        bridgedInputFace = null;
    }

    private void updateGeneratedBridgeRotation() {
        if (level == null || level.isClientSide) {
            return;
        }

        float previousSpeed = this.speed;
        float generatedSpeed = getGeneratedSpeed();

        if (isGeneratedBridgeMode() && source != null) {
            detachKinetics();
            removeSource();
            previousSpeed = this.speed;
        }

        if (!isGeneratedBridgeMode()) {
            if (source == null && previousSpeed != 0) {
                applyNewBridgedSpeed(previousSpeed, 0);
                updateSpeed = true;
                onSpeedChanged(previousSpeed);
                sendData();
            }
            return;
        }

        if (previousSpeed == generatedSpeed) {
            syncGeneratedBridgeStress();
            sendData();
            return;
        }

        applyNewBridgedSpeed(previousSpeed, generatedSpeed);
        syncGeneratedBridgeStress();
        onSpeedChanged(previousSpeed);
        sendData();
    }

    private void applyNewBridgedSpeed(float previousSpeed, float nextSpeed) {
        if (nextSpeed == 0) {
            detachKinetics();
            setSpeed(0);
            setNetwork(null);
            return;
        }

        if (previousSpeed == 0) {
            setSpeed(nextSpeed);
            setNetwork(createBridgedNetworkId());
            attachKinetics();
            return;
        }

        detachKinetics();
        setSpeed(nextSpeed);
        setNetwork(createBridgedNetworkId());
        attachKinetics();
    }

    private Long createBridgedNetworkId() {
        return worldPosition.asLong() ^ BRIDGED_NETWORK_MASK;
    }

    private void resetLoadedKinetics() {
        clearKineticInformation();
        crossMode = false;
        operatingMode = OperatingMode.LOCAL_PASSIVE;
        bridgedGeneratedSpeed = 0;
        bridgedAvailableStress = 0;
        bridgedInputFace = null;
        networkDirty = false;
        updateSpeed = true;
    }

    private void syncGeneratedBridgeStress() {
        if (!isGeneratedBridgeMode() || !hasNetwork() || getGeneratedSpeed() == 0) {
            return;
        }

        getOrCreateNetwork().updateCapacityFor(this, calculateAddedStressCapacity());
        getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        getOrCreateNetwork().updateStress();
    }

    private boolean isGeneratedBridgeMode() {
        return operatingMode == OperatingMode.CROSS_REMOTE || operatingMode == OperatingMode.CROSS_BIDIRECTIONAL;
    }

    private OperatingMode getLocalOperatingMode() {
        return describeLocalDriveInput() != null ? OperatingMode.LOCAL_DIRECT : OperatingMode.LOCAL_PASSIVE;
    }

    private PocketFactorySavedData.EndpointRole localRole() {
        return internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL;
    }

    private PocketFactorySavedData.EndpointRole oppositeRole() {
        return internalEndpoint ? PocketFactorySavedData.EndpointRole.EXTERNAL : PocketFactorySavedData.EndpointRole.INTERNAL;
    }

    private @Nullable PocketFactorySavedData.ClutchEndpointSnapshot getSnapshotForRole(
        PocketFactorySavedData.ClutchBridgeSnapshot snapshot,
        PocketFactorySavedData.EndpointRole role) {
        return role == PocketFactorySavedData.EndpointRole.INTERNAL ? snapshot.internalEndpoint() : snapshot.externalEndpoint();
    }

    private @Nullable PocketFactorySavedData.ClutchDriveState toBridgeDriveState(@Nullable DriveInput driveInput) {
        if (driveInput == null) {
            return null;
        }
        return new PocketFactorySavedData.ClutchDriveState(
            driveInput.speed(),
            driveInput.axisDirection(),
            driveInput.availableStress());
    }

    private @Nullable DriveInput fromBridgeDriveState(@Nullable PocketFactorySavedData.ClutchDriveState driveState) {
        if (driveState == null) {
            return null;
        }
        return new DriveInput(driveState.speed(), driveState.axisDirection(), driveState.availableStress());
    }

    private @Nullable DriveInput sampleDriveInput(Direction direction) {
        if (level == null) {
            return null;
        }

        BlockPos neighborPos = worldPosition.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (!(neighborState.getBlock() instanceof IRotate rotate)
            || !rotate.hasShaftTowards(level, neighborPos, neighborState, direction.getOpposite())) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(neighborPos);
        if (!(blockEntity instanceof KineticBlockEntity neighborKinetic)) {
            return null;
        }

        float neighborSpeed = neighborKinetic.getTheoreticalSpeed();
        if (neighborSpeed == 0) {
            return null;
        }

        if (neighborKinetic.hasSource() && worldPosition.equals(neighborKinetic.source)) {
            return null;
        }

        if (!neighborKinetic.hasSource() && !neighborKinetic.isSource()) {
            return null;
        }

        return new DriveInput(
            neighborSpeed,
            direction.getAxisDirection(),
            getAvailableStress(neighborKinetic));
    }

    private float getAvailableStress(KineticBlockEntity input) {
        if (!input.hasNetwork()) {
            return 0;
        }

        return Math.max(0, input.getOrCreateNetwork().calculateCapacity() - input.getOrCreateNetwork().calculateStress());
    }

    private void clearBridgeRegistration() {
        if (!(level instanceof ServerLevel serverLevel) || !hasBinding() || serverLevel.getServer() == null) {
            return;
        }
        PocketFactorySavedData.get(serverLevel.getServer()).clearClutchEndpointState(bindingId, localRole());
    }

    private OperatingMode readOperatingMode(String name) {
        try {
            return OperatingMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return OperatingMode.LOCAL_PASSIVE;
        }
    }

    private enum OperatingMode {
        LOCAL_PASSIVE,
        LOCAL_DIRECT,
        CROSS_SOURCE,
        CROSS_BIDIRECTIONAL,
        CROSS_REMOTE,
        CROSS_IDLE
    }
}