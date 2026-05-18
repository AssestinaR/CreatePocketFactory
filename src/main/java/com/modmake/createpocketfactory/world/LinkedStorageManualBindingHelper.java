package com.modmake.createpocketfactory.world;

import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.block.entity.BindingEndpointHelper;
import com.modmake.createpocketfactory.block.entity.LinkedFluidTankBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedItemVaultBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedStorageBindingHelper;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class LinkedStorageManualBindingHelper {
    private static final Set<String> COLLAPSING_POSITIONS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> PENDING_TRIGGER_REMOVALS = Collections.synchronizedSet(new HashSet<>());

    private LinkedStorageManualBindingHelper() {
    }

    public static @Nullable StorageKind getStorageKind(BlockState state) {
        if (AllBlocks.ITEM_VAULT.has(state) || state.is(ModBlocks.LINKED_ITEM_VAULT.get())) {
            return StorageKind.ITEM_VAULT;
        }
        if (AllBlocks.FLUID_TANK.has(state) || state.is(ModBlocks.LINKED_FLUID_TANK.get())) {
            return StorageKind.FLUID_TANK;
        }
        return null;
    }

    public static boolean isNormalStorage(BlockState state) {
        StorageKind kind = getStorageKind(state);
        return kind != null && kind.normalBlock() == state.getBlock();
    }

    public static boolean canBindAt(Level level, BlockPos origin, StorageKind kind) {
        if (!kind.matchesNormal(level.getBlockState(origin))) {
            return false;
        }

        BlockPos capabilityPos = resolveCapabilityPos(level, origin, kind);
        return capabilityPos != null && isStructureEmpty(level, capabilityPos, kind);
    }

    public static boolean bind(ServerLevel level, BlockPos origin, StorageKind kind,
                               LinkedStorageBindingHelper.BindingTarget bindingTarget) {
        if (!kind.matchesNormal(level.getBlockState(origin))) {
            return false;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        boolean internalEndpoint = bindingTarget.chunkOffset() != null;

        BlockPos capabilityPos = resolveCapabilityPos(level, origin, kind);
        if (capabilityPos == null || !isStructureEmpty(level, capabilityPos, kind)) {
            return false;
        }

        Set<BlockPos> connected = collectConnected(level, origin, kind.normalBlock());
        if (connected.isEmpty()) {
            return false;
        }

        convertCluster(level, connected, kind, true);

        String endpointKey;
        int bindingId;
        switch (kind) {
            case ITEM_VAULT -> {
                LinkedItemVaultBlockEntity controller = resolveItemController(level, connected);
                if (controller == null) {
                    convertCluster(level, connected, kind, false);
                    return false;
                }
                endpointKey = LinkedStorageBindingHelper.endpointKey(level, controller.getBlockPos());
                bindingId = bindingTarget.bindingId() > 0
                        ? savedData.bindEndpointToBinding(bindingTarget.bindingId(), bindingTarget.factoryId(), kind.bindingChannel(),
                        internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL,
                        endpointKey)
                        : savedData.bindEndpoint(bindingTarget.factoryId(), kind.bindingChannel(),
                        internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL,
                        endpointKey);
                if (bindingId <= 0) {
                    convertCluster(level, connected, kind, false);
                    return false;
                }
                controller.applyExistingBinding(new LinkedStorageBindingHelper.BindingTarget(bindingId, bindingTarget.factoryId(), bindingTarget.chunkOffset()));
            }
            case FLUID_TANK -> {
                LinkedFluidTankBlockEntity controller = resolveFluidController(level, connected);
                if (controller == null) {
                    convertCluster(level, connected, kind, false);
                    return false;
                }
                endpointKey = LinkedStorageBindingHelper.endpointKey(level, controller.getBlockPos());
                bindingId = bindingTarget.bindingId() > 0
                        ? savedData.bindEndpointToBinding(bindingTarget.bindingId(), bindingTarget.factoryId(), kind.bindingChannel(),
                        internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL,
                        endpointKey)
                        : savedData.bindEndpoint(bindingTarget.factoryId(), kind.bindingChannel(),
                        internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL,
                        endpointKey);
                if (bindingId <= 0) {
                    convertCluster(level, connected, kind, false);
                    return false;
                }
                controller.applyExistingBinding(
                    new LinkedStorageBindingHelper.BindingTarget(bindingId, bindingTarget.factoryId(), bindingTarget.chunkOffset()),
                    savedData.getFluidSnapshot(bindingId)
                );
            }
        }

        return true;
    }

    public static void unlinkItemVault(LinkedItemVaultBlockEntity controller, BlockPos triggerPos) {
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || !controller.isController()) {
            return;
        }
        handleBoundStorageRemoval((ServerLevel) controller.getLevel(), controller.getBlockPos(), triggerPos,
                StorageKind.ITEM_VAULT, controller.getBindingId(), false);
    }

    public static void unlinkFluidTank(LinkedFluidTankBlockEntity controller, BlockPos triggerPos) {
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || !controller.isController()) {
            return;
        }
        handleBoundStorageRemoval((ServerLevel) controller.getLevel(), controller.getBlockPos(), triggerPos,
                StorageKind.FLUID_TANK, controller.getBindingId(), false);
    }

    public static boolean isStructureCollapsing(Level level, BlockPos pos) {
        return COLLAPSING_POSITIONS.contains(collapseKey(level, pos));
    }

    public static void markPendingTriggerRemoval(Level level, BlockPos pos) {
        PENDING_TRIGGER_REMOVALS.add(collapseKey(level, pos));
    }

    public static boolean consumePendingTriggerRemoval(Level level, BlockPos pos) {
        return PENDING_TRIGGER_REMOVALS.remove(collapseKey(level, pos));
    }

    public static void collapseItemVaultFromPlayer(LinkedItemVaultBlockEntity controller, BlockPos triggerPos) {
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || !controller.isController()) {
            return;
        }
        handleBoundStorageRemoval((ServerLevel) controller.getLevel(), controller.getBlockPos(), triggerPos,
                StorageKind.ITEM_VAULT, controller.getBindingId(), true);
    }

    public static void collapseFluidTankFromPlayer(LinkedFluidTankBlockEntity controller, BlockPos triggerPos) {
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || !controller.isController()) {
            return;
        }
        handleBoundStorageRemoval((ServerLevel) controller.getLevel(), controller.getBlockPos(), triggerPos,
                StorageKind.FLUID_TANK, controller.getBindingId(), true);
    }

    public static void normalizeOrphanedItemVault(LinkedItemVaultBlockEntity controller) {
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || !controller.isController()) {
            return;
        }
        normalizeStructure((ServerLevel) controller.getLevel(), controller.getBlockPos(), StorageKind.ITEM_VAULT);
    }

    public static void normalizeOrphanedFluidTank(LinkedFluidTankBlockEntity controller) {
        if (controller.getLevel() == null || controller.getLevel().isClientSide() || !controller.isController()) {
            return;
        }
        normalizeStructure((ServerLevel) controller.getLevel(), controller.getBlockPos(), StorageKind.FLUID_TANK);
    }

    private static @Nullable BlockPos resolveCapabilityPos(Level level, BlockPos origin, StorageKind kind) {
        BlockEntity blockEntity = level.getBlockEntity(origin);
        return switch (kind) {
            case ITEM_VAULT -> {
                if (blockEntity instanceof ItemVaultBlockEntity vault) {
                    ItemVaultBlockEntity controller = vault.getControllerBE();
                    yield controller != null ? controller.getBlockPos() : origin;
                }
                yield origin;
            }
            case FLUID_TANK -> {
                if (blockEntity instanceof FluidTankBlockEntity tank) {
                    FluidTankBlockEntity controller = tank.getControllerBE();
                    yield controller != null ? controller.getBlockPos() : origin;
                }
                yield origin;
            }
        };
    }

    private static boolean isStructureEmpty(Level level, BlockPos pos, StorageKind kind) {
        return switch (kind) {
            case ITEM_VAULT -> {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler == null) {
                    yield false;
                }
                boolean empty = true;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (!handler.getStackInSlot(slot).isEmpty()) {
                        empty = false;
                        break;
                    }
                }
                yield empty;
            }
            case FLUID_TANK -> {
                IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
                if (handler == null) {
                    yield false;
                }
                boolean empty = true;
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    if (!handler.getFluidInTank(tank).isEmpty()) {
                        empty = false;
                        break;
                    }
                }
                yield empty;
            }
        };
    }

    private static void handleBoundStorageRemoval(ServerLevel level, BlockPos controllerPos, BlockPos triggerPos,
                                                  StorageKind kind, int bindingId, boolean triggerDestroyedSeparately) {
        Set<BlockPos> localPositions = collectControlledStructure(level, controllerPos, kind);
        if (localPositions.isEmpty() && level.getBlockState(triggerPos).is(kind.linkedBlock())) {
            localPositions.add(triggerPos.immutable());
        }

        StructureTarget remoteTarget = resolveRemoteStructure(level, bindingId, kind, controllerPos);
        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());

        switch (kind) {
            case ITEM_VAULT -> {
                PocketFactorySavedData.ItemStorageSnapshot snapshot = bindingId > 0
                        ? savedData.disposeItemBinding(bindingId, level.registryAccess())
                        : null;
                clearBindingState(level, localPositions, kind);
                if (remoteTarget != null) {
                    restoreItemStructure(remoteTarget, snapshot);
                } else if (snapshot != null) {
                    dropItemSnapshot(level, triggerPos, snapshot);
                }
            }
            case FLUID_TANK -> {
                PocketFactorySavedData.FluidStorageSnapshot snapshot = bindingId > 0
                        ? savedData.disposeFluidBinding(bindingId)
                        : null;
                clearBindingState(level, localPositions, kind);
                if (remoteTarget != null) {
                    restoreFluidStructure(remoteTarget, snapshot);
                }
            }
        }

        if (triggerDestroyedSeparately) {
            localPositions.remove(triggerPos);
        }
        destroyStructure(level, localPositions, kind, triggerPos);
    }

    private static void destroyStructure(ServerLevel level, Set<BlockPos> positions, StorageKind kind, BlockPos triggerPos) {
        if (positions.isEmpty()) {
            return;
        }

        markCollapsing(level, positions, true);
        try {
            for (BlockPos pos : positions) {
                if (pos.equals(triggerPos)) {
                    continue;
                }
                if (!level.getBlockState(pos).is(kind.linkedBlock())) {
                    continue;
                }
                level.destroyBlock(pos, true);
            }
        } finally {
            markCollapsing(level, positions, false);
        }
    }

    private static void clearBindingState(ServerLevel level, Set<BlockPos> positions, StorageKind kind) {
        switch (kind) {
            case ITEM_VAULT -> {
                for (BlockPos pos : positions) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof LinkedItemVaultBlockEntity linked) {
                        linked.clearBindingForPendingContinuation();
                    }
                }
            }
            case FLUID_TANK -> {
                for (BlockPos pos : positions) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof LinkedFluidTankBlockEntity linked) {
                        linked.clearBindingForPendingContinuation();
                    }
                }
            }
        }
    }

    private static @Nullable StructureTarget resolveRemoteStructure(ServerLevel level, int bindingId, StorageKind kind, BlockPos localControllerPos) {
        if (bindingId <= 0) {
            return null;
        }

        PocketFactorySavedData.BindingEndpoints endpoints = PocketFactorySavedData.get(level.getServer())
                .getBindingEndpoints(bindingId, kind.bindingChannel());
        if (endpoints == null) {
            return null;
        }

        String localEndpointKey = LinkedStorageBindingHelper.endpointKey(level, localControllerPos);
        String remoteEndpointKey = BindingEndpointHelper.resolveOppositeEndpointKey(endpoints, localEndpointKey);
        if (remoteEndpointKey == null || remoteEndpointKey.equals(localEndpointKey)) {
            return null;
        }

        LinkedStorageBindingHelper.EndpointLocation endpointLocation = LinkedStorageBindingHelper.parseEndpointKey(remoteEndpointKey);
        if (endpointLocation == null) {
            return null;
        }

        ServerLevel remoteLevel = level.getServer().getLevel(endpointLocation.dimension());
        if (remoteLevel == null) {
            return null;
        }

        Set<BlockPos> positions = collectControlledStructure(remoteLevel, endpointLocation.pos(), kind);
        if (positions.isEmpty() && remoteLevel.getBlockState(endpointLocation.pos()).is(kind.linkedBlock())) {
            positions.add(endpointLocation.pos().immutable());
        }
        return positions.isEmpty() ? null : new StructureTarget(remoteLevel, endpointLocation.pos().immutable(), positions);
    }

    private static void restoreItemStructure(StructureTarget target, @Nullable PocketFactorySavedData.ItemStorageSnapshot snapshot) {
        markCollapsing(target.level(), target.positions(), true);
        try {
            clearBindingState(target.level(), target.positions(), StorageKind.ITEM_VAULT);
            convertCluster(target.level(), target.positions(), StorageKind.ITEM_VAULT, false);
            if (snapshot != null) {
                restoreItemSnapshot(target.level(), target.controllerPos(), snapshot);
            }
        } finally {
            markCollapsing(target.level(), target.positions(), false);
        }
    }

    private static void restoreFluidStructure(StructureTarget target, @Nullable PocketFactorySavedData.FluidStorageSnapshot snapshot) {
        markCollapsing(target.level(), target.positions(), true);
        try {
            clearBindingState(target.level(), target.positions(), StorageKind.FLUID_TANK);
            convertCluster(target.level(), target.positions(), StorageKind.FLUID_TANK, false);
            if (snapshot != null) {
                restoreFluidSnapshot(target.level(), target.controllerPos(), snapshot);
            }
        } finally {
            markCollapsing(target.level(), target.positions(), false);
        }
    }

    private static void restoreItemSnapshot(ServerLevel level, BlockPos controllerPos, PocketFactorySavedData.ItemStorageSnapshot snapshot) {
        BlockPos capabilityPos = resolveCapabilityPos(level, controllerPos, StorageKind.ITEM_VAULT);
        if (capabilityPos == null) {
            dropItemSnapshot(level, controllerPos, snapshot);
            return;
        }

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, capabilityPos, null);
        if (handler == null) {
            dropItemSnapshot(level, controllerPos, snapshot);
            return;
        }

        for (int slot = 0; slot < snapshot.handler().getSlots(); slot++) {
            ItemStack remaining = snapshot.handler().getStackInSlot(slot).copy();
            if (remaining.isEmpty()) {
                continue;
            }
            for (int targetSlot = 0; targetSlot < handler.getSlots() && !remaining.isEmpty(); targetSlot++) {
                remaining = handler.insertItem(targetSlot, remaining, false);
            }
            if (!remaining.isEmpty()) {
                Block.popResource(level, controllerPos, remaining);
            }
        }
    }

    private static void restoreFluidSnapshot(ServerLevel level, BlockPos controllerPos, PocketFactorySavedData.FluidStorageSnapshot snapshot) {
        if (snapshot.fluid().isEmpty()) {
            return;
        }

        BlockPos capabilityPos = resolveCapabilityPos(level, controllerPos, StorageKind.FLUID_TANK);
        if (capabilityPos == null) {
            return;
        }

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, capabilityPos, null);
        if (handler != null) {
            handler.fill(snapshot.fluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static void dropItemSnapshot(ServerLevel level, BlockPos dropPos, PocketFactorySavedData.ItemStorageSnapshot snapshot) {
        for (int slot = 0; slot < snapshot.handler().getSlots(); slot++) {
            ItemStack stack = snapshot.handler().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, dropPos, stack.copy());
            }
        }
    }

    private static void normalizeStructure(ServerLevel level, BlockPos controllerPos, StorageKind kind) {
        Set<BlockPos> positions = collectControlledStructure(level, controllerPos, kind);
        if (positions.isEmpty() && level.getBlockState(controllerPos).is(kind.linkedBlock())) {
            positions.add(controllerPos.immutable());
        }
        if (positions.isEmpty()) {
            return;
        }

        markCollapsing(level, positions, true);
        try {
            clearBindingState(level, positions, kind);
            convertCluster(level, positions, kind, false);
        } finally {
            markCollapsing(level, positions, false);
        }
    }

    private static void markCollapsing(Level level, Set<BlockPos> positions, boolean collapsing) {
        for (BlockPos pos : positions) {
            String key = collapseKey(level, pos);
            if (collapsing) {
                COLLAPSING_POSITIONS.add(key);
            } else {
                COLLAPSING_POSITIONS.remove(key);
            }
        }
    }

    private static String collapseKey(Level level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Set<BlockPos> collectControlledStructure(ServerLevel level, BlockPos controllerPos, StorageKind kind) {
        return switch (kind) {
            case ITEM_VAULT -> collectItemVaultStructure(level, controllerPos);
            case FLUID_TANK -> collectFluidTankStructure(level, controllerPos);
        };
    }

    private static Set<BlockPos> collectItemVaultStructure(ServerLevel level, BlockPos controllerPos) {
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (!(blockEntity instanceof ItemVaultBlockEntity vault)) {
            return Set.of();
        }

        ItemVaultBlockEntity controller = vault.getControllerBE();
        if (controller == null) {
            controller = vault;
        }

        BlockPos root = controller.getBlockPos();
        Set<BlockPos> positions = new HashSet<>();
        switch (controller.getMainConnectionAxis()) {
            case X -> LinkedStorageBindingHelper.forEachBox(root, controller.getHeight(), controller.getWidth(), controller.getWidth(),
                    pos -> addIfSameItemVaultController(level, pos, root, positions));
            case Y -> LinkedStorageBindingHelper.forEachBox(root, controller.getWidth(), controller.getHeight(), controller.getWidth(),
                    pos -> addIfSameItemVaultController(level, pos, root, positions));
            case Z -> LinkedStorageBindingHelper.forEachBox(root, controller.getWidth(), controller.getWidth(), controller.getHeight(),
                    pos -> addIfSameItemVaultController(level, pos, root, positions));
        }
        return positions;
    }

    private static Set<BlockPos> collectFluidTankStructure(ServerLevel level, BlockPos controllerPos) {
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
            return Set.of();
        }

        FluidTankBlockEntity controller = tank.getControllerBE();
        if (controller == null) {
            controller = tank;
        }

        BlockPos root = controller.getBlockPos();
        Set<BlockPos> positions = new HashSet<>();
        LinkedStorageBindingHelper.forEachBox(root, controller.getWidth(), controller.getHeight(), controller.getWidth(),
                pos -> addIfSameFluidTankController(level, pos, root, positions));
        return positions;
    }

    private static void addIfSameItemVaultController(Level level, BlockPos pos, BlockPos controllerPos, Set<BlockPos> positions) {
        if (!level.getBlockState(pos).is(ModBlocks.LINKED_ITEM_VAULT.get())) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ItemVaultBlockEntity vault)) {
            return;
        }
        ItemVaultBlockEntity resolvedController = vault.getControllerBE();
        BlockPos resolvedControllerPos = resolvedController != null ? resolvedController.getBlockPos() : vault.getBlockPos();
        if (resolvedControllerPos.equals(controllerPos)) {
            positions.add(pos.immutable());
        }
    }

    private static void addIfSameFluidTankController(Level level, BlockPos pos, BlockPos controllerPos, Set<BlockPos> positions) {
        if (!level.getBlockState(pos).is(ModBlocks.LINKED_FLUID_TANK.get())) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
            return;
        }
        FluidTankBlockEntity resolvedController = tank.getControllerBE();
        BlockPos resolvedControllerPos = resolvedController != null ? resolvedController.getBlockPos() : tank.getBlockPos();
        if (resolvedControllerPos.equals(controllerPos)) {
            positions.add(pos.immutable());
        }
    }

    private static @Nullable LinkedItemVaultBlockEntity resolveItemController(Level level, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LinkedItemVaultBlockEntity vault) {
                if (vault.isController()) {
                    return vault;
                }
                ItemVaultBlockEntity controller = vault.getControllerBE();
                if (controller instanceof LinkedItemVaultBlockEntity linkedController) {
                    return linkedController;
                }
            }
        }
        return null;
    }

    private static @Nullable LinkedFluidTankBlockEntity resolveFluidController(Level level, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LinkedFluidTankBlockEntity tank) {
                if (tank.isController()) {
                    return tank;
                }
                FluidTankBlockEntity controller = tank.getControllerBE();
                if (controller instanceof LinkedFluidTankBlockEntity linkedController) {
                    return linkedController;
                }
            }
        }
        return null;
    }

    private static void convertCluster(ServerLevel level, Set<BlockPos> connected, StorageKind kind, boolean toLinked) {
        Map<BlockPos, BlockState> oldStates = new HashMap<>();
        Map<BlockPos, CompoundTag> oldData = new HashMap<>();
        for (BlockPos pos : connected) {
            oldStates.put(pos, level.getBlockState(pos));
            BlockEntity oldBe = level.getBlockEntity(pos);
            if (oldBe != null) {
                oldData.put(pos, oldBe.saveWithFullMetadata(level.registryAccess()));
            }
        }

        for (BlockPos pos : connected) {
            BlockState convertedState = kind.copyState(oldStates.get(pos), toLinked);
            level.setBlock(pos, convertedState, Block.UPDATE_ALL_IMMEDIATE);
        }

        for (Map.Entry<BlockPos, CompoundTag> entry : oldData.entrySet()) {
            BlockEntity newBe = level.getBlockEntity(entry.getKey());
            if (newBe == null) {
                continue;
            }
            CompoundTag tag = entry.getValue().copy();
            tag.putInt("x", entry.getKey().getX());
            tag.putInt("y", entry.getKey().getY());
            tag.putInt("z", entry.getKey().getZ());
            newBe.loadWithComponents(tag, level.registryAccess());
            newBe.setChanged();
        }

        for (BlockPos pos : connected) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LinkedItemVaultBlockEntity vault) {
                vault.refreshConnectivityNow();
            }
            if (blockEntity instanceof LinkedFluidTankBlockEntity tank) {
                tank.refreshConnectivityNow();
            }
        }
    }

    private static Set<BlockPos> collectConnected(Level level, BlockPos origin, Block sourceBlock) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() == sourceBlock) {
                    frontier.add(next);
                }
            }
        }

        return visited;
    }

    public enum StorageKind {
        ITEM_VAULT(AllBlocks.ITEM_VAULT.get(), ModBlocks.LINKED_ITEM_VAULT.get()),
        FLUID_TANK(AllBlocks.FLUID_TANK.get(), ModBlocks.LINKED_FLUID_TANK.get());

        private final Block normalBlock;
        private final Block linkedBlock;

        StorageKind(Block normalBlock, Block linkedBlock) {
            this.normalBlock = normalBlock;
            this.linkedBlock = linkedBlock;
        }

        public Block normalBlock() {
            return normalBlock;
        }

        public Block linkedBlock() {
            return linkedBlock;
        }

        public boolean matchesNormal(BlockState state) {
            return state.getBlock() == normalBlock;
        }

        public PocketFactorySavedData.BindingChannel bindingChannel() {
            return switch (this) {
                case ITEM_VAULT -> PocketFactorySavedData.BindingChannel.ITEM_STORAGE;
                case FLUID_TANK -> PocketFactorySavedData.BindingChannel.FLUID_STORAGE;
            };
        }

        private BlockState copyState(BlockState oldState, boolean toLinked) {
            return switch (this) {
                case ITEM_VAULT -> {
                    Block block = toLinked ? linkedBlock : normalBlock;
                    yield block.defaultBlockState()
                            .setValue(ItemVaultBlock.HORIZONTAL_AXIS, oldState.getValue(ItemVaultBlock.HORIZONTAL_AXIS))
                            .setValue(ItemVaultBlock.LARGE, oldState.getValue(ItemVaultBlock.LARGE));
                }
                case FLUID_TANK -> {
                    Block block = toLinked ? linkedBlock : normalBlock;
                    yield block.defaultBlockState()
                            .setValue(FluidTankBlock.TOP, oldState.getValue(FluidTankBlock.TOP))
                            .setValue(FluidTankBlock.BOTTOM, oldState.getValue(FluidTankBlock.BOTTOM))
                            .setValue(FluidTankBlock.SHAPE, oldState.getValue(FluidTankBlock.SHAPE));
                }
            };
        }
    }

    private record StructureTarget(ServerLevel level, BlockPos controllerPos, Set<BlockPos> positions) {
    }
}