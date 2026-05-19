package com.assestinar.createpocketfactory.block.entity;

import com.assestinar.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import com.simibubi.create.AllBlocks;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Iterate;

public final class LinkedChuteBlockEntity extends ChuteBlockEntity implements IHaveGoggleInformation {
    private static final String BINDING_ID_TAG = "BindingId";
    private static final String FACTORY_ID_TAG = "FactoryId";
    private static final String INTERNAL_ENDPOINT_TAG = "InternalEndpoint";
    private static final String RECEIVED_TRANSFER_IN_FLIGHT_TAG = "ReceivedTransferInFlight";
    private static final float OUTPUT_GATE = 0.5f;
    private static final int TOPOLOGY_CACHE_TTL = 10;
    private static final Field ITEM_POSITION_FIELD = resolveItemPositionField();
    private static final Field PULL_FIELD = resolveChuteField("pull");
    private static final Field PUSH_FIELD = resolveChuteField("push");
    private static final Field UPDATE_AIR_FLOW_FIELD = resolveChuteField("updateAirFlow");

    private int bindingId = -1;
    private int factoryId = -1;
    private boolean internalEndpoint;
    private boolean receivedTransferInFlight;
    private float localPullSource;
    private float localPushSource;
    private @Nullable CachedLocalTopology cachedLocalTopology;
    private @Nullable CachedOppositeEndpoint cachedOppositeEndpoint;

    public LinkedChuteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINKED_CHUTE.get(), pos, state);
    }

    public int getBindingId() {
        return bindingId;
    }

    public boolean hasBinding() {
        return bindingId > 0;
    }

    public int getFactoryId() {
        return factoryId;
    }

    public boolean hasFactoryId() {
        return factoryId > 0;
    }

    public boolean isInternalEndpoint() {
        return internalEndpoint;
    }

    public void setBinding(int bindingId, int factoryId, boolean internalEndpoint) {
        boolean changed = this.bindingId != bindingId || this.factoryId != factoryId || this.internalEndpoint != internalEndpoint;
        this.bindingId = bindingId;
        this.factoryId = factoryId;
        this.internalEndpoint = internalEndpoint;
        if (!changed) {
            return;
        }
        invalidateCaches();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void clearBinding() {
        setBinding(-1, -1, false);
        receivedTransferInFlight = false;
        localPullSource = 0.0f;
        localPushSource = 0.0f;
        applyEffectiveAirflow(0.0f, 0.0f);
    }

    @Override
    public void tick() {
        if (getItem().isEmpty() && receivedTransferInFlight) {
            receivedTransferInFlight = false;
        }

        if (level != null && !level.isClientSide && level.getServer() != null && hasBinding()) {
            PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
            String endpointKey = LinkedStorageBindingHelper.endpointKey(level, worldPosition);
            if (!savedData.isEndpointBoundToBinding(bindingId, PocketFactorySavedData.BindingChannel.LINKED_CHUTE, endpointKey)) {
                normalizeOrphanedChute(level, getBlockState());
                return;
            }

            syncBoundAirflow((ServerLevel) level, savedData, endpointKey);

            if (getItem().isEmpty()) {
                absorbTopItemEntities((ServerLevel) level);
            }

            if (getItem().isEmpty()) {
                ItemStack pendingStack = savedData.pollChuteTransfer(bindingId, endpointKey, level.registryAccess());
                if (!pendingStack.isEmpty()) {
                    receivedTransferInFlight = true;
                    setItem(pendingStack, 1.0f);
                }
            }

            if (tryForwardBoundItem((ServerLevel) level, savedData, endpointKey)) {
                return;
            }
        }

        super.tick();
    }

    @Override
    public void invalidate() {
        invalidateCaches();
        super.invalidate();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt(BINDING_ID_TAG, bindingId);
        compound.putInt(FACTORY_ID_TAG, factoryId);
        compound.putBoolean(INTERNAL_ENDPOINT_TAG, internalEndpoint);
        compound.putBoolean(RECEIVED_TRANSFER_IN_FLIGHT_TAG, receivedTransferInFlight);
        super.write(compound, registries, clientPacket);
    }

    @Override
    public void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        bindingId = compound.contains(BINDING_ID_TAG) ? compound.getInt(BINDING_ID_TAG) : -1;
        factoryId = compound.contains(FACTORY_ID_TAG) ? compound.getInt(FACTORY_ID_TAG) : -1;
        internalEndpoint = compound.getBoolean(INTERNAL_ENDPOINT_TAG);
        receivedTransferInFlight = compound.getBoolean(RECEIVED_TRANSFER_IN_FLIGHT_TAG);
        super.read(compound, registries, clientPacket);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag, registries, true);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        read(tag == null ? new CompoundTag() : tag, registries, true);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
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
        if (hasFactoryId()) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.factory_id", factoryId)
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(internalEndpoint ? "Endpoint: internal" : "Endpoint: external")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        return true;
    }

    private void absorbTopItemEntities(ServerLevel level) {
        if (!getItem().isEmpty()) {
            return;
        }

        AABB searchArea = new AABB(worldPosition).move(0.0, 0.75, 0.0).inflate(0.35, 0.3, 0.35);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchArea)) {
            if (!itemEntity.isAlive()) {
                continue;
            }

            ItemStack entityItem = itemEntity.getItem();
            if (entityItem.isEmpty()) {
                continue;
            }

            setItem(entityItem.copy(), 1.0f);
            itemEntity.discard();
            return;
        }
    }

    private void normalizeOrphanedChute(Level level, BlockState currentState) {
        BlockState restoredState = AllBlocks.CHUTE.getDefaultState()
                .setValue(com.simibubi.create.content.logistics.chute.ChuteBlock.FACING,
                        currentState.getValue(com.simibubi.create.content.logistics.chute.ChuteBlock.FACING))
                .setValue(com.simibubi.create.content.logistics.chute.ChuteBlock.SHAPE,
                        currentState.getValue(com.simibubi.create.content.logistics.chute.ChuteBlock.SHAPE))
                .setValue(com.simibubi.create.content.logistics.chute.ChuteBlock.WATERLOGGED,
                        currentState.getValue(com.simibubi.create.content.logistics.chute.ChuteBlock.WATERLOGGED));
        level.setBlock(worldPosition, restoredState, Block.UPDATE_ALL_IMMEDIATE);
    }

    private boolean tryForwardBoundItem(ServerLevel level, PocketFactorySavedData savedData, String endpointKey) {
        if (getItem().isEmpty() || getItemMotion() >= 0 || receivedTransferInFlight) {
            return false;
        }

        Direction facing = AbstractChuteBlock.getChuteFacing(getBlockState());
        if (facing != Direction.DOWN) {
            return false;
        }

        float currentPosition = getTrackedItemPosition();
        float nextPosition = currentPosition + getItemMotion();
        if (nextPosition >= OUTPUT_GATE) {
            return false;
        }

        String targetEndpointKey = resolveOppositeEndpointKey(savedData, endpointKey);
        if (targetEndpointKey == null) {
            setTrackedItemPosition(OUTPUT_GATE);
            return true;
        }

        if (savedData.offerChuteTransfer(bindingId, targetEndpointKey, getItem().copy(), level.registryAccess())) {
            setItem(ItemStack.EMPTY, 0.0f);
            return true;
        }

        setTrackedItemPosition(OUTPUT_GATE);
        return true;
    }

    private void syncBoundAirflow(ServerLevel level, PocketFactorySavedData savedData, String endpointKey) {
        localPullSource = computeLocalUpperPull();
        localPushSource = computeLocalLowerPush();

        LinkedChuteBlockEntity opposite = resolveOppositeEndpoint(savedData, endpointKey);
        if (opposite != null) {
            opposite.applyEffectiveAirflow(localPullSource, localPushSource);
            applyEffectiveAirflow(opposite.localPullSource, opposite.localPushSource);
            return;
        }

        applyEffectiveAirflow(0.0f, 0.0f);
    }

    private float computeLocalUpperPull() {
        if (level == null) {
            return 0.0f;
        }

        BlockState aboveState = level.getBlockState(worldPosition.above());
        if (AllBlocks.ENCASED_FAN.has(aboveState) && aboveState.getValue(EncasedFanBlock.FACING) == Direction.DOWN) {
            BlockEntity blockEntity = level.getBlockEntity(worldPosition.above());
            if (blockEntity instanceof EncasedFanBlockEntity fan && !fan.isRemoved()) {
                return fan.getSpeed();
            }
        }

        float totalPull = 0.0f;
        for (ChuteBlockEntity inputChute : getLocalInputChutes()) {
            totalPull += getChutePull(inputChute);
        }
        return totalPull;
    }

    private float computeLocalLowerPush() {
        if (level == null) {
            return 0.0f;
        }

        BlockState belowState = level.getBlockState(worldPosition.below());
        if (AllBlocks.ENCASED_FAN.has(belowState) && belowState.getValue(EncasedFanBlock.FACING) == Direction.UP) {
            BlockEntity blockEntity = level.getBlockEntity(worldPosition.below());
            if (blockEntity instanceof EncasedFanBlockEntity fan && !fan.isRemoved()) {
                return fan.getSpeed();
            }
        }

        ChuteBlockEntity targetChute = getLocalTargetChute();
        if (targetChute == null) {
            return 0.0f;
        }

        int branchCount = Math.max(1, countInputChutes(targetChute));
        return getChutePush(targetChute) / branchCount;
    }

    private void applyEffectiveAirflow(float effectivePull, float effectivePush) {
        if (Float.compare(getChutePull(this), effectivePull) == 0 && Float.compare(getChutePush(this), effectivePush) == 0) {
            return;
        }

        setChutePull(this, effectivePull);
        setChutePush(this, effectivePush);
        setUpdateAirFlow(this, true);
        setChanged();
        sendData();

        ChuteBlockEntity targetChute = getLocalTargetChute();
        if (targetChute != null) {
            targetChute.updatePull();
        }

        List<ChuteBlockEntity> inputChutes = getLocalInputChutes();
        int branchCount = inputChutes.size();
        if (branchCount <= 0) {
            return;
        }

        for (ChuteBlockEntity inputChute : inputChutes) {
            inputChute.updatePush(branchCount);
        }
    }

    private @Nullable LinkedChuteBlockEntity resolveOppositeEndpoint(PocketFactorySavedData savedData, String endpointKey) {
        CachedOppositeEndpoint cached = getCachedOppositeEndpoint(savedData, endpointKey);
        if (cached == null || level == null || level.getServer() == null) {
            return null;
        }

        ServerLevel targetLevel = level.getServer().getLevel(cached.location().dimension());
        if (targetLevel == null) {
            return null;
        }

        return targetLevel.getBlockEntity(cached.location().pos()) instanceof LinkedChuteBlockEntity opposite && !opposite.isRemoved()
                ? opposite
                : null;
    }

    private @Nullable ChuteBlockEntity getLocalTargetChute() {
        CachedLocalTopology topology = getCachedLocalTopology();
        if (topology == null || topology.targetPos() == null || level == null) {
            return null;
        }

        BlockPos chutePos = topology.targetPos();
        if (!(level.getBlockEntity(chutePos) instanceof ChuteBlockEntity targetChute) || targetChute.isRemoved()) {
            return null;
        }
        return targetChute;
    }

    private @Nullable BlockPos findLocalTargetChutePos() {
        if (level == null) {
            return null;
        }

        Direction targetDirection = AbstractChuteBlock.getChuteFacing(getBlockState());
        if (targetDirection == null) {
            return null;
        }

        BlockPos chutePos = worldPosition.below();
        if (targetDirection.getAxis().isHorizontal()) {
            chutePos = chutePos.relative(targetDirection.getOpposite());
        }

        return chutePos;
    }

    private List<ChuteBlockEntity> getLocalInputChutes() {
        CachedLocalTopology topology = getCachedLocalTopology();
        List<ChuteBlockEntity> inputs = new ArrayList<>();
        if (topology == null || level == null) {
            return inputs;
        }

        for (BlockPos inputPos : topology.inputPositions()) {
            if (level.getBlockEntity(inputPos) instanceof ChuteBlockEntity inputChute && !inputChute.isRemoved()) {
                inputs.add(inputChute);
            }
        }
        return inputs;
    }

    private List<BlockPos> findLocalInputChutePositions() {
        List<ChuteBlockEntity> inputs = new LinkedList<>();
        List<BlockPos> positions = new ArrayList<>();
        for (Direction direction : Iterate.directions) {
            ChuteBlockEntity inputChute = getLocalInputChute(direction);
            if (inputChute != null) {
                inputs.add(inputChute);
                positions.add(inputChute.getBlockPos());
            }
        }
        return positions;
    }

    private @Nullable ChuteBlockEntity getLocalInputChute(Direction direction) {
        if (level == null || direction == Direction.DOWN) {
            return null;
        }

        Direction oppositeDirection = direction.getOpposite();
        BlockPos chutePos = worldPosition.above();
        if (oppositeDirection.getAxis().isHorizontal()) {
            chutePos = chutePos.relative(oppositeDirection);
        }

        BlockState chuteState = level.getBlockState(chutePos);
        Direction chuteFacing = AbstractChuteBlock.getChuteFacing(chuteState);
        if (chuteFacing != oppositeDirection) {
            return null;
        }

        return level.getBlockEntity(chutePos) instanceof ChuteBlockEntity inputChute && !inputChute.isRemoved()
                ? inputChute
                : null;
    }

    private static int countInputChutes(ChuteBlockEntity chute) {
        Level chuteLevel = chute.getLevel();
        if (chuteLevel == null) {
            return 0;
        }

        int count = 0;
        for (Direction direction : Iterate.directions) {
            if (direction == Direction.DOWN) {
                continue;
            }

            Direction oppositeDirection = direction.getOpposite();
            BlockPos chutePos = chute.getBlockPos().above();
            if (oppositeDirection.getAxis().isHorizontal()) {
                chutePos = chutePos.relative(oppositeDirection);
            }

            BlockState chuteState = chuteLevel.getBlockState(chutePos);
            if (AbstractChuteBlock.getChuteFacing(chuteState) == oppositeDirection
                    && chuteLevel.getBlockEntity(chutePos) instanceof ChuteBlockEntity inputChute
                    && !inputChute.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    private @Nullable String resolveOppositeEndpointKey(PocketFactorySavedData savedData, String endpointKey) {
        PocketFactorySavedData.BindingEndpoints endpoints = savedData.getBindingEndpoints(bindingId, PocketFactorySavedData.BindingChannel.LINKED_CHUTE);
        if (endpoints == null) {
            return null;
        }
        return BindingEndpointHelper.resolveOppositeEndpointKey(endpoints, endpointKey);
    }

    private @Nullable CachedLocalTopology getCachedLocalTopology() {
        if (level == null) {
            return null;
        }

        long gameTime = level.getGameTime();
        if (cachedLocalTopology == null || !isLocalTopologyValid(cachedLocalTopology)) {
            cachedLocalTopology = buildLocalTopologyCache(gameTime + TOPOLOGY_CACHE_TTL);
        } else if (cachedLocalTopology.expiresAt() <= gameTime) {
            cachedLocalTopology = cachedLocalTopology.withExpiry(gameTime + TOPOLOGY_CACHE_TTL);
        }
        return cachedLocalTopology;
    }

    private CachedLocalTopology buildLocalTopologyCache(long expiresAt) {
        List<WatchedBlockState> watchedStates = new ArrayList<>();
        BlockPos targetPos = findLocalTargetChutePos();
        if (targetPos != null && level != null) {
            watchedStates.add(new WatchedBlockState(targetPos, Block.getId(level.getBlockState(targetPos))));
        }

        List<BlockPos> inputPositions = findLocalInputChutePositions();
        if (level != null) {
            for (BlockPos inputPos : inputPositions) {
                watchedStates.add(new WatchedBlockState(inputPos, Block.getId(level.getBlockState(inputPos))));
            }
        }

        return new CachedLocalTopology(expiresAt, targetPos, inputPositions, watchedStates);
    }

    private boolean isLocalTopologyValid(CachedLocalTopology topology) {
        if (level == null) {
            return false;
        }
        for (WatchedBlockState watchedState : topology.watchedStates()) {
            if (!level.isLoaded(watchedState.pos())) {
                return false;
            }
            if (Block.getId(level.getBlockState(watchedState.pos())) != watchedState.stateId()) {
                return false;
            }
        }
        return true;
    }

    private @Nullable CachedOppositeEndpoint getCachedOppositeEndpoint(PocketFactorySavedData savedData, String endpointKey) {
        if (level == null || level.getServer() == null) {
            return null;
        }

        long gameTime = level.getGameTime();
        if (cachedOppositeEndpoint == null
                || !endpointKey.equals(cachedOppositeEndpoint.endpointKey())
                || cachedOppositeEndpoint.expiresAt() <= gameTime) {
            String oppositeEndpointKey = resolveOppositeEndpointKey(savedData, endpointKey);
            LinkedStorageBindingHelper.EndpointLocation location = oppositeEndpointKey == null
                    ? null
                    : LinkedStorageBindingHelper.parseEndpointKey(oppositeEndpointKey);
            if (location == null) {
                cachedOppositeEndpoint = null;
                return null;
            }
            cachedOppositeEndpoint = new CachedOppositeEndpoint(gameTime + TOPOLOGY_CACHE_TTL, endpointKey, location);
        }
        return cachedOppositeEndpoint;
    }

    private void invalidateCaches() {
        cachedLocalTopology = null;
        cachedOppositeEndpoint = null;
    }

    private float getTrackedItemPosition() {
        try {
            LerpedFloat itemPosition = (LerpedFloat) ITEM_POSITION_FIELD.get(this);
            return itemPosition.getValue();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read chute item position", exception);
        }
    }

    private void setTrackedItemPosition(float value) {
        try {
            LerpedFloat itemPosition = (LerpedFloat) ITEM_POSITION_FIELD.get(this);
            itemPosition.setValue(value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to update chute item position", exception);
        }
    }

    private static Field resolveItemPositionField() {
        try {
            Field field = ChuteBlockEntity.class.getDeclaredField("itemPosition");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Unable to access chute item position field", exception);
        }
    }

    private static Field resolveChuteField(String fieldName) {
        try {
            Field field = ChuteBlockEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Unable to access chute field: " + fieldName, exception);
        }
    }

    private static float getChutePull(ChuteBlockEntity chute) {
        try {
            return PULL_FIELD.getFloat(chute);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read chute pull", exception);
        }
    }

    private static float getChutePush(ChuteBlockEntity chute) {
        try {
            return PUSH_FIELD.getFloat(chute);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read chute push", exception);
        }
    }

    private static void setChutePull(ChuteBlockEntity chute, float value) {
        try {
            PULL_FIELD.setFloat(chute, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to update chute pull", exception);
        }
    }

    private static void setChutePush(ChuteBlockEntity chute, float value) {
        try {
            PUSH_FIELD.setFloat(chute, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to update chute push", exception);
        }
    }

    private static void setUpdateAirFlow(ChuteBlockEntity chute, boolean value) {
        try {
            UPDATE_AIR_FLOW_FIELD.setBoolean(chute, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to update chute airflow flag", exception);
        }
    }

    private record WatchedBlockState(BlockPos pos, int stateId) {
    }

    private record CachedLocalTopology(long expiresAt, @Nullable BlockPos targetPos, List<BlockPos> inputPositions,
                                       List<WatchedBlockState> watchedStates) {
        private CachedLocalTopology withExpiry(long newExpiresAt) {
            return new CachedLocalTopology(newExpiresAt, targetPos, inputPositions, watchedStates);
        }
    }

    private record CachedOppositeEndpoint(long expiresAt, String endpointKey,
                                          LinkedStorageBindingHelper.EndpointLocation location) {
    }
}