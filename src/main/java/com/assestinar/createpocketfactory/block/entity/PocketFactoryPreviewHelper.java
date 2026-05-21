package com.assestinar.createpocketfactory.block.entity;

import com.assestinar.createpocketfactory.block.ModBlocks;
import com.assestinar.createpocketfactory.world.PocketFactoryDimensions;
import com.assestinar.createpocketfactory.world.PocketFactorySavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PocketFactoryPreviewHelper {
    public static final String PREVIEW_BLOCKS_TAG = "PreviewBlocks";
    private static final Set<String> REMAPPED_BLOCK_POS_TAGS = Set.of(
            "Controller",
            "LastKnownPos",
            "Source",
            "Breaking",
            "DestroyEffect",
            "Target",
            "BypassCrushingWheel");

    private PocketFactoryPreviewHelper() {
    }

    public static List<PocketFactoryEntranceBlockEntity.PreviewBlock> sampleFactoryPreview(ServerLevel sourceLevel, int factoryId) {
        if (sourceLevel.getServer() == null || factoryId <= 0) {
            return List.of();
        }

        PocketFactorySavedData.FactoryRecord factory = PocketFactorySavedData.get(sourceLevel.getServer()).getFactory(factoryId);
        ServerLevel factoryLevel = sourceLevel.getServer().getLevel(PocketFactoryDimensions.LEVEL_KEY);
        if (factory == null || factoryLevel == null) {
            return List.of();
        }

        List<PocketFactorySavedData.FactoryChunkOffset> occupiedChunks = new ArrayList<>(factory.occupiedChunks());
        if (occupiedChunks.isEmpty()) {
            return List.of();
        }
        occupiedChunks.sort(Comparator.comparingInt(PocketFactorySavedData.FactoryChunkOffset::x)
                .thenComparingInt(PocketFactorySavedData.FactoryChunkOffset::z));

        int minChunkOffsetX = occupiedChunks.stream().mapToInt(PocketFactorySavedData.FactoryChunkOffset::x).min().orElse(0);
        int minChunkOffsetZ = occupiedChunks.stream().mapToInt(PocketFactorySavedData.FactoryChunkOffset::z).min().orElse(0);
        ChunkPos originChunk = PocketFactoryDimensions.getFactoryChunk(factory, new PocketFactorySavedData.FactoryChunkOffset(minChunkOffsetX, minChunkOffsetZ));
        int originX = originChunk.getMinBlockX();
        int originY = factory.minY();
        int originZ = originChunk.getMinBlockZ();

        List<PocketFactoryEntranceBlockEntity.PreviewBlock> sampled = new ArrayList<>();
        for (PocketFactorySavedData.FactoryChunkOffset chunkOffset : occupiedChunks) {
            ChunkPos chunkPos = PocketFactoryDimensions.getFactoryChunk(factory, chunkOffset);
            for (int y = factory.minY(); y <= factory.maxY(); y++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                        BlockPos targetPos = new BlockPos(x, y, z);
                        BlockState state = factoryLevel.getBlockState(targetPos);
                        if (state.isAir()) {
                            continue;
                        }
                        if (state.is(ModBlocks.POCKET_FACTORY_BLOCK_A.get()) || state.is(ModBlocks.POCKET_FACTORY_BLOCK_B.get())) {
                            continue;
                        }

                        int localX = x - originX;
                        int localY = y - originY;
                        int localZ = z - originZ;
                        sampled.add(new PocketFactoryEntranceBlockEntity.PreviewBlock(
                                localX,
                                localY,
                                localZ,
                                state,
                                captureBlockEntityTag(factoryLevel, targetPos, state, localX, localY, localZ, originX, originY, originZ)));
                    }
                }
            }
        }

        return List.copyOf(sampled);
    }

    public static CompoundTag writePreviewBlocks(List<PocketFactoryEntranceBlockEntity.PreviewBlock> previewBlocks) {
        CompoundTag root = new CompoundTag();
        ListTag previewTag = new ListTag();
        for (PocketFactoryEntranceBlockEntity.PreviewBlock previewBlock : previewBlocks) {
            CompoundTag blockTag = new CompoundTag();
            blockTag.putInt("X", previewBlock.x());
            blockTag.putInt("Y", previewBlock.y());
            blockTag.putInt("Z", previewBlock.z());
            blockTag.put("State", NbtUtils.writeBlockState(previewBlock.state()));
            if (previewBlock.blockEntityTag() != null) {
                blockTag.put("BlockEntity", previewBlock.blockEntityTag().copy());
            }
            previewTag.add(blockTag);
        }
        root.put(PREVIEW_BLOCKS_TAG, previewTag);
        return root;
    }

    public static List<PocketFactoryEntranceBlockEntity.PreviewBlock> readPreviewBlocks(CompoundTag tag, HolderLookup.Provider registries) {
        List<PocketFactoryEntranceBlockEntity.PreviewBlock> loadedPreview = new ArrayList<>();
        ListTag previewTag = tag.getList(PREVIEW_BLOCKS_TAG, Tag.TAG_COMPOUND);
        for (Tag entry : previewTag) {
            CompoundTag blockTag = (CompoundTag) entry;
            BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), blockTag.getCompound("State"));
            loadedPreview.add(new PocketFactoryEntranceBlockEntity.PreviewBlock(
                    blockTag.getInt("X"),
                    blockTag.getInt("Y"),
                    blockTag.getInt("Z"),
                    state,
                    blockTag.contains("BlockEntity", Tag.TAG_COMPOUND) ? blockTag.getCompound("BlockEntity").copy() : null));
        }
        return List.copyOf(loadedPreview);
    }

    private static @Nullable CompoundTag captureBlockEntityTag(ServerLevel factoryLevel, BlockPos targetPos, BlockState state,
                                                               int localX, int localY, int localZ,
                                                               int originX, int originY, int originZ) {
        if (!state.hasBlockEntity()) {
            return null;
        }

        BlockEntity blockEntity = factoryLevel.getBlockEntity(targetPos);
        if (blockEntity == null) {
            return null;
        }

        CompoundTag blockEntityTag = blockEntity.saveWithFullMetadata(factoryLevel.registryAccess());
        blockEntityTag.putInt("x", localX);
        blockEntityTag.putInt("y", localY);
        blockEntityTag.putInt("z", localZ);
        remapBlockPosTags(blockEntityTag, originX, originY, originZ);
        return blockEntityTag;
    }

    private static void remapBlockPosTags(CompoundTag tag, int originX, int originY, int originZ) {
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (REMAPPED_BLOCK_POS_TAGS.contains(key)) {
                BlockPos absolutePos = NbtUtils.readBlockPos(tag, key).orElse(null);
                if (absolutePos != null) {
                    tag.put(key, NbtUtils.writeBlockPos(new BlockPos(
                            absolutePos.getX() - originX,
                            absolutePos.getY() - originY,
                            absolutePos.getZ() - originZ)));
                    continue;
                }
            }

            Tag child = tag.get(key);
            if (child instanceof CompoundTag childCompound) {
                remapBlockPosTags(childCompound, originX, originY, originZ);
            } else if (child instanceof ListTag childList) {
                remapBlockPosTags(childList, originX, originY, originZ);
            }
        }
    }

    private static void remapBlockPosTags(ListTag listTag, int originX, int originY, int originZ) {
        for (Tag entry : listTag) {
            if (entry instanceof CompoundTag entryCompound) {
                remapBlockPosTags(entryCompound, originX, originY, originZ);
            } else if (entry instanceof ListTag nestedList) {
                remapBlockPosTags(nestedList, originX, originY, originZ);
            }
        }
    }
}