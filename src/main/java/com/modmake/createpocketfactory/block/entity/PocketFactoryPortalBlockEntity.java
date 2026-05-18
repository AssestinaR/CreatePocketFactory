package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

public final class PocketFactoryPortalBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final String FACTORY_ID_TAG = "FactoryId";
    private static final String INTERNAL_ENDPOINT_TAG = "InternalEndpoint";
    private static final String PORTAL_COOLDOWN_TAG = "CreatePocketFactoryPortalCooldown";

    private int factoryId = -1;
    private boolean internalEndpoint;

    public PocketFactoryPortalBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.POCKET_FACTORY_PORTAL.get(), pos, blockState);
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

    public void setBinding(int factoryId, boolean internalEndpoint) {
        boolean changed = this.factoryId != factoryId || this.internalEndpoint != internalEndpoint;
        this.factoryId = factoryId;
        this.internalEndpoint = internalEndpoint;
        if (!changed) {
            return;
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void tryTransportItemEntity(ItemEntity itemEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !hasFactoryId()) {
            return;
        }

        long cooldownUntil = itemEntity.getPersistentData().getLong(PORTAL_COOLDOWN_TAG);
        if (cooldownUntil > serverLevel.getGameTime()) {
            return;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(serverLevel.getServer());
        PocketFactorySavedData.PortalEndpoint targetEndpoint = internalEndpoint
                ? PocketFactorySavedData.PortalEndpoint.EXTERNAL
                : PocketFactorySavedData.PortalEndpoint.INTERNAL;
        PocketFactorySavedData.PortalEndpointRecord target = savedData.getPortalEndpoint(factoryId, targetEndpoint);
        if (target == null) {
            return;
        }

        ResourceKey<Level> targetDimension = ResourceKey.create(Registries.DIMENSION, target.dimension());
        ServerLevel targetLevel = serverLevel.getServer().getLevel(targetDimension);
        if (targetLevel == null || !targetLevel.hasChunkAt(target.pos())) {
            return;
        }

        ItemStack stack = itemEntity.getItem().copy();
        if (stack.isEmpty()) {
            return;
        }

        ItemEntity transported = new ItemEntity(
                targetLevel,
                target.pos().getX() + 0.5D,
                target.pos().getY() - 0.15D,
                target.pos().getZ() + 0.5D,
                stack
        );
        transported.setDeltaMovement(0.0D, Math.min(itemEntity.getDeltaMovement().y, -0.12D), 0.0D);
        transported.setDefaultPickUpDelay();
        transported.getPersistentData().putLong(PORTAL_COOLDOWN_TAG, targetLevel.getGameTime() + 10L);
        targetLevel.addFreshEntity(transported);
        itemEntity.discard();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (factoryId > 0) {
            tag.putInt(FACTORY_ID_TAG, factoryId);
        }
        tag.putBoolean(INTERNAL_ENDPOINT_TAG, internalEndpoint);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        factoryId = tag.contains(FACTORY_ID_TAG) ? tag.getInt(FACTORY_ID_TAG) : -1;
        internalEndpoint = tag.getBoolean(INTERNAL_ENDPOINT_TAG);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        loadAdditional(tag == null ? new CompoundTag() : tag, registries);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(getBlockState().getBlock().getName().copy().withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("goggles.create_pocket_factory.common.binding")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(hasFactoryId()
                                ? "goggles.create_pocket_factory.common.bound"
                                : "goggles.create_pocket_factory.common.unbound")
                        .withStyle(hasFactoryId() ? ChatFormatting.GREEN : ChatFormatting.RED)));
        if (hasFactoryId()) {
            tooltip.add(Component.literal("Factory: " + factoryId).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(internalEndpoint ? "Endpoint: internal" : "Endpoint: external")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        return true;
    }
}