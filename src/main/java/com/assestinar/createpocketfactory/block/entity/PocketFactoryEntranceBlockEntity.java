package com.assestinar.createpocketfactory.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PocketFactoryEntranceBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final String FACTORY_ID_TAG = "FactoryId";
    private static final String PREVIEW_HASH_TAG = "PreviewHash";
    private static final String PROJECTION_ANCHOR_TAG = "ProjectionAnchor";
    private static final String PROJECTION_ROTATION_TAG = "ProjectionRotation";
    private static final String PROJECTION_FLIP_X_TAG = "ProjectionFlipX";
    private static final String PROJECTION_FLIP_Z_TAG = "ProjectionFlipZ";

    private int factoryId = -1;
    private List<PreviewBlock> previewBlocks = List.of();
    private int previewHash;
    private long clientLastPreviewRequestTick = -80L;
    @Nullable
    private BlockPos projectionAnchor;
    private int projectionRotationQuarterTurns;
    private boolean projectionFlipX;
    private boolean projectionFlipZ;

    public PocketFactoryEntranceBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.POCKET_FACTORY_ENTRANCE.get(), pos, blockState);
    }

    public int getFactoryId() {
        return factoryId;
    }

    public boolean hasFactoryId() {
        return factoryId > 0;
    }

    public void setFactoryId(int factoryId) {
        this.factoryId = factoryId;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public List<PreviewBlock> getPreviewBlocks() {
        return previewBlocks;
    }

    public boolean hasPreviewBlocks() {
        return !previewBlocks.isEmpty();
    }

    public int getPreviewHash() {
        return previewHash;
    }

    public @Nullable BlockPos getProjectionAnchor() {
        return projectionAnchor;
    }

    public boolean hasProjectionAnchor() {
        return projectionAnchor != null;
    }

    public void setProjectionAnchor(@Nullable BlockPos projectionAnchor) {
        this.projectionAnchor = projectionAnchor;
        notifyProjectionChanged();
    }

    public int getProjectionRotationQuarterTurns() {
        return projectionRotationQuarterTurns;
    }

    public boolean isProjectionFlipX() {
        return projectionFlipX;
    }

    public boolean isProjectionFlipZ() {
        return projectionFlipZ;
    }

    public void setProjectionTransform(int rotationQuarterTurns, boolean flipX, boolean flipZ) {
        int normalizedRotation = Math.floorMod(rotationQuarterTurns, 4);
        if (projectionRotationQuarterTurns == normalizedRotation && projectionFlipX == flipX && projectionFlipZ == flipZ) {
            return;
        }

        projectionRotationQuarterTurns = normalizedRotation;
        projectionFlipX = flipX;
        projectionFlipZ = flipZ;
        notifyProjectionChanged();
    }

    public void setProjectionState(@Nullable BlockPos projectionAnchor, int rotationQuarterTurns, boolean flipX, boolean flipZ) {
        int normalizedRotation = Math.floorMod(rotationQuarterTurns, 4);
        boolean changed = !java.util.Objects.equals(this.projectionAnchor, projectionAnchor)
                || projectionRotationQuarterTurns != normalizedRotation
                || projectionFlipX != flipX
                || projectionFlipZ != flipZ;
        if (!changed) {
            return;
        }

        this.projectionAnchor = projectionAnchor;
        projectionRotationQuarterTurns = normalizedRotation;
        projectionFlipX = flipX;
        projectionFlipZ = flipZ;
        notifyProjectionChanged();
    }

    private void notifyProjectionChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean shouldRequestPreview(long gameTime) {
        return hasFactoryId() && (clientLastPreviewRequestTick < 0L || gameTime - clientLastPreviewRequestTick >= 80L);
    }

    public void markPreviewRequest(long gameTime) {
        clientLastPreviewRequestTick = gameTime;
    }

    public void refreshPreviewSnapshot() {
        if (!(level instanceof ServerLevel serverLevel) || level.getServer() == null || !hasFactoryId()) {
            return;
        }

        updatePreviewBlocks(PocketFactoryPreviewHelper.sampleFactoryPreview(serverLevel, factoryId));
    }

    private void updatePreviewBlocks(List<PreviewBlock> updatedPreview) {
        int updatedHash = updatedPreview.hashCode();
        if (previewHash == updatedHash && previewBlocks.equals(updatedPreview)) {
            return;
        }
        previewBlocks = List.copyOf(updatedPreview);
        previewHash = updatedHash;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (factoryId > 0) {
            tag.putInt(FACTORY_ID_TAG, factoryId);
        }
        if (projectionAnchor != null) {
            tag.putLong(PROJECTION_ANCHOR_TAG, projectionAnchor.asLong());
        }
        tag.putInt(PROJECTION_ROTATION_TAG, projectionRotationQuarterTurns);
        tag.putBoolean(PROJECTION_FLIP_X_TAG, projectionFlipX);
        tag.putBoolean(PROJECTION_FLIP_Z_TAG, projectionFlipZ);
        tag.putInt(PREVIEW_HASH_TAG, previewHash);
        tag.merge(PocketFactoryPreviewHelper.writePreviewBlocks(previewBlocks));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        factoryId = tag.contains(FACTORY_ID_TAG) ? tag.getInt(FACTORY_ID_TAG) : -1;
        projectionAnchor = tag.contains(PROJECTION_ANCHOR_TAG) ? BlockPos.of(tag.getLong(PROJECTION_ANCHOR_TAG)) : null;
        projectionRotationQuarterTurns = Math.floorMod(tag.getInt(PROJECTION_ROTATION_TAG), 4);
        projectionFlipX = tag.getBoolean(PROJECTION_FLIP_X_TAG);
        projectionFlipZ = tag.getBoolean(PROJECTION_FLIP_Z_TAG);
        previewHash = tag.getInt(PREVIEW_HASH_TAG);
        previewBlocks = PocketFactoryPreviewHelper.readPreviewBlocks(tag, registries);
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
        tooltip.add(bindingLine(hasFactoryId()));
        if (hasFactoryId()) {
            tooltip.add(Component.translatable("goggles.create_pocket_factory.common.factory_id", factoryId)
                    .withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    private static Component bindingLine(boolean bound) {
        return Component.translatable("goggles.create_pocket_factory.common.binding")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(bound
                                ? "goggles.create_pocket_factory.common.bound"
                                : "goggles.create_pocket_factory.common.unbound")
                        .withStyle(bound ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public record PreviewBlock(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntityTag) {
    }
}