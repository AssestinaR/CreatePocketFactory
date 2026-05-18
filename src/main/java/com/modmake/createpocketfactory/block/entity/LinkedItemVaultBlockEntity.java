package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.block.LinkedItemVaultBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.ICapabilityProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryWrapper;
import com.simibubi.create.foundation.utility.SameSizeCombinedInvWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.modmake.createpocketfactory.world.LinkedStorageManualBindingHelper;

import java.util.ArrayList;
import java.util.List;

public class LinkedItemVaultBlockEntity extends ItemVaultBlockEntity implements IHaveGoggleInformation {
    private static final String BINDING_ID_TAG = "LinkedBindingId";
    private static final String FACTORY_ID_TAG = "LinkedFactoryId";
    private static final String CHUNK_X_TAG = "LinkedChunkX";
    private static final String CHUNK_Z_TAG = "LinkedChunkZ";
    private static final String TOOLTIP_CAPACITY_TAG = "TooltipCapacitySlots";
    private static final String TOOLTIP_USED_TAG = "TooltipUsedSlots";
    private static final String TOOLTIP_TYPES_TAG = "TooltipItemTypes";
    private static final String TOOLTIP_TOTAL_ITEMS_TAG = "TooltipTotalItems";
    private static final String TOOLTIP_PREVIEW_TAG = "TooltipPreview";
    private static final int MAX_TOOLTIP_PREVIEW_ITEMS = 8;
    private static final int NO_CHUNK = Integer.MIN_VALUE;

    private int bindingId = -1;
    private int factoryId = -1;
    private int boundChunkX = NO_CHUNK;
    private int boundChunkZ = NO_CHUNK;
    private boolean sharedInventoryMerged;
    private int tooltipCapacitySlots;
    private int tooltipUsedSlots;
    private int tooltipItemTypes;
    private int tooltipTotalItems;
    private List<ItemStack> tooltipPreviewStacks = List.of();

    public LinkedItemVaultBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.LINKED_ITEM_VAULT.get(), pos, state);
    }

    public LinkedItemVaultBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LINKED_ITEM_VAULT.get(),
                (be, context) -> {
                    be.initLinkedCapability();
                    if (be.itemCapability == null) {
                        return null;
                    }
                    return be.itemCapability.getCapability();
                }
        );
    }

    public void refreshConnectivityNow() {
        updateConnectivity();
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
            clearTooltipSummary();
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        String endpointKey = LinkedStorageBindingHelper.endpointKey(level, worldPosition);
        if (!savedData.isEndpointBoundToBinding(bindingId, PocketFactorySavedData.BindingChannel.ITEM_STORAGE, endpointKey)) {
            LinkedStorageManualBindingHelper.normalizeOrphanedItemVault(this);
            clearTooltipSummary();
            return;
        }

        int totalSlots = getTotalLocalSlots();
        if (!sharedInventoryMerged) {
            savedData.mergeItemEndpointInventory(bindingId, endpointKey, exportLocalInventory(level.registryAccess()), level.registryAccess());
            clearLocalInventories();
            sharedInventoryMerged = true;
        }
        savedData.registerItemEndpoint(bindingId, endpointKey, totalSlots, level.registryAccess());
        updateTooltipSummary(savedData.getItemSnapshot(bindingId, level.registryAccess()));

        itemCapability = null;
        invalidateCapabilities();
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
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
        compound.putBoolean("SharedMerged", sharedInventoryMerged);
        compound.putInt(TOOLTIP_CAPACITY_TAG, tooltipCapacitySlots);
        compound.putInt(TOOLTIP_USED_TAG, tooltipUsedSlots);
        compound.putInt(TOOLTIP_TYPES_TAG, tooltipItemTypes);
        compound.putInt(TOOLTIP_TOTAL_ITEMS_TAG, tooltipTotalItems);
        ListTag previewTag = new ListTag();
        for (ItemStack stack : tooltipPreviewStacks) {
            previewTag.add(stack.saveOptional(registries));
        }
        compound.put(TOOLTIP_PREVIEW_TAG, previewTag);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        bindingId = compound.contains(BINDING_ID_TAG) ? compound.getInt(BINDING_ID_TAG) : -1;
        factoryId = compound.contains(FACTORY_ID_TAG) ? compound.getInt(FACTORY_ID_TAG) : -1;
        boundChunkX = compound.contains(CHUNK_X_TAG) ? compound.getInt(CHUNK_X_TAG) : NO_CHUNK;
        boundChunkZ = compound.contains(CHUNK_Z_TAG) ? compound.getInt(CHUNK_Z_TAG) : NO_CHUNK;
        sharedInventoryMerged = compound.getBoolean("SharedMerged");
        tooltipCapacitySlots = compound.getInt(TOOLTIP_CAPACITY_TAG);
        tooltipUsedSlots = compound.getInt(TOOLTIP_USED_TAG);
        tooltipItemTypes = compound.getInt(TOOLTIP_TYPES_TAG);
        tooltipTotalItems = compound.getInt(TOOLTIP_TOTAL_ITEMS_TAG);
        List<ItemStack> previewStacks = new ArrayList<>();
        ListTag previewTag = compound.getList(TOOLTIP_PREVIEW_TAG, Tag.TAG_COMPOUND);
        for (Tag entry : previewTag) {
            previewStacks.add(ItemStack.parseOptional(registries, (CompoundTag) entry));
        }
        tooltipPreviewStacks = List.copyOf(previewStacks);
        super.read(compound, registries, clientPacket);
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

    public void applyExistingBinding(LinkedStorageBindingHelper.BindingTarget bindingTarget) {
        applyBinding(bindingTarget);
        sharedInventoryMerged = true;
        clearLocalInventories();
        itemCapability = null;
        invalidateCapabilities();
        setChanged();
        sendData();
    }

    public void clearSharedRegistration(BlockPos dropPos) {
        if (level == null || level.isClientSide || level.getServer() == null || bindingId <= 0 || !isController()) {
            return;
        }
        PocketFactorySavedData.ItemStorageSnapshot snapshot = PocketFactorySavedData.get(level.getServer()).disposeItemBinding(bindingId, level.registryAccess());
        if (snapshot != null) {
            dropSnapshotContents(snapshot, dropPos);
        }
    }

    private void initLinkedCapability() {
        if (itemCapability != null && itemCapability.getCapability() != null) {
            return;
        }

        boolean alongZ = LinkedItemVaultBlock.getVaultAxis(getBlockState()) == Axis.Z;

        if (!isController()) {
            ItemVaultBlockEntity controllerBE = getControllerBE();
            if (!(controllerBE instanceof LinkedItemVaultBlockEntity linkedController)) {
                return;
            }
            linkedController.initLinkedCapability();
            itemCapability = ICapabilityProvider.of(() -> {
                if (linkedController.isRemoved() || linkedController.itemCapability == null) {
                    return null;
                }
                return linkedController.itemCapability.getCapability();
            });
            invId = linkedController.invId;
            return;
        }

        if (bindingId > 0 && level != null && level.getServer() != null) {
            itemCapability = ICapabilityProvider.of(new SharedItemVaultHandler(this));
            BlockPos farCorner = alongZ
                    ? worldPosition.offset(radius, radius, length)
                    : worldPosition.offset(length, radius, radius);
            BoundingBox bounds = BoundingBox.fromCorners(worldPosition, farCorner);
            invId = new InventoryIdentifier.Bounds(bounds);
            return;
        }

        IItemHandlerModifiable[] inventories = new IItemHandlerModifiable[length * radius * radius];
        for (int yOffset = 0; yOffset < length; yOffset++) {
            for (int xOffset = 0; xOffset < radius; xOffset++) {
                for (int zOffset = 0; zOffset < radius; zOffset++) {
                    BlockPos vaultPos = alongZ
                            ? worldPosition.offset(xOffset, zOffset, yOffset)
                            : worldPosition.offset(yOffset, xOffset, zOffset);
                    LinkedItemVaultBlockEntity vaultAt = ConnectivityHandler.partAt(ModBlockEntities.LINKED_ITEM_VAULT.get(), level, vaultPos);
                    inventories[yOffset * radius * radius + xOffset * radius + zOffset] =
                            vaultAt != null ? vaultAt.inventory : new ItemStackHandler();
                }
            }
        }

        itemCapability = ICapabilityProvider.of(new VersionedInventoryWrapper(SameSizeCombinedInvWrapper.create(inventories)));

        BlockPos farCorner = alongZ
                ? worldPosition.offset(radius, radius, length)
                : worldPosition.offset(length, radius, radius);
        BoundingBox bounds = BoundingBox.fromCorners(worldPosition, farCorner);
        invId = new InventoryIdentifier.Bounds(bounds);
    }

    private ItemStackHandler exportLocalInventory(HolderLookup.Provider registries) {
        ItemStackHandler exported = new ItemStackHandler(getTotalLocalSlots());
        int slotIndex = 0;
        for (LinkedItemVaultBlockEntity part : getStructureParts()) {
            for (int slot = 0; slot < part.inventory.getSlots(); slot++) {
                exported.setStackInSlot(slotIndex++, part.inventory.getStackInSlot(slot).copy());
            }
        }
        return exported;
    }

    private void clearLocalInventories() {
        for (LinkedItemVaultBlockEntity part : getStructureParts()) {
            for (int slot = 0; slot < part.inventory.getSlots(); slot++) {
                part.inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private int getTotalLocalSlots() {
        return getStructureParts().size() * inventory.getSlots();
    }

    private List<LinkedItemVaultBlockEntity> getStructureParts() {
        List<LinkedItemVaultBlockEntity> parts = new ArrayList<>();
        boolean alongZ = LinkedItemVaultBlock.getVaultAxis(getBlockState()) == Axis.Z;
        int sizeX = alongZ ? radius : length;
        int sizeY = radius;
        int sizeZ = alongZ ? length : radius;
        LinkedStorageBindingHelper.forEachBox(worldPosition, sizeX, sizeY, sizeZ, pos -> {
            if (level.getBlockEntity(pos) instanceof LinkedItemVaultBlockEntity vault) {
                parts.add(vault);
            }
        });
        return parts;
    }

    @Override
    public void removeController(boolean keepContents) {
        if (level.isClientSide()) {
            return;
        }
        updateConnectivity = true;
        controller = null;
        radius = 1;
        length = 1;

        BlockState state = getBlockState();
        if (LinkedItemVaultBlock.isVault(state)) {
            state = state.setValue(LinkedItemVaultBlock.LARGE, false);
            getLevel().setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
        }

        itemCapability = null;
        invalidateCapabilities();
        setChanged();
        sendData();
    }

    @Override
    public void notifyMultiUpdated() {
        BlockState state = getBlockState();
        if (LinkedItemVaultBlock.isVault(state)) {
            level.setBlock(getBlockPos(), state.setValue(LinkedItemVaultBlock.LARGE, radius > 2), Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        itemCapability = null;
        invalidateCapabilities();
        setChanged();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LinkedItemVaultBlockEntity source = getTooltipSource();
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

        tooltip.add(Component.translatable("goggles.create_pocket_factory.common.used_slots", tooltipUsedSlots, tooltipCapacitySlots)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("goggles.create_pocket_factory.common.total_items", tooltipTotalItems)
                .withStyle(ChatFormatting.GRAY));

        if (tooltipItemTypes <= 0 || tooltipPreviewStacks.isEmpty()) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.empty").withStyle(ChatFormatting.DARK_GRAY));
            return true;
        }

        int previewLimit = isPlayerSneaking ? tooltipPreviewStacks.size() : Math.min(5, tooltipPreviewStacks.size());
        for (int index = 0; index < previewLimit; index++) {
            ItemStack stack = tooltipPreviewStacks.get(index);
            tooltip.add(Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(stack.getHoverName().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GOLD)));
        }

        if (tooltipItemTypes > previewLimit) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.more_types", tooltipItemTypes - previewLimit)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        return true;
    }

    private LinkedItemVaultBlockEntity getTooltipSource() {
        if (isController()) {
            return this;
        }
        ItemVaultBlockEntity controllerBE = getControllerBE();
        return controllerBE instanceof LinkedItemVaultBlockEntity linkedController ? linkedController : this;
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
        itemCapability = null;
        invalidateCapabilities();
        setChanged();
        sendData();
    }

    public void clearBindingForPendingContinuation() {
        if (level == null || level.isClientSide()) {
            return;
        }
        bindingId = -1;
        factoryId = -1;
        boundChunkX = NO_CHUNK;
        boundChunkZ = NO_CHUNK;
        sharedInventoryMerged = false;
        clearTooltipSummaryFields();
        itemCapability = null;
        invalidateCapabilities();
        setChanged();
        sendData();
    }

    private void dropSnapshotContents(PocketFactorySavedData.ItemStorageSnapshot snapshot, BlockPos dropPos) {
        for (int slot = 0; slot < snapshot.handler().getSlots(); slot++) {
            ItemStack stack = snapshot.handler().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, dropPos, stack.copy());
            }
        }
    }

    private void updateTooltipSummary(PocketFactorySavedData.ItemStorageSnapshot snapshot) {
        ItemStackHandler handler = snapshot.handler();
        int usedSlots = 0;
        int itemTypes = 0;
        int totalItems = 0;
        List<ItemStack> previewStacks = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            usedSlots++;
            itemTypes++;
            totalItems += stack.getCount();
            if (previewStacks.size() < MAX_TOOLTIP_PREVIEW_ITEMS) {
                previewStacks.add(stack.copy());
            }
        }

        if (tooltipCapacitySlots == snapshot.currentCapacity()
                && tooltipUsedSlots == usedSlots
                && tooltipItemTypes == itemTypes
                && tooltipTotalItems == totalItems
                && samePreviewStacks(tooltipPreviewStacks, previewStacks)) {
            return;
        }

        tooltipCapacitySlots = snapshot.currentCapacity();
        tooltipUsedSlots = usedSlots;
        tooltipItemTypes = itemTypes;
        tooltipTotalItems = totalItems;
        tooltipPreviewStacks = List.copyOf(previewStacks);
        setChanged();
        sendData();
    }

    private void clearTooltipSummary() {
        if (tooltipCapacitySlots == 0 && tooltipUsedSlots == 0 && tooltipItemTypes == 0 && tooltipTotalItems == 0
                && tooltipPreviewStacks.isEmpty()) {
            return;
        }
        clearTooltipSummaryFields();
        setChanged();
        sendData();
    }

    private void clearTooltipSummaryFields() {
        tooltipCapacitySlots = 0;
        tooltipUsedSlots = 0;
        tooltipItemTypes = 0;
        tooltipTotalItems = 0;
        tooltipPreviewStacks = List.of();
    }

    private Component bindingLine(boolean bound) {
        return Component.translatable(bound
                        ? "goggles.create_pocket_factory.common.bound"
                        : "goggles.create_pocket_factory.common.unbound")
                .withStyle(bound ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static boolean samePreviewStacks(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ItemStack leftStack = left.get(index);
            ItemStack rightStack = right.get(index);
            if (!ItemStack.isSameItemSameComponents(leftStack, rightStack) || leftStack.getCount() != rightStack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static final class SharedItemVaultHandler implements IItemHandlerModifiable {
        private final LinkedItemVaultBlockEntity vault;

        private SharedItemVaultHandler(LinkedItemVaultBlockEntity vault) {
            this.vault = vault;
        }

        @Override
        public int getSlots() {
            return snapshot().currentCapacity();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStackHandler handler = snapshot().handler();
            if (slot < 0 || slot >= handler.getSlots()) {
                return ItemStack.EMPTY;
            }
            return handler.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            PocketFactorySavedData.ItemStorageSnapshot snapshot = snapshot();
            ItemStackHandler handler = snapshot.handler();
            if (slot < 0 || slot >= handler.getSlots()) {
                return stack;
            }
            if (slot >= snapshot.currentCapacity() && handler.getStackInSlot(slot).isEmpty()) {
                return stack;
            }
            ItemStack remaining = handler.insertItem(slot, stack, simulate);
            if (!simulate && remaining.getCount() != stack.getCount()) {
                save(handler);
            }
            return remaining;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            PocketFactorySavedData.ItemStorageSnapshot snapshot = snapshot();
            ItemStackHandler handler = snapshot.handler();
            if (slot < 0 || slot >= handler.getSlots()) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = handler.extractItem(slot, amount, simulate);
            if (!simulate && !extracted.isEmpty()) {
                save(handler);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return snapshot().handler().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            PocketFactorySavedData.ItemStorageSnapshot snapshot = snapshot();
            ItemStackHandler handler = snapshot.handler();
            if (slot < 0 || slot >= handler.getSlots()) {
                return false;
            }
            return slot < snapshot.currentCapacity() || !handler.getStackInSlot(slot).isEmpty();
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            PocketFactorySavedData.ItemStorageSnapshot snapshot = snapshot();
            ItemStackHandler handler = snapshot.handler();
            if (slot < 0 || slot >= handler.getSlots()) {
                return;
            }
            if (slot >= snapshot.currentCapacity() && handler.getStackInSlot(slot).isEmpty() && !stack.isEmpty()) {
                return;
            }
            handler.setStackInSlot(slot, stack);
            save(handler);
        }

        private PocketFactorySavedData.ItemStorageSnapshot snapshot() {
            return PocketFactorySavedData.get(vault.level.getServer()).getItemSnapshot(vault.bindingId, vault.level.registryAccess());
        }

        private void save(ItemStackHandler handler) {
            PocketFactorySavedData.get(vault.level.getServer()).saveItemSnapshot(vault.bindingId, handler, vault.level.registryAccess());
        }
    }
}
