package com.modmake.createpocketfactory.block.entity;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.modmake.createpocketfactory.world.LinkedStorageManualBindingHelper;

import java.util.List;

public class LinkedFluidTankBlockEntity extends FluidTankBlockEntity implements IHaveGoggleInformation {
    private static final String BINDING_ID_TAG = "LinkedBindingId";
    private static final String FACTORY_ID_TAG = "LinkedFactoryId";
    private static final String CHUNK_X_TAG = "LinkedChunkX";
    private static final String CHUNK_Z_TAG = "LinkedChunkZ";
    private static final String DISPLAY_CAPACITY_TAG = "LinkedDisplayCapacityMb";
    private static final int NO_CHUNK = Integer.MIN_VALUE;

    private int bindingId = -1;
    private int factoryId = -1;
    private int boundChunkX = NO_CHUNK;
    private int boundChunkZ = NO_CHUNK;
    private boolean sharedFluidMerged;
    private int lastSharedVersion = -1;
    private String lastPushedFluidKey = "";
    private int lastPushedAmount = -1;
    private int displayCapacityMb = getCapacityMultiplier();
    private boolean sharedTopologyDirty;

    public LinkedFluidTankBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.LINKED_FLUID_TANK.get(), pos, state);
    }

    public LinkedFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.LINKED_FLUID_TANK.get(),
                (be, context) -> be.fluidCapability
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !isController()) {
            return;
        }

        if (level.getServer() == null) {
            return;
        }

        if (bindingId <= 0) {
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        String endpointKey = LinkedStorageBindingHelper.endpointKey(level, worldPosition);
        if (!savedData.isEndpointBoundToBinding(bindingId, PocketFactorySavedData.BindingChannel.FLUID_STORAGE, endpointKey)) {
            LinkedStorageManualBindingHelper.normalizeOrphanedFluidTank(this);
            return;
        }

        int localCapacity = getTotalTankSize() * getCapacityMultiplier();
        if (sharedTopologyDirty) {
            PocketFactorySavedData.FluidStorageSnapshot snapshot = savedData.registerFluidEndpoint(bindingId, endpointKey, localCapacity);
            applySharedFluidSnapshot(snapshot);
            sharedTopologyDirty = false;
            setChanged();
            sendData();
            return;
        }

        if (!sharedFluidMerged) {
            PocketFactorySavedData.FluidStorageSnapshot merged = savedData.mergeFluidEndpointState(bindingId, endpointKey, tankInventory.getFluid().copy(), localCapacity);
            applySharedFluidSnapshot(merged);
            sharedFluidMerged = true;
            return;
        }

        PocketFactorySavedData.FluidStorageSnapshot snapshot = savedData.registerFluidEndpoint(bindingId, endpointKey, localCapacity);
        String currentFluidKey = tankInventory.getFluid().isEmpty() ? "" : tankInventory.getFluid().getFluid().builtInRegistryHolder().key().location().toString();
        int currentAmount = tankInventory.getFluidAmount();
        if (!currentFluidKey.equals(lastPushedFluidKey) || currentAmount != lastPushedAmount) {
            int updatedVersion = savedData.saveFluidSnapshot(bindingId, tankInventory.getFluid().copy());
            lastSharedVersion = updatedVersion;
            lastPushedFluidKey = currentFluidKey;
            lastPushedAmount = currentAmount;
            snapshot = savedData.getFluidSnapshot(bindingId);
        }

        if (snapshot.version() != lastSharedVersion || tankInventory.getCapacity() != snapshot.currentCapacityMb()) {
            applySharedFluidSnapshot(snapshot);
        }
    }

    public void refreshConnectivityNow() {
        updateConnectivity();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (bindingId > 0) {
            compound.putInt(BINDING_ID_TAG, bindingId);
        }
        if (factoryId > 0) {
            compound.putInt(FACTORY_ID_TAG, factoryId);
        }
        if (hasChunkBinding()) {
            compound.putInt(CHUNK_X_TAG, boundChunkX);
            compound.putInt(CHUNK_Z_TAG, boundChunkZ);
        }
        compound.putBoolean("SharedMerged", sharedFluidMerged);
        compound.putInt(DISPLAY_CAPACITY_TAG, displayCapacityMb);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        bindingId = compound.contains(BINDING_ID_TAG) ? compound.getInt(BINDING_ID_TAG) : -1;
        factoryId = compound.contains(FACTORY_ID_TAG) ? compound.getInt(FACTORY_ID_TAG) : -1;
        boundChunkX = compound.contains(CHUNK_X_TAG) ? compound.getInt(CHUNK_X_TAG) : NO_CHUNK;
        boundChunkZ = compound.contains(CHUNK_Z_TAG) ? compound.getInt(CHUNK_Z_TAG) : NO_CHUNK;
        sharedFluidMerged = compound.getBoolean("SharedMerged");
        super.read(compound, registries, clientPacket);
        displayCapacityMb = Math.max(getCapacityMultiplier(), compound.contains(DISPLAY_CAPACITY_TAG) ? compound.getInt(DISPLAY_CAPACITY_TAG) : getCapacityMultiplier());
        tankInventory.setCapacity(displayCapacityMb);
        refreshDisplayFluidLevel();
    }

    public boolean hasBinding() {
        return bindingId > 0;
    }

    public int getBindingId() {
        return bindingId;
    }

    public boolean hasFactoryId() {
        return factoryId > 0;
    }

    public void clearSharedRegistration() {
        if (level == null || level.isClientSide || level.getServer() == null || bindingId <= 0 || !isController()) {
            return;
        }
        PocketFactorySavedData.get(level.getServer()).disposeFluidBinding(bindingId);
    }

    @Override
    public void removeController(boolean keepFluids) {
        if (level.isClientSide) {
            return;
        }
        boolean linkedSharedState = bindingId > 0 || sharedFluidMerged;
        super.removeController(keepFluids);
        if (linkedSharedState) {
            tankInventory.setFluid(FluidStack.EMPTY);
            displayCapacityMb = getCapacityMultiplier();
            tankInventory.setCapacity(displayCapacityMb);
            sharedFluidMerged = false;
            lastPushedFluidKey = "";
            lastPushedAmount = -1;
            refreshDisplayFluidLevel();
            setChanged();
            sendData();
        }
    }

    @Override
    public void notifyMultiUpdated() {
        if (!level.isClientSide && isController() && bindingId > 0) {
            sharedTopologyDirty = true;
        }
        super.notifyMultiUpdated();
    }

    public void applyExistingBinding(LinkedStorageBindingHelper.BindingTarget bindingTarget, PocketFactorySavedData.FluidStorageSnapshot snapshot) {
        applyBinding(bindingTarget);
        sharedFluidMerged = true;
        sharedTopologyDirty = false;
        applySharedFluidSnapshot(snapshot);
        setChanged();
        sendData();
    }

    private void applySharedFluidSnapshot(PocketFactorySavedData.FluidStorageSnapshot snapshot) {
        int previousCapacity = tankInventory.getCapacity();
        displayCapacityMb = Math.max(snapshot.currentCapacityMb(), getCapacityMultiplier());
        tankInventory.setCapacity(displayCapacityMb);
        FluidStack existing = tankInventory.getFluid().copy();
        if (!FluidStack.isSameFluidSameComponents(existing, snapshot.fluid()) || existing.getAmount() != snapshot.fluid().getAmount()) {
            tankInventory.setFluid(snapshot.fluid().copy());
            onFluidStackChanged(tankInventory.getFluid());
        } else if (previousCapacity != displayCapacityMb) {
            refreshDisplayFluidLevel();
        }
        lastSharedVersion = snapshot.version();
        lastPushedFluidKey = snapshot.fluid().isEmpty() ? "" : snapshot.fluid().getFluid().builtInRegistryHolder().key().location().toString();
        lastPushedAmount = snapshot.fluid().getAmount();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LinkedFluidTankBlockEntity source = getTooltipSource();
        if (source != this) {
            return source != null && source.addToGoggleTooltip(tooltip, isPlayerSneaking);
        }

        tooltip.add(getBlockState().getBlock().getName().copy().withStyle(ChatFormatting.GOLD));
        tooltip.add(bindingLine(hasBinding()));
        if (hasBinding()) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.binding_id", bindingId)
                    .withStyle(ChatFormatting.GRAY));
            if (hasFactoryId()) {
                tooltip.add(Component.translatable("goggles.create_pocket_factory.common.factory_id", factoryId)
                        .withStyle(ChatFormatting.GRAY));
            }
            if (hasChunkBinding()) {
                tooltip.add(Component.translatable("goggles.create_pocket_factory.common.chunk_id", boundChunkX, boundChunkZ)
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        if (!hasBinding()) {
            return true;
        }

        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }

    private LinkedFluidTankBlockEntity getTooltipSource() {
        if (isController()) {
            return this;
        }
        FluidTankBlockEntity controllerBE = getControllerBE();
        return controllerBE instanceof LinkedFluidTankBlockEntity linkedController ? linkedController : this;
    }

    private boolean hasChunkBinding() {
        return boundChunkX != NO_CHUNK && boundChunkZ != NO_CHUNK;
    }

    private void applyBinding(LinkedStorageBindingHelper.BindingTarget bindingTarget) {
        int newBindingId = bindingTarget.bindingId();
        int newFactoryId = bindingTarget.factoryId();
        int newChunkX = bindingTarget.chunkOffset() != null ? bindingTarget.chunkOffset().x() : NO_CHUNK;
        int newChunkZ = bindingTarget.chunkOffset() != null ? bindingTarget.chunkOffset().z() : NO_CHUNK;
        if (bindingId == newBindingId && factoryId == newFactoryId && boundChunkX == newChunkX && boundChunkZ == newChunkZ) {
            return;
        }
        bindingId = newBindingId;
        factoryId = newFactoryId;
        boundChunkX = newChunkX;
        boundChunkZ = newChunkZ;
        setChanged();
        sendData();
    }

    public void clearBindingForPendingContinuation() {
        if (level == null || level.isClientSide) {
            return;
        }
        bindingId = -1;
        factoryId = -1;
        boundChunkX = NO_CHUNK;
        boundChunkZ = NO_CHUNK;
        tankInventory.setFluid(FluidStack.EMPTY);
        displayCapacityMb = getCapacityMultiplier();
        tankInventory.setCapacity(displayCapacityMb);
        sharedFluidMerged = false;
        sharedTopologyDirty = false;
        lastPushedFluidKey = "";
        lastPushedAmount = -1;
        refreshDisplayFluidLevel();
        setChanged();
        sendData();
    }

    private void refreshDisplayFluidLevel() {
        LerpedFloat fluidLevel = getFluidLevel();
        float fillState = tankInventory.getCapacity() > 0 ? (float) tankInventory.getFluidAmount() / tankInventory.getCapacity() : 0.0F;
        if (fluidLevel == null) {
            setFluidLevel(LerpedFloat.linear().startWithValue(fillState));
            fluidLevel = getFluidLevel();
        }
        fluidLevel.chase(fillState, 0.5f, Chaser.EXP);
    }

    private static Component bindingLine(boolean bound) {
        return Component.translatable("goggles.create_pocket_factory.common.binding")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(bound
                                ? "goggles.create_pocket_factory.common.bound"
                                : "goggles.create_pocket_factory.common.unbound")
                        .withStyle(bound ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}