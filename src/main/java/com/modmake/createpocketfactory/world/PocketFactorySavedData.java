package com.modmake.createpocketfactory.world;

import com.modmake.createpocketfactory.CreatePocketFactory;
import java.util.Collections;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class PocketFactorySavedData extends SavedData {
    public static final String DATA_NAME = CreatePocketFactory.MOD_ID + "_factories";
    public static final int DEFAULT_MIN_Y = 32;
    public static final int DEFAULT_MAX_Y = 52;

    private final Map<Integer, FactoryRecord> factories = new LinkedHashMap<>();
    private final Map<Integer, BindingRecord> bindings = new LinkedHashMap<>();
    private final Map<String, Integer> endpointIndex = new LinkedHashMap<>();
    private final Map<Integer, ItemStorageState> itemChannels = new LinkedHashMap<>();
    private final Map<Integer, FluidStorageState> fluidChannels = new LinkedHashMap<>();
    private final Map<Integer, ChuteBridgeState> chuteChannels = new LinkedHashMap<>();
    private final Map<Integer, PumpBridgeState> pumpChannels = new LinkedHashMap<>();
    private final Map<Integer, ClutchBridgeState> clutchChannels = new LinkedHashMap<>();
    private final Map<Integer, PortalChannelState> portalChannels = new LinkedHashMap<>();
    private int nextFactoryId = 1;
    private int nextBindingId = 1;
    private int nextSlotIndex = 0;

    public static PocketFactorySavedData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(PocketFactorySavedData::new, PocketFactorySavedData::load), DATA_NAME);
    }

    private static PocketFactorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PocketFactorySavedData data = new PocketFactorySavedData();
        data.nextFactoryId = tag.getInt("NextFactoryId");
        data.nextBindingId = Math.max(1, tag.contains("NextBindingId", Tag.TAG_INT) ? tag.getInt("NextBindingId") : 1);
        data.nextSlotIndex = tag.getInt("NextSlotIndex");

        ListTag factoriesTag = tag.getList("Factories", Tag.TAG_COMPOUND);
        for (Tag entry : factoriesTag) {
            FactoryRecord record = FactoryRecord.load((CompoundTag) entry);
            data.factories.put(record.id(), record);
        }

        ListTag bindingsTag = tag.getList("Bindings", Tag.TAG_COMPOUND);
        for (Tag entry : bindingsTag) {
            BindingRecord record = BindingRecord.load((CompoundTag) entry);
            if (record != null && !record.isEmpty()) {
                data.bindings.put(record.bindingId(), record);
            }
        }

        ListTag itemChannelsTag = tag.getList("ItemChannels", Tag.TAG_COMPOUND);
        for (Tag entry : itemChannelsTag) {
            CompoundTag channelTag = (CompoundTag) entry;
            int bindingId = channelTag.contains("BindingId", Tag.TAG_INT) ? channelTag.getInt("BindingId") : channelTag.getInt("FactoryId");
            data.itemChannels.put(bindingId, ItemStorageState.load(channelTag));
        }

        ListTag fluidChannelsTag = tag.getList("FluidChannels", Tag.TAG_COMPOUND);
        for (Tag entry : fluidChannelsTag) {
            CompoundTag channelTag = (CompoundTag) entry;
            int bindingId = channelTag.contains("BindingId", Tag.TAG_INT) ? channelTag.getInt("BindingId") : channelTag.getInt("FactoryId");
            data.fluidChannels.put(bindingId, FluidStorageState.load(channelTag, registries));
        }

        ListTag portalChannelsTag = tag.getList("PortalChannels", Tag.TAG_COMPOUND);
        for (Tag entry : portalChannelsTag) {
            CompoundTag channelTag = (CompoundTag) entry;
            data.portalChannels.put(channelTag.getInt("FactoryId"), PortalChannelState.load(channelTag));
        }

        ListTag chuteChannelsTag = tag.getList("ChuteChannels", Tag.TAG_COMPOUND);
        for (Tag entry : chuteChannelsTag) {
            CompoundTag channelTag = (CompoundTag) entry;
            int bindingId = channelTag.getInt("BindingId");
            if (bindingId > 0) {
                data.chuteChannels.put(bindingId, ChuteBridgeState.load(channelTag, registries));
            }
        }

        ListTag pumpChannelsTag = tag.getList("PumpChannels", Tag.TAG_COMPOUND);
        for (Tag entry : pumpChannelsTag) {
            CompoundTag channelTag = (CompoundTag) entry;
            int bindingId = channelTag.getInt("BindingId");
            if (bindingId > 0) {
                data.pumpChannels.put(bindingId, PumpBridgeState.load(channelTag, registries));
            }
        }

        data.bootstrapLegacyBindings();
    data.rebuildEndpointIndex();

        return data;
    }

    private PocketFactorySavedData() {
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextFactoryId", nextFactoryId);
        tag.putInt("NextBindingId", nextBindingId);
        tag.putInt("NextSlotIndex", nextSlotIndex);

        ListTag factoriesTag = new ListTag();
        for (FactoryRecord record : factories.values()) {
            factoriesTag.add(record.save());
        }
        tag.put("Factories", factoriesTag);

        ListTag bindingsTag = new ListTag();
        for (BindingRecord record : bindings.values()) {
            if (!record.isEmpty()) {
                bindingsTag.add(record.save());
            }
        }
        tag.put("Bindings", bindingsTag);

        ListTag itemChannelsTag = new ListTag();
        for (Map.Entry<Integer, ItemStorageState> entry : itemChannels.entrySet()) {
            itemChannelsTag.add(entry.getValue().save(entry.getKey()));
        }
        tag.put("ItemChannels", itemChannelsTag);

        ListTag fluidChannelsTag = new ListTag();
        for (Map.Entry<Integer, FluidStorageState> entry : fluidChannels.entrySet()) {
            fluidChannelsTag.add(entry.getValue().save(entry.getKey(), registries));
        }
        tag.put("FluidChannels", fluidChannelsTag);

        ListTag portalChannelsTag = new ListTag();
        for (Map.Entry<Integer, PortalChannelState> entry : portalChannels.entrySet()) {
            portalChannelsTag.add(entry.getValue().save(entry.getKey()));
        }
        tag.put("PortalChannels", portalChannelsTag);

        ListTag chuteChannelsTag = new ListTag();
        for (Map.Entry<Integer, ChuteBridgeState> entry : chuteChannels.entrySet()) {
            chuteChannelsTag.add(entry.getValue().save(entry.getKey(), registries));
        }
        tag.put("ChuteChannels", chuteChannelsTag);

        ListTag pumpChannelsTag = new ListTag();
        for (Map.Entry<Integer, PumpBridgeState> entry : pumpChannels.entrySet()) {
            pumpChannelsTag.add(entry.getValue().save(entry.getKey(), registries));
        }
        tag.put("PumpChannels", pumpChannelsTag);

        return tag;
    }

    public Collection<FactoryRecord> getFactories() {
        return factories.values();
    }

    public @Nullable FactoryRecord getFactory(int factoryId) {
        return factories.get(factoryId);
    }

    public FactoryRecord updateFactory(FactoryRecord factory) {
        factories.put(factory.id(), factory);
        setDirty();
        return factory;
    }

    public FactoryRecord createFactory(@Nullable UUID owner) {
        int factoryId = nextFactoryId++;
        SlotCoord slot = spiralSlot(nextSlotIndex++);
        FactoryRecord record = new FactoryRecord(
                factoryId,
                owner,
                slot.x(),
                slot.z(),
                Set.of(new FactoryChunkOffset(0, 0)),
                DEFAULT_MIN_Y,
                DEFAULT_MAX_Y,
                null,
                BlockPos.ZERO,
                false
        );
        factories.put(factoryId, record);
        setDirty();
        return record;
    }

    public FactoryRecord getOrCreateDebugFactory(@Nullable UUID owner) {
        FactoryRecord existing = factories.values().stream().findFirst().orElse(null);
        return existing != null ? existing : createFactory(owner);
    }

    public void bindEntrance(int factoryId, ResourceKey<Level> dimension, BlockPos entrancePos) {
        FactoryRecord existing = factories.get(factoryId);
        if (existing == null) {
            return;
        }

        factories.put(factoryId, existing.withEntrance(dimension.location(), entrancePos, true));
        setDirty();
    }

    public void clearEntranceIfMatches(int factoryId, ResourceKey<Level> dimension, BlockPos entrancePos) {
        FactoryRecord existing = factories.get(factoryId);
        if (existing == null || !existing.hasEntrance()) {
            return;
        }

        if (!dimension.location().equals(existing.entranceDimension()) || !entrancePos.equals(existing.entrancePos())) {
            return;
        }

        factories.put(factoryId, existing.withEntrance(null, BlockPos.ZERO, false));
        setDirty();
    }

    public ItemStorageSnapshot registerItemEndpoint(int factoryId, String endpointKey, int slotCount, HolderLookup.Provider registries) {
        ItemStorageState state = itemChannels.computeIfAbsent(factoryId, id -> new ItemStorageState());
        if (!Integer.valueOf(slotCount).equals(state.endpointSlots.get(endpointKey))) {
            state.endpointSlots.put(endpointKey, slotCount);
            state.ensureStorageSlots(Math.max(state.getCurrentCapacity(), slotCount), registries);
            setDirty();
        }
        return state.snapshot(registries);
    }

    public int bindEndpoint(int factoryId, BindingChannel channel, EndpointRole role, String endpointKey) {
        BindingRecord record = resolveBindingRecordForWrite(factoryId, channel, role, endpointKey);
        if (record.setEndpoint(role, endpointKey)) {
            endpointIndex.put(endpointKey, record.bindingId());
            setDirty();
        }
        return record.bindingId();
    }

    public int createBinding(int factoryId, BindingChannel channel) {
        BindingRecord record = new BindingRecord(nextBindingId++, channel, factoryId);
        bindings.put(record.bindingId(), record);
        setDirty();
        return record.bindingId();
    }

    public int bindEndpointToBinding(int bindingId, int factoryId, BindingChannel channel, EndpointRole role, String endpointKey) {
        reassignEndpointIndex(endpointKey, bindingId);

        BindingRecord record = bindings.get(bindingId);
        if (record == null) {
            record = new BindingRecord(bindingId, channel, factoryId);
            bindings.put(bindingId, record);
            nextBindingId = Math.max(nextBindingId, bindingId + 1);
        } else if (record.channel() != channel || record.factoryId() != factoryId) {
            return -1;
        }

        String staleEndpoint = record.getEndpoint(role);
        if (staleEndpoint != null && !staleEndpoint.equals(endpointKey)) {
            endpointIndex.remove(staleEndpoint);
        }

        if (record.setEndpoint(role, endpointKey)) {
            endpointIndex.put(endpointKey, bindingId);
            setDirty();
        } else {
            endpointIndex.put(endpointKey, bindingId);
        }
        return bindingId;
    }

    public @Nullable Integer getBindingFactoryId(int bindingId) {
        BindingRecord record = bindings.get(bindingId);
        return record == null ? null : record.factoryId();
    }

    public @Nullable BindingChannel getBindingChannel(int bindingId) {
        BindingRecord record = bindings.get(bindingId);
        return record == null ? null : record.channel();
    }

    public @Nullable BindingEndpoints getBindingEndpoints(int bindingId, BindingChannel channel) {
        BindingRecord record = bindings.get(bindingId);
        if (record == null || record.channel() != channel) {
            return null;
        }
        return new BindingEndpoints(record.getEndpoint(EndpointRole.INTERNAL), record.getEndpoint(EndpointRole.EXTERNAL));
    }

    public boolean disposeBinding(int bindingId, BindingChannel channel) {
        if (channel == BindingChannel.LINKED_CHUTE) {
            chuteChannels.remove(bindingId);
        }
        if (channel == BindingChannel.LINKED_PUMP) {
            pumpChannels.remove(bindingId);
        }
        if (channel == BindingChannel.LINKED_CLUTCH) {
            clutchChannels.remove(bindingId);
        }
        if (!clearBindingAssignments(bindingId, channel)) {
            return false;
        }
        setDirty();
        return true;
    }

    public @Nullable Integer getBoundBindingId(int factoryId, BindingChannel channel, EndpointRole role) {
        BindingRecord record = findBindingRecord(factoryId, channel, role);
        return record == null ? null : record.bindingId();
    }

    public @Nullable String getBoundItemEndpointKey(int factoryId, boolean internalEndpoint) {
        return getBoundEndpointKey(factoryId, BindingChannel.ITEM_STORAGE,
                internalEndpoint ? EndpointRole.INTERNAL : EndpointRole.EXTERNAL);
    }

    public void setBoundItemEndpointKey(int factoryId, boolean internalEndpoint, @Nullable String endpointKey) {
        if (endpointKey != null && !endpointKey.isEmpty()) {
            bindEndpoint(factoryId, BindingChannel.ITEM_STORAGE,
                    internalEndpoint ? EndpointRole.INTERNAL : EndpointRole.EXTERNAL, endpointKey);
        }
    }

    public @Nullable ItemStorageSnapshot disposeItemBinding(int bindingId, HolderLookup.Provider registries) {
        ItemStorageState state = itemChannels.remove(bindingId);
        boolean changed = clearBindingAssignments(bindingId, BindingChannel.ITEM_STORAGE);
        if (state == null) {
            if (changed) {
                setDirty();
            }
            return null;
        }
        setDirty();
        return state.snapshot(registries);
    }

    public void mergeItemEndpointInventory(int bindingId, String endpointKey, ItemStackHandler handler, HolderLookup.Provider registries) {
        ItemStorageState state = itemChannels.computeIfAbsent(bindingId, id -> new ItemStorageState());
        state.endpointSlots.put(endpointKey, handler.getSlots());
        state.ensureStorageSlots(Math.max(state.getCurrentCapacity(), handler.getSlots()), registries);
        ItemStackHandler storage = state.createHandlerCopy(registries);
        boolean changed = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack remaining = handler.getStackInSlot(slot).copy();
            if (remaining.isEmpty()) {
                continue;
            }
            for (int targetSlot = 0; targetSlot < storage.getSlots() && !remaining.isEmpty(); targetSlot++) {
                remaining = storage.insertItem(targetSlot, remaining, false);
            }
            if (remaining.getCount() != handler.getStackInSlot(slot).getCount()) {
                changed = true;
            }
        }
        if (changed) {
            state.writeFrom(storage, registries);
            state.version++;
            setDirty();
        }
    }

    public ItemStorageSnapshot getItemSnapshot(int bindingId, HolderLookup.Provider registries) {
        ItemStorageState state = itemChannels.computeIfAbsent(bindingId, id -> new ItemStorageState());
        return state.snapshot(registries);
    }

    public int saveItemSnapshot(int bindingId, ItemStackHandler handler, HolderLookup.Provider registries) {
        ItemStorageState state = itemChannels.computeIfAbsent(bindingId, id -> new ItemStorageState());
        state.writeFrom(handler, registries);
        state.version++;
        setDirty();
        return state.version;
    }

    public FluidStorageSnapshot registerFluidEndpoint(int bindingId, String endpointKey, int capacityMb) {
        FluidStorageState state = fluidChannels.computeIfAbsent(bindingId, id -> new FluidStorageState());
        if (!Integer.valueOf(capacityMb).equals(state.endpointCapacityMb.get(endpointKey))) {
            state.endpointCapacityMb.put(endpointKey, capacityMb);
            setDirty();
        }
        return state.snapshot();
    }

    public @Nullable String getBoundFluidEndpointKey(int factoryId, boolean internalEndpoint) {
        return getBoundEndpointKey(factoryId, BindingChannel.FLUID_STORAGE,
                internalEndpoint ? EndpointRole.INTERNAL : EndpointRole.EXTERNAL);
    }

    public void setBoundFluidEndpointKey(int factoryId, boolean internalEndpoint, @Nullable String endpointKey) {
        if (endpointKey != null && !endpointKey.isEmpty()) {
            bindEndpoint(factoryId, BindingChannel.FLUID_STORAGE,
                    internalEndpoint ? EndpointRole.INTERNAL : EndpointRole.EXTERNAL, endpointKey);
        }
    }

    public @Nullable FluidStorageSnapshot disposeFluidBinding(int bindingId) {
        FluidStorageState state = fluidChannels.remove(bindingId);
        boolean changed = clearBindingAssignments(bindingId, BindingChannel.FLUID_STORAGE);
        if (state == null) {
            if (changed) {
                setDirty();
            }
            return null;
        }
        setDirty();
        return state.snapshot();
    }

    public @Nullable String getBoundEndpointKey(int factoryId, BindingChannel channel, EndpointRole role) {
        BindingRecord record = findBindingRecord(factoryId, channel, role);
        return record == null ? null : record.getEndpoint(role);
    }

    public void setBoundEndpointKey(int factoryId, BindingChannel channel, EndpointRole role, @Nullable String endpointKey) {
        if (endpointKey == null || endpointKey.isEmpty()) {
            BindingRecord record = findBindingRecord(factoryId, channel, role);
            if (record == null) {
                return;
            }
            String existingEndpointKey = record.getEndpoint(role);
            if (existingEndpointKey == null || !record.setEndpoint(role, null)) {
                return;
            }
            endpointIndex.remove(existingEndpointKey);
            if (record.isEmpty()) {
                bindings.remove(record.bindingId());
            }
            setDirty();
            return;
        }
        bindEndpoint(factoryId, channel, role, endpointKey);
    }

    public boolean clearBoundEndpointIfMatches(int factoryId, BindingChannel channel, EndpointRole role, String endpointKey) {
        BindingRecord record = findBindingRecord(factoryId, channel, role);
        if (record == null || !java.util.Objects.equals(record.getEndpoint(role), endpointKey)) {
            return false;
        }
        record.setEndpoint(role, null);
        endpointIndex.remove(endpointKey);
        if (record.isEmpty()) {
            bindings.remove(record.bindingId());
        }
        setDirty();
        return true;
    }

    public FluidStorageSnapshot mergeFluidEndpointState(int bindingId, String endpointKey, FluidStack localFluid, int capacityMb) {
        FluidStorageState state = fluidChannels.computeIfAbsent(bindingId, id -> new FluidStorageState());
        state.endpointCapacityMb.put(endpointKey, capacityMb);
        boolean changed = false;
        if (!localFluid.isEmpty()) {
            if (state.fluid.isEmpty()) {
                state.fluid = localFluid.copy();
                changed = true;
            } else if (FluidStack.isSameFluidSameComponents(state.fluid, localFluid)) {
                state.fluid.grow(localFluid.getAmount());
                changed = true;
            }
        }
        if (changed) {
            state.version++;
            setDirty();
        }
        return state.snapshot();
    }

    public FluidStorageSnapshot getFluidSnapshot(int bindingId) {
        FluidStorageState state = fluidChannels.computeIfAbsent(bindingId, id -> new FluidStorageState());
        return state.snapshot();
    }

    public int saveFluidSnapshot(int bindingId, FluidStack fluidStack) {
        FluidStorageState state = fluidChannels.computeIfAbsent(bindingId, id -> new FluidStorageState());
        state.fluid = fluidStack.copy();
        state.version++;
        setDirty();
        return state.version;
    }

    public boolean isEndpointBoundToBinding(int bindingId, BindingChannel channel, String endpointKey) {
        Integer indexedBindingId = endpointIndex.get(endpointKey);
        if (!Integer.valueOf(bindingId).equals(indexedBindingId)) {
            return false;
        }
        BindingRecord record = bindings.get(bindingId);
        return record != null && record.channel() == channel && record.containsEndpoint(endpointKey);
    }

    public ChuteBridgeSnapshot getChuteSnapshot(int bindingId, HolderLookup.Provider registries) {
        return chuteChannels.computeIfAbsent(bindingId, id -> new ChuteBridgeState()).snapshot(registries);
    }

    public boolean offerChuteTransfer(int bindingId, String targetEndpointKey, ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return false;
        }
        ChuteBridgeState state = chuteChannels.computeIfAbsent(bindingId, id -> new ChuteBridgeState());
        if (!state.offer(targetEndpointKey, stack, registries)) {
            return false;
        }
        setDirty();
        return true;
    }

    public ItemStack pollChuteTransfer(int bindingId, String endpointKey, HolderLookup.Provider registries) {
        ChuteBridgeState state = chuteChannels.get(bindingId);
        if (state == null) {
            return ItemStack.EMPTY;
        }
        ItemStack polled = state.poll(endpointKey, registries);
        if (!polled.isEmpty()) {
            setDirty();
        }
        return polled;
    }

    public FluidStack peekPumpBridgeFluid(int bindingId, @Nullable String requesterEndpointKey) {
        PumpBridgeState state = pumpChannels.get(bindingId);
        return state == null ? FluidStack.EMPTY : state.peekForRequester(requesterEndpointKey);
    }

    public int getPumpBridgeRemainingCapacity(int bindingId, @Nullable String sourceEndpointKey, int capacityMb) {
        if (sourceEndpointKey == null || sourceEndpointKey.isEmpty()) {
            return 0;
        }
        PumpBridgeState state = pumpChannels.computeIfAbsent(bindingId, id -> new PumpBridgeState());
        return Math.max(0, capacityMb - state.getAmountForSource(sourceEndpointKey));
    }

    public int getPumpBridgeAcceptedAmount(int bindingId, @Nullable String sourceEndpointKey, FluidStack stack, int capacityMb) {
        if (sourceEndpointKey == null || sourceEndpointKey.isEmpty() || stack.isEmpty()) {
            return 0;
        }
        PumpBridgeState state = pumpChannels.computeIfAbsent(bindingId, id -> new PumpBridgeState());
        FluidStack stored = state.peekForSource(sourceEndpointKey);
        if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, stack)) {
            return 0;
        }
        return Math.min(stack.getAmount(), Math.max(0, capacityMb - stored.getAmount()));
    }

    public int fillPumpBridge(int bindingId, @Nullable String sourceEndpointKey, FluidStack stack, int capacityMb) {
        if (sourceEndpointKey == null || sourceEndpointKey.isEmpty() || stack.isEmpty()) {
            return 0;
        }
        PumpBridgeState state = pumpChannels.computeIfAbsent(bindingId, id -> new PumpBridgeState());
        int accepted = getPumpBridgeAcceptedAmount(bindingId, sourceEndpointKey, stack, capacityMb);
        if (accepted <= 0) {
            return 0;
        }
        state.fillFromSource(sourceEndpointKey, stack, accepted);
        state.version++;
        setDirty();
        return accepted;
    }

    public FluidStack drainPumpBridgeFluid(int bindingId, @Nullable String requesterEndpointKey, int amount) {
        PumpBridgeState state = pumpChannels.get(bindingId);
        if (state == null || amount <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = state.drainForRequester(requesterEndpointKey, amount);
        if (drained.isEmpty()) {
            return FluidStack.EMPTY;
        }
        state.version++;
        setDirty();
        return drained;
    }

    public ClutchBridgeSnapshot getClutchSnapshot(int bindingId) {
        ClutchBridgeState state = clutchChannels.computeIfAbsent(bindingId, id -> new ClutchBridgeState());
        return state.snapshot();
    }

    public ClutchBridgeSnapshot updateClutchEndpointState(int bindingId, EndpointRole role, boolean powered,
                                                          @Nullable ClutchDriveState driveState) {
        ClutchBridgeState state = clutchChannels.computeIfAbsent(bindingId, id -> new ClutchBridgeState());
        state.updateEndpoint(role, powered, driveState);
        return state.snapshot();
    }

    public void clearClutchEndpointState(int bindingId, EndpointRole role) {
        ClutchBridgeState state = clutchChannels.get(bindingId);
        if (state == null) {
            return;
        }
        if (state.clearEndpoint(role) && state.isEmpty()) {
            clutchChannels.remove(bindingId);
        }
    }

    public void registerPortalEndpoint(int factoryId, PortalEndpoint endpoint, ResourceKey<Level> dimension, BlockPos pos) {
        PortalChannelState state = portalChannels.computeIfAbsent(factoryId, id -> new PortalChannelState());
        if (state.set(endpoint, dimension.location(), pos)) {
            setDirty();
        }
    }

    public void clearPortalEndpointIfMatches(int factoryId, PortalEndpoint endpoint, ResourceKey<Level> dimension, BlockPos pos) {
        PortalChannelState state = portalChannels.get(factoryId);
        if (state == null) {
            return;
        }
        if (state.clearIfMatches(endpoint, dimension.location(), pos)) {
            setDirty();
        }
    }

    public @Nullable PortalEndpointRecord getPortalEndpoint(int factoryId, PortalEndpoint endpoint) {
        PortalChannelState state = portalChannels.get(factoryId);
        return state == null ? null : state.get(endpoint);
    }

    private static SlotCoord spiralSlot(int index) {
        if (index == 0) {
            return new SlotCoord(0, 0);
        }

        int x = 0;
        int z = 0;
        int stepLength = 1;
        int stepsTaken = 0;
        int directionIndex = 0;
        int directionRepeats = 0;
        int[][] directions = new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

        for (int i = 0; i < index; i++) {
            x += directions[directionIndex][0];
            z += directions[directionIndex][1];
            stepsTaken++;

            if (stepsTaken == stepLength) {
                stepsTaken = 0;
                directionIndex = (directionIndex + 1) % directions.length;
                directionRepeats++;
                if ((directionRepeats & 1) == 0) {
                    stepLength++;
                }
            }
        }

        return new SlotCoord(x, z);
    }

    private record SlotCoord(int x, int z) {
    }

    public record ItemStorageSnapshot(ItemStackHandler handler, int version, int currentCapacity) {
    }

    public record FluidStorageSnapshot(FluidStack fluid, int version, int currentCapacityMb) {
    }

    public record BindingEndpoints(@Nullable String internalEndpointKey, @Nullable String externalEndpointKey) {
    }

    public record ChuteBridgeSnapshot(ItemStack pendingStack, @Nullable String targetEndpointKey, int version) {
    }

    public record ClutchDriveState(float speed, Direction.AxisDirection axisDirection, float availableStress) {
    }

    public record ClutchEndpointSnapshot(boolean powered, @Nullable ClutchDriveState driveState) {
    }

    public record ClutchBridgeSnapshot(@Nullable ClutchEndpointSnapshot internalEndpoint,
                                       @Nullable ClutchEndpointSnapshot externalEndpoint,
                                       int version) {
    }

    public record PortalEndpointRecord(ResourceLocation dimension, BlockPos pos) {
    }

    public enum PortalEndpoint {
        INTERNAL("Internal"),
        EXTERNAL("External");

        private final String key;

        PortalEndpoint(String key) {
            this.key = key;
        }

        private String key() {
            return key;
        }
    }

    public enum BindingChannel {
        ITEM_STORAGE("item_storage"),
        FLUID_STORAGE("fluid_storage"),
        LINKED_CHUTE("linked_chute"),
        LINKED_PUMP("linked_pump"),
        LINKED_CLUTCH("linked_clutch");

        private final String id;

        BindingChannel(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }

        private static @Nullable BindingChannel byId(String id) {
            for (BindingChannel value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            return null;
        }
    }

    public enum EndpointRole {
        INTERNAL("internal"),
        EXTERNAL("external");

        private final String id;

        EndpointRole(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }

        private static @Nullable EndpointRole byId(String id) {
            for (EndpointRole value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            return null;
        }
    }

    private void bootstrapLegacyBindings() {
        Map<Integer, ItemStorageState> migratedItemChannels = new LinkedHashMap<>(itemChannels);
        for (Map.Entry<Integer, ItemStorageState> entry : itemChannels.entrySet()) {
            int factoryId = entry.getKey();
            ItemStorageState state = entry.getValue();
            if (state.boundInternalEndpointKey == null && state.boundExternalEndpointKey == null) {
                continue;
            }
            int bindingId = nextBindingId++;
            BindingRecord record = new BindingRecord(bindingId, BindingChannel.ITEM_STORAGE, factoryId);
            if (state.boundInternalEndpointKey != null) {
                record.setEndpoint(EndpointRole.INTERNAL, state.boundInternalEndpointKey);
            }
            if (state.boundExternalEndpointKey != null) {
                record.setEndpoint(EndpointRole.EXTERNAL, state.boundExternalEndpointKey);
            }
            if (!record.isEmpty()) {
                bindings.put(bindingId, record);
            }
            state.boundInternalEndpointKey = null;
            state.boundExternalEndpointKey = null;
            if (factoryId != bindingId) {
                migratedItemChannels.remove(factoryId);
                migratedItemChannels.put(bindingId, state);
            }
        }
        itemChannels.clear();
        itemChannels.putAll(migratedItemChannels);

        Map<Integer, FluidStorageState> migratedFluidChannels = new LinkedHashMap<>(fluidChannels);
        for (Map.Entry<Integer, FluidStorageState> entry : fluidChannels.entrySet()) {
            int factoryId = entry.getKey();
            FluidStorageState state = entry.getValue();
            if (state.boundInternalEndpointKey == null && state.boundExternalEndpointKey == null) {
                continue;
            }
            int bindingId = nextBindingId++;
            BindingRecord record = new BindingRecord(bindingId, BindingChannel.FLUID_STORAGE, factoryId);
            if (state.boundInternalEndpointKey != null) {
                record.setEndpoint(EndpointRole.INTERNAL, state.boundInternalEndpointKey);
            }
            if (state.boundExternalEndpointKey != null) {
                record.setEndpoint(EndpointRole.EXTERNAL, state.boundExternalEndpointKey);
            }
            if (!record.isEmpty()) {
                bindings.put(bindingId, record);
            }
            state.boundInternalEndpointKey = null;
            state.boundExternalEndpointKey = null;
            if (factoryId != bindingId) {
                migratedFluidChannels.remove(factoryId);
                migratedFluidChannels.put(bindingId, state);
            }
        }
        fluidChannels.clear();
        fluidChannels.putAll(migratedFluidChannels);

        int maxKnownBindingId = Math.max(maxMapKey(bindings), Math.max(maxMapKey(itemChannels), maxMapKey(fluidChannels)));
        nextBindingId = Math.max(nextBindingId, maxKnownBindingId + 1);
    }

    private void syncLegacyBindingState(int factoryId, BindingChannel channel, EndpointRole role, @Nullable String endpointKey) {
    }

    private boolean clearBindingAssignments(int bindingId, BindingChannel channel) {
        BindingRecord record = bindings.get(bindingId);
        if (record == null || record.channel() != channel) {
            return false;
        }
        for (String endpointKey : record.endpointKeys()) {
            endpointIndex.remove(endpointKey);
        }
        bindings.remove(bindingId);
        return true;
    }

    private void rebuildEndpointIndex() {
        endpointIndex.clear();
        java.util.Iterator<Map.Entry<Integer, BindingRecord>> iterator = bindings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, BindingRecord> entry = iterator.next();
            BindingRecord record = entry.getValue();
            if (record.isEmpty()) {
                iterator.remove();
                continue;
            }
            for (String endpointKey : record.endpointKeys()) {
                endpointIndex.put(endpointKey, entry.getKey());
            }
        }
    }

    private BindingRecord resolveBindingRecordForWrite(int factoryId, BindingChannel channel, EndpointRole role, String endpointKey) {
        Integer existingBindingId = endpointIndex.get(endpointKey);
        if (existingBindingId != null) {
            BindingRecord existingRecord = bindings.get(existingBindingId);
            if (existingRecord != null && existingRecord.channel() == channel && existingRecord.factoryId() == factoryId) {
                return existingRecord;
            }
            reassignEndpointIndex(endpointKey, null);
        }

        BindingRecord oppositeRoleRecord = null;
        EndpointRole counterpart = role == EndpointRole.INTERNAL ? EndpointRole.EXTERNAL : EndpointRole.INTERNAL;
        for (BindingRecord record : bindings.values()) {
            if (record.factoryId() != factoryId || record.channel() != channel) {
                continue;
            }
            if (record.getEndpoint(role) == null && record.getEndpoint(counterpart) != null) {
                oppositeRoleRecord = record;
                break;
            }
        }

        if (oppositeRoleRecord != null) {
            return oppositeRoleRecord;
        }

        BindingRecord created = new BindingRecord(nextBindingId++, channel, factoryId);
        bindings.put(created.bindingId(), created);
        return created;
    }

    private void reassignEndpointIndex(String endpointKey, @Nullable Integer replacementBindingId) {
        Integer existingBindingId = endpointIndex.get(endpointKey);
        if (existingBindingId == null) {
            return;
        }

        BindingRecord existingRecord = bindings.get(existingBindingId);
        if (existingRecord != null) {
            existingRecord.clearEndpoint(endpointKey);
            if (existingRecord.isEmpty()) {
                bindings.remove(existingBindingId);
            }
        }

        if (replacementBindingId == null) {
            endpointIndex.remove(endpointKey);
        } else {
            endpointIndex.put(endpointKey, replacementBindingId);
        }
    }

    private @Nullable BindingRecord findBindingRecord(int factoryId, BindingChannel channel, EndpointRole role) {
        for (BindingRecord record : bindings.values()) {
            if (record.factoryId() == factoryId && record.channel() == channel && record.getEndpoint(role) != null) {
                return record;
            }
        }
        return null;
    }

    private static int maxMapKey(Map<Integer, ?> map) {
        int max = 0;
        for (Integer key : map.keySet()) {
            max = Math.max(max, key);
        }
        return max;
    }

    private static final class BindingRecord {
        private final int bindingId;
        private final BindingChannel channel;
        private final int factoryId;
        private final EnumMap<EndpointRole, String> endpoints = new EnumMap<>(EndpointRole.class);

        private BindingRecord(int bindingId, BindingChannel channel, int factoryId) {
            this.bindingId = bindingId;
            this.channel = channel;
            this.factoryId = factoryId;
        }

        private static @Nullable BindingRecord load(CompoundTag tag) {
            int bindingId = tag.contains("BindingId", Tag.TAG_INT) ? tag.getInt("BindingId") : 0;
            BindingChannel channel = BindingChannel.byId(tag.getString("Channel"));
            if (bindingId <= 0 || channel == null) {
                return null;
            }

            int factoryId = tag.contains("FactoryId", Tag.TAG_INT) ? tag.getInt("FactoryId") : 0;
            BindingRecord record = new BindingRecord(bindingId, channel, factoryId);
            ListTag endpoints = tag.getList("Endpoints", Tag.TAG_COMPOUND);
            for (Tag entry : endpoints) {
                CompoundTag endpointTag = (CompoundTag) entry;
                EndpointRole role = EndpointRole.byId(endpointTag.getString("Role"));
                if (role == null || !endpointTag.contains("EndpointKey", Tag.TAG_STRING)) {
                    continue;
                }
                String endpointKey = endpointTag.getString("EndpointKey");
                if (!endpointKey.isEmpty()) {
                    record.setEndpoint(role, endpointKey);
                }
            }
            return record;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("BindingId", bindingId);
            tag.putString("Channel", channel.id());
            tag.putInt("FactoryId", factoryId);
            ListTag endpointsTag = new ListTag();
            for (Map.Entry<EndpointRole, String> entry : endpoints.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                CompoundTag endpointTag = new CompoundTag();
                endpointTag.putString("Role", entry.getKey().id());
                endpointTag.putString("EndpointKey", entry.getValue());
                endpointsTag.add(endpointTag);
            }
            tag.put("Endpoints", endpointsTag);
            return tag;
        }

        private int bindingId() {
            return bindingId;
        }

        private BindingChannel channel() {
            return channel;
        }

        private int factoryId() {
            return factoryId;
        }

        private @Nullable String getEndpoint(EndpointRole role) {
            return endpoints.get(role);
        }

        private boolean setEndpoint(EndpointRole role, @Nullable String endpointKey) {
            String normalized = endpointKey == null || endpointKey.isEmpty() ? null : endpointKey;
            if (java.util.Objects.equals(endpoints.get(role), normalized)) {
                return false;
            }
            if (normalized == null) {
                endpoints.remove(role);
            } else {
                endpoints.put(role, normalized);
            }
            return true;
        }

        private void clearEndpoint(String endpointKey) {
            for (Map.Entry<EndpointRole, String> entry : endpoints.entrySet()) {
                if (java.util.Objects.equals(entry.getValue(), endpointKey)) {
                    endpoints.remove(entry.getKey());
                    return;
                }
            }
        }

        private boolean containsEndpoint(String endpointKey) {
            return endpoints.containsValue(endpointKey);
        }

        private int endpointCount() {
            return endpoints.size();
        }

        private Set<String> endpointKeys() {
            return new LinkedHashSet<>(endpoints.values());
        }

        private boolean isEmpty() {
            return endpoints.isEmpty();
        }
    }

    private static final class ItemStorageState {
        private final Map<String, Integer> endpointSlots = new LinkedHashMap<>();
        private CompoundTag handlerTag = new CompoundTag();
        private @Nullable String boundInternalEndpointKey;
        private @Nullable String boundExternalEndpointKey;
        private int storageSlots = 0;
        private int version = 0;

        private static ItemStorageState load(CompoundTag tag) {
            ItemStorageState state = new ItemStorageState();
            state.storageSlots = Math.max(0, tag.getInt("StorageSlots"));
            state.version = Math.max(0, tag.getInt("Version"));
            state.handlerTag = tag.getCompound("Handler").copy();
            state.boundInternalEndpointKey = tag.contains("BoundInternalEndpointKey", Tag.TAG_STRING)
                    ? tag.getString("BoundInternalEndpointKey")
                    : null;
            state.boundExternalEndpointKey = tag.contains("BoundExternalEndpointKey", Tag.TAG_STRING)
                    ? tag.getString("BoundExternalEndpointKey")
                    : null;
            ListTag endpoints = tag.getList("Endpoints", Tag.TAG_COMPOUND);
            for (Tag entry : endpoints) {
                CompoundTag endpointTag = (CompoundTag) entry;
                state.endpointSlots.put(endpointTag.getString("Key"), endpointTag.getInt("Slots"));
            }
            return state;
        }

        private CompoundTag save(int bindingId) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("BindingId", bindingId);
            tag.putInt("StorageSlots", storageSlots);
            tag.putInt("Version", version);
            tag.put("Handler", handlerTag.copy());
            if (boundInternalEndpointKey != null && !boundInternalEndpointKey.isEmpty()) {
                tag.putString("BoundInternalEndpointKey", boundInternalEndpointKey);
            }
            if (boundExternalEndpointKey != null && !boundExternalEndpointKey.isEmpty()) {
                tag.putString("BoundExternalEndpointKey", boundExternalEndpointKey);
            }
            ListTag endpoints = new ListTag();
            for (Map.Entry<String, Integer> entry : endpointSlots.entrySet()) {
                CompoundTag endpointTag = new CompoundTag();
                endpointTag.putString("Key", entry.getKey());
                endpointTag.putInt("Slots", entry.getValue());
                endpoints.add(endpointTag);
            }
            tag.put("Endpoints", endpoints);
            return tag;
        }

        private int getCurrentCapacity() {
            return endpointSlots.values().stream().mapToInt(Integer::intValue).sum();
        }

        private ItemStackHandler createHandlerCopy(HolderLookup.Provider registries) {
            int size = Math.max(storageSlots, getCurrentCapacity());
            ItemStackHandler handler = new ItemStackHandler(size);
            if (!handlerTag.isEmpty()) {
                handler.deserializeNBT(registries, handlerTag.copy());
            }
            return handler;
        }

        private void ensureStorageSlots(int targetSlots, HolderLookup.Provider registries) {
            int desired = Math.max(storageSlots, targetSlots);
            if (desired == storageSlots) {
                return;
            }
            ItemStackHandler copy = createHandlerCopy(registries);
            ItemStackHandler resized = new ItemStackHandler(desired);
            for (int slot = 0; slot < Math.min(copy.getSlots(), desired); slot++) {
                resized.setStackInSlot(slot, copy.getStackInSlot(slot).copy());
            }
            storageSlots = desired;
            handlerTag = resized.serializeNBT(registries);
        }

        private void writeFrom(ItemStackHandler handler, HolderLookup.Provider registries) {
            storageSlots = handler.getSlots();
            handlerTag = handler.serializeNBT(registries);
        }

        private ItemStorageSnapshot snapshot(HolderLookup.Provider registries) {
            return new ItemStorageSnapshot(createHandlerCopy(registries), version, getCurrentCapacity());
        }
    }

    private static final class FluidStorageState {
        private final Map<String, Integer> endpointCapacityMb = new LinkedHashMap<>();
        private FluidStack fluid = FluidStack.EMPTY;
        private @Nullable String boundInternalEndpointKey;
        private @Nullable String boundExternalEndpointKey;
        private int version = 0;

        private static FluidStorageState load(CompoundTag tag, HolderLookup.Provider registries) {
            FluidStorageState state = new FluidStorageState();
            state.version = Math.max(0, tag.getInt("Version"));
            state.boundInternalEndpointKey = tag.contains("BoundInternalEndpointKey", Tag.TAG_STRING)
                    ? tag.getString("BoundInternalEndpointKey")
                    : null;
            state.boundExternalEndpointKey = tag.contains("BoundExternalEndpointKey", Tag.TAG_STRING)
                    ? tag.getString("BoundExternalEndpointKey")
                    : null;
            if (tag.contains("Fluid", Tag.TAG_COMPOUND)) {
                state.fluid = FluidStack.parseOptional(registries, tag.getCompound("Fluid"));
            }
            ListTag endpoints = tag.getList("Endpoints", Tag.TAG_COMPOUND);
            for (Tag entry : endpoints) {
                CompoundTag endpointTag = (CompoundTag) entry;
                state.endpointCapacityMb.put(endpointTag.getString("Key"), endpointTag.getInt("Capacity"));
            }
            return state;
        }

        private CompoundTag save(int bindingId, HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("BindingId", bindingId);
            tag.putInt("Version", version);
            tag.put("Fluid", fluid.saveOptional(registries));
            if (boundInternalEndpointKey != null && !boundInternalEndpointKey.isEmpty()) {
                tag.putString("BoundInternalEndpointKey", boundInternalEndpointKey);
            }
            if (boundExternalEndpointKey != null && !boundExternalEndpointKey.isEmpty()) {
                tag.putString("BoundExternalEndpointKey", boundExternalEndpointKey);
            }
            ListTag endpoints = new ListTag();
            for (Map.Entry<String, Integer> entry : endpointCapacityMb.entrySet()) {
                CompoundTag endpointTag = new CompoundTag();
                endpointTag.putString("Key", entry.getKey());
                endpointTag.putInt("Capacity", entry.getValue());
                endpoints.add(endpointTag);
            }
            tag.put("Endpoints", endpoints);
            return tag;
        }

        private int getCurrentCapacityMb() {
            return endpointCapacityMb.values().stream().mapToInt(Integer::intValue).sum();
        }

        private FluidStorageSnapshot snapshot() {
            return new FluidStorageSnapshot(fluid.copy(), version, getCurrentCapacityMb());
        }
    }

    private static final class PortalChannelState {
        private @Nullable ResourceLocation internalDimension;
        private BlockPos internalPos = BlockPos.ZERO;
        private boolean hasInternal;
        private @Nullable ResourceLocation externalDimension;
        private BlockPos externalPos = BlockPos.ZERO;
        private boolean hasExternal;

        private static PortalChannelState load(CompoundTag tag) {
            PortalChannelState state = new PortalChannelState();
            state.hasInternal = tag.getBoolean("HasInternal");
            state.hasExternal = tag.getBoolean("HasExternal");
            if (tag.contains("InternalDimension", Tag.TAG_STRING)) {
                state.internalDimension = ResourceLocation.parse(tag.getString("InternalDimension"));
            }
            if (tag.contains("ExternalDimension", Tag.TAG_STRING)) {
                state.externalDimension = ResourceLocation.parse(tag.getString("ExternalDimension"));
            }
            state.internalPos = new BlockPos(tag.getInt("InternalX"), tag.getInt("InternalY"), tag.getInt("InternalZ"));
            state.externalPos = new BlockPos(tag.getInt("ExternalX"), tag.getInt("ExternalY"), tag.getInt("ExternalZ"));
            return state;
        }

        private CompoundTag save(int factoryId) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("FactoryId", factoryId);
            tag.putBoolean("HasInternal", hasInternal);
            tag.putBoolean("HasExternal", hasExternal);
            if (internalDimension != null) {
                tag.putString("InternalDimension", internalDimension.toString());
            }
            if (externalDimension != null) {
                tag.putString("ExternalDimension", externalDimension.toString());
            }
            tag.putInt("InternalX", internalPos.getX());
            tag.putInt("InternalY", internalPos.getY());
            tag.putInt("InternalZ", internalPos.getZ());
            tag.putInt("ExternalX", externalPos.getX());
            tag.putInt("ExternalY", externalPos.getY());
            tag.putInt("ExternalZ", externalPos.getZ());
            return tag;
        }

        private boolean set(PortalEndpoint endpoint, ResourceLocation dimension, BlockPos pos) {
            BlockPos immutablePos = pos.immutable();
            return switch (endpoint) {
                case INTERNAL -> {
                    boolean changed = !hasInternal || !immutablePos.equals(internalPos) || !java.util.Objects.equals(internalDimension, dimension);
                    hasInternal = true;
                    internalDimension = dimension;
                    internalPos = immutablePos;
                    yield changed;
                }
                case EXTERNAL -> {
                    boolean changed = !hasExternal || !immutablePos.equals(externalPos) || !java.util.Objects.equals(externalDimension, dimension);
                    hasExternal = true;
                    externalDimension = dimension;
                    externalPos = immutablePos;
                    yield changed;
                }
            };
        }

        private boolean clearIfMatches(PortalEndpoint endpoint, ResourceLocation dimension, BlockPos pos) {
            return switch (endpoint) {
                case INTERNAL -> {
                    if (!hasInternal || internalDimension == null || !internalDimension.equals(dimension) || !internalPos.equals(pos)) {
                        yield false;
                    }
                    hasInternal = false;
                    internalDimension = null;
                    internalPos = BlockPos.ZERO;
                    yield true;
                }
                case EXTERNAL -> {
                    if (!hasExternal || externalDimension == null || !externalDimension.equals(dimension) || !externalPos.equals(pos)) {
                        yield false;
                    }
                    hasExternal = false;
                    externalDimension = null;
                    externalPos = BlockPos.ZERO;
                    yield true;
                }
            };
        }

        private @Nullable PortalEndpointRecord get(PortalEndpoint endpoint) {
            return switch (endpoint) {
                case INTERNAL -> hasInternal && internalDimension != null ? new PortalEndpointRecord(internalDimension, internalPos) : null;
                case EXTERNAL -> hasExternal && externalDimension != null ? new PortalEndpointRecord(externalDimension, externalPos) : null;
            };
        }
    }

    public record FactoryChunkOffset(int x, int z) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("ChunkX", x);
            tag.putInt("ChunkZ", z);
            return tag;
        }

        private static FactoryChunkOffset load(CompoundTag tag) {
            return new FactoryChunkOffset(tag.getInt("ChunkX"), tag.getInt("ChunkZ"));
        }
    }

    public record FactoryRecord(int id, @Nullable UUID owner, int slotX, int slotZ, Set<FactoryChunkOffset> occupiedChunks,
                                int minY, int maxY, @Nullable ResourceLocation entranceDimension, BlockPos entrancePos,
                                boolean hasEntrance) {
        public FactoryRecord {
            occupiedChunks = Collections.unmodifiableSet(new LinkedHashSet<>(occupiedChunks));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Id", id);
            if (owner != null) {
                tag.putUUID("Owner", owner);
            }
            tag.putInt("SlotX", slotX);
            tag.putInt("SlotZ", slotZ);
            tag.putInt("MinY", minY);
            tag.putInt("MaxY", maxY);
            tag.putBoolean("HasEntrance", hasEntrance);
            if (entranceDimension != null) {
                tag.putString("EntranceDimension", entranceDimension.toString());
            }
            tag.putInt("EntranceX", entrancePos.getX());
            tag.putInt("EntranceY", entrancePos.getY());
            tag.putInt("EntranceZ", entrancePos.getZ());

            ListTag occupiedChunksTag = new ListTag();
            for (FactoryChunkOffset chunkOffset : occupiedChunks) {
                occupiedChunksTag.add(chunkOffset.save());
            }
            tag.put("OccupiedChunks", occupiedChunksTag);
            return tag;
        }

        private static FactoryRecord load(CompoundTag tag) {
            UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            ResourceLocation entranceDimension = tag.contains("EntranceDimension", Tag.TAG_STRING)
                    ? ResourceLocation.parse(tag.getString("EntranceDimension"))
                    : null;
            BlockPos entrancePos = new BlockPos(tag.getInt("EntranceX"), tag.getInt("EntranceY"), tag.getInt("EntranceZ"));
            Set<FactoryChunkOffset> occupiedChunks = new LinkedHashSet<>();
            ListTag occupiedChunksTag = tag.getList("OccupiedChunks", Tag.TAG_COMPOUND);
            for (Tag entry : occupiedChunksTag) {
                occupiedChunks.add(FactoryChunkOffset.load((CompoundTag) entry));
            }

            return new FactoryRecord(
                    tag.getInt("Id"),
                    owner,
                    tag.getInt("SlotX"),
                    tag.getInt("SlotZ"),
                    occupiedChunks,
                    tag.getInt("MinY"),
                    tag.getInt("MaxY"),
                    entranceDimension,
                    entrancePos,
                    tag.getBoolean("HasEntrance")
            );
        }

        public FactoryRecord withEntrance(@Nullable ResourceLocation dimension, BlockPos pos, boolean present) {
            return new FactoryRecord(id, owner, slotX, slotZ, occupiedChunks, minY, maxY, dimension, pos.immutable(), present);
        }

        public boolean containsChunk(int chunkX, int chunkZ) {
            return occupiedChunks.contains(new FactoryChunkOffset(chunkX, chunkZ));
        }

        public FactoryRecord withAddedChunk(int chunkX, int chunkZ) {
            Set<FactoryChunkOffset> updatedChunks = new LinkedHashSet<>(occupiedChunks);
            updatedChunks.add(new FactoryChunkOffset(chunkX, chunkZ));
            return new FactoryRecord(id, owner, slotX, slotZ, updatedChunks, minY, maxY, entranceDimension, entrancePos, hasEntrance);
        }

        public FactoryRecord withVerticalBounds(int updatedMinY, int updatedMaxY) {
            return new FactoryRecord(id, owner, slotX, slotZ, occupiedChunks, updatedMinY, updatedMaxY, entranceDimension, entrancePos, hasEntrance);
        }
    }

    private static final class ChuteBridgeState {
        private ItemStack pendingStack = ItemStack.EMPTY;
        private @Nullable String targetEndpointKey;
        private int version;

        private static ChuteBridgeState load(CompoundTag tag, HolderLookup.Provider registries) {
            ChuteBridgeState state = new ChuteBridgeState();
            state.pendingStack = ItemStack.parseOptional(registries, tag.getCompound("PendingStack"));
            state.targetEndpointKey = tag.contains("TargetEndpointKey", Tag.TAG_STRING) ? tag.getString("TargetEndpointKey") : null;
            state.version = tag.getInt("Version");
            return state;
        }

        private CompoundTag save(int bindingId, HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("BindingId", bindingId);
            tag.put("PendingStack", pendingStack.saveOptional(registries));
            if (targetEndpointKey != null && !targetEndpointKey.isEmpty()) {
                tag.putString("TargetEndpointKey", targetEndpointKey);
            }
            tag.putInt("Version", version);
            return tag;
        }

        private ChuteBridgeSnapshot snapshot(HolderLookup.Provider registries) {
            return new ChuteBridgeSnapshot(pendingStack.copy(), targetEndpointKey, version);
        }

        private boolean offer(String targetEndpointKey, ItemStack stack, HolderLookup.Provider registries) {
            if (!pendingStack.isEmpty()) {
                return false;
            }
            pendingStack = stack.copy();
            this.targetEndpointKey = targetEndpointKey;
            version++;
            return true;
        }

        private ItemStack poll(String endpointKey, HolderLookup.Provider registries) {
            if (pendingStack.isEmpty() || targetEndpointKey == null || !targetEndpointKey.equals(endpointKey)) {
                return ItemStack.EMPTY;
            }
            ItemStack result = pendingStack.copy();
            pendingStack = ItemStack.EMPTY;
            targetEndpointKey = null;
            version++;
            return result;
        }
    }

    private static final class PumpBridgeState {
        private final Map<String, FluidStack> channels = new LinkedHashMap<>();
        private int version;

        private static PumpBridgeState load(CompoundTag tag, HolderLookup.Provider registries) {
            PumpBridgeState state = new PumpBridgeState();
            ListTag channelEntries = tag.getList("Channels", Tag.TAG_COMPOUND);
            for (Tag entry : channelEntries) {
                CompoundTag channelTag = (CompoundTag) entry;
                if (!channelTag.contains("SourceEndpointKey", Tag.TAG_STRING)) {
                    continue;
                }
                String sourceEndpointKey = channelTag.getString("SourceEndpointKey");
                if (sourceEndpointKey.isEmpty()) {
                    continue;
                }
                FluidStack fluid = FluidStack.parseOptional(registries, channelTag.getCompound("Fluid"));
                if (!fluid.isEmpty()) {
                    state.channels.put(sourceEndpointKey, fluid);
                }
            }

            if (state.channels.isEmpty()) {
                FluidStack legacyFluid = FluidStack.parseOptional(registries, tag.getCompound("Fluid"));
                String legacySourceEndpointKey = tag.contains("SourceEndpointKey", Tag.TAG_STRING) ? tag.getString("SourceEndpointKey") : "";
                if (!legacyFluid.isEmpty() && !legacySourceEndpointKey.isEmpty()) {
                    state.channels.put(legacySourceEndpointKey, legacyFluid);
                }
            }
            state.version = tag.getInt("Version");
            return state;
        }

        private CompoundTag save(int bindingId, HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("BindingId", bindingId);
            ListTag channelsTag = new ListTag();
            for (Map.Entry<String, FluidStack> entry : channels.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                CompoundTag channelTag = new CompoundTag();
                channelTag.putString("SourceEndpointKey", entry.getKey());
                channelTag.put("Fluid", entry.getValue().saveOptional(registries));
                channelsTag.add(channelTag);
            }
            tag.put("Channels", channelsTag);
            tag.putInt("Version", version);
            return tag;
        }

        private FluidStack peekForRequester(@Nullable String requesterEndpointKey) {
            if (requesterEndpointKey == null || requesterEndpointKey.isEmpty()) {
                return FluidStack.EMPTY;
            }
            for (Map.Entry<String, FluidStack> entry : channels.entrySet()) {
                if (!entry.getKey().equals(requesterEndpointKey) && !entry.getValue().isEmpty()) {
                    return entry.getValue().copy();
                }
            }
            return FluidStack.EMPTY;
        }

        private FluidStack peekForSource(String sourceEndpointKey) {
            FluidStack fluid = channels.get(sourceEndpointKey);
            return fluid == null ? FluidStack.EMPTY : fluid.copy();
        }

        private int getAmountForSource(String sourceEndpointKey) {
            FluidStack fluid = channels.get(sourceEndpointKey);
            return fluid == null ? 0 : fluid.getAmount();
        }

        private void fillFromSource(String sourceEndpointKey, FluidStack stack, int accepted) {
            FluidStack existing = channels.get(sourceEndpointKey);
            if (existing == null || existing.isEmpty()) {
                FluidStack stored = stack.copy();
                stored.setAmount(accepted);
                channels.put(sourceEndpointKey, stored);
                return;
            }
            existing.grow(accepted);
        }

        private FluidStack drainForRequester(@Nullable String requesterEndpointKey, int amount) {
            if (requesterEndpointKey == null || requesterEndpointKey.isEmpty()) {
                return FluidStack.EMPTY;
            }
            java.util.Iterator<Map.Entry<String, FluidStack>> iterator = channels.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, FluidStack> entry = iterator.next();
                if (entry.getKey().equals(requesterEndpointKey) || entry.getValue().isEmpty()) {
                    continue;
                }
                FluidStack drained = entry.getValue().copy();
                drained.setAmount(Math.min(amount, entry.getValue().getAmount()));
                entry.getValue().shrink(drained.getAmount());
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
                return drained;
            }
            return FluidStack.EMPTY;
        }
    }

    private static final class ClutchBridgeState {
        private final EnumMap<EndpointRole, ClutchEndpointSnapshot> endpoints = new EnumMap<>(EndpointRole.class);
        private int version;

        private void updateEndpoint(EndpointRole role, boolean powered, @Nullable ClutchDriveState driveState) {
            ClutchEndpointSnapshot previous = endpoints.get(role);
            ClutchEndpointSnapshot next = new ClutchEndpointSnapshot(powered, driveState);
            if (next.equals(previous)) {
                return;
            }
            endpoints.put(role, next);
            version++;
        }

        private boolean clearEndpoint(EndpointRole role) {
            ClutchEndpointSnapshot removed = endpoints.remove(role);
            if (removed == null) {
                return false;
            }
            version++;
            return true;
        }

        private boolean isEmpty() {
            return endpoints.isEmpty();
        }

        private ClutchBridgeSnapshot snapshot() {
            return new ClutchBridgeSnapshot(
                endpoints.get(EndpointRole.INTERNAL),
                endpoints.get(EndpointRole.EXTERNAL),
                version);
        }
    }
}
