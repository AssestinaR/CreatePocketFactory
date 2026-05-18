package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.world.PocketFactoryDimensions;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PocketFactoryEntranceBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final String FACTORY_ID_TAG = "FactoryId";
    private static final String PREVIEW_BLOCKS_TAG = "PreviewBlocks";
    private static final String PREVIEW_HASH_TAG = "PreviewHash";
    private static final int PREVIEW_SIZE = 16;
    private static final int PREVIEW_RADIUS = PREVIEW_SIZE / 2;
    private static final int MAX_PREVIEW_BLOCKS = 192;

    private int factoryId = -1;
    private List<PreviewBlock> previewBlocks = List.of();
    private int previewHash;
    private long clientLastPreviewRequestTick = -80L;

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

        PocketFactorySavedData.FactoryRecord factory = PocketFactorySavedData.get(level.getServer()).getFactory(factoryId);
        ServerLevel factoryLevel = level.getServer().getLevel(PocketFactoryDimensions.LEVEL_KEY);
        if (factory == null || factoryLevel == null) {
            updatePreviewBlocks(List.of());
            return;
        }

        ChunkPos baseChunk = PocketFactoryDimensions.getFactoryBaseChunk(factory);
        int centerX = baseChunk.getMinBlockX() + 8;
        int centerY = (factory.minY() + factory.maxY()) / 2;
        int centerZ = baseChunk.getMinBlockZ() + 8;
        BlockPos min = new BlockPos(centerX - PREVIEW_RADIUS, centerY - PREVIEW_RADIUS, centerZ - PREVIEW_RADIUS);

        List<PreviewBlock> sampled = new ArrayList<>();
        for (int y = 0; y < PREVIEW_SIZE && sampled.size() < MAX_PREVIEW_BLOCKS; y++) {
            for (int z = 0; z < PREVIEW_SIZE && sampled.size() < MAX_PREVIEW_BLOCKS; z++) {
                for (int x = 0; x < PREVIEW_SIZE && sampled.size() < MAX_PREVIEW_BLOCKS; x++) {
                    BlockPos targetPos = min.offset(x, y, z);
                    BlockState state = factoryLevel.getBlockState(targetPos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(ModBlocks.POCKET_FACTORY_BLOCK_A.get()) || state.is(ModBlocks.POCKET_FACTORY_BLOCK_B.get())) {
                        continue;
                    }
                    sampled.add(new PreviewBlock((byte) x, (byte) y, (byte) z, state));
                }
            }
        }

        updatePreviewBlocks(sampled);
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
        tag.putInt(PREVIEW_HASH_TAG, previewHash);
        ListTag previewTag = new ListTag();
        for (PreviewBlock previewBlock : previewBlocks) {
            CompoundTag blockTag = new CompoundTag();
            blockTag.putByte("X", previewBlock.x());
            blockTag.putByte("Y", previewBlock.y());
            blockTag.putByte("Z", previewBlock.z());
            blockTag.put("State", NbtUtils.writeBlockState(previewBlock.state()));
            previewTag.add(blockTag);
        }
        tag.put(PREVIEW_BLOCKS_TAG, previewTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        factoryId = tag.contains(FACTORY_ID_TAG) ? tag.getInt(FACTORY_ID_TAG) : -1;
        previewHash = tag.getInt(PREVIEW_HASH_TAG);
        List<PreviewBlock> loadedPreview = new ArrayList<>();
        ListTag previewTag = tag.getList(PREVIEW_BLOCKS_TAG, Tag.TAG_COMPOUND);
        for (Tag entry : previewTag) {
            CompoundTag blockTag = (CompoundTag) entry;
            BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), blockTag.getCompound("State"));
            loadedPreview.add(new PreviewBlock(blockTag.getByte("X"), blockTag.getByte("Y"), blockTag.getByte("Z"), state));
        }
        previewBlocks = List.copyOf(loadedPreview);
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

    public record PreviewBlock(byte x, byte y, byte z, BlockState state) {
    }
}