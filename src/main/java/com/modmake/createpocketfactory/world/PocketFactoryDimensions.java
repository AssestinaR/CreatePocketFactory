package com.modmake.createpocketfactory.world;

import com.modmake.createpocketfactory.CreatePocketFactory;
import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.data.ReturnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PocketFactoryDimensions {
    public static final int FACTORY_SPACING_BLOCKS = 1024;
    public static final int FACTORY_BASE_OFFSET_BLOCKS = 16;
    public static final int MAX_HORIZONTAL_RADIUS_CHUNKS = 30;
    public static final ResourceKey<Level> LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID, "pocket_factory")
    );
            public static final int DEFAULT_FLOOR_Y = PocketFactorySavedData.DEFAULT_MIN_Y;
            public static final int DEFAULT_CEILING_Y = PocketFactorySavedData.DEFAULT_MAX_Y;

    private static final int WALL_FLAG = 3;

    private PocketFactoryDimensions() {
    }

    public static void initializeFactoryDimension(MinecraftServer server) {
        ServerLevel level = server.getLevel(LEVEL_KEY);
        if (level != null) {
            for (PocketFactorySavedData.FactoryRecord factory : PocketFactorySavedData.get(server).getFactories()) {
                prepareFactory(level, factory);
            }
        }
    }

    public static void prepareFactory(ServerLevel level, PocketFactorySavedData.FactoryRecord factory) {
        for (PocketFactorySavedData.FactoryChunkOffset chunkOffset : factory.occupiedChunks()) {
            ChunkPos factoryChunk = getFactoryChunk(factory, chunkOffset);
            level.setChunkForced(factoryChunk.x, factoryChunk.z, true);
        }

        if (!isStarterRoomGenerated(level, factory)) {
            applyFactoryShell(level, null, factory);
        }
    }

    public static void applyExpansion(ServerLevel level, PocketFactorySavedData.FactoryRecord previousFactory, PocketFactorySavedData.FactoryRecord factory) {
        for (PocketFactorySavedData.FactoryChunkOffset chunkOffset : factory.occupiedChunks()) {
            ChunkPos factoryChunk = getFactoryChunk(factory, chunkOffset);
            level.setChunkForced(factoryChunk.x, factoryChunk.z, true);
        }

        applyFactoryShell(level, previousFactory, factory);
    }

    public static void teleportToFactory(ServerPlayer player, ServerLevel targetLevel, PocketFactorySavedData.FactoryRecord factory) {
        BlockPos targetPos = findPreferredTeleportPos(targetLevel, factory);
        player.teleportTo(targetLevel, targetPos.getX() + 0.5D, targetPos.getY() + 0.1D, targetPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
    }

    public static boolean isReturnPointUsable(ServerLevel level, ReturnPointData point) {
        BlockPos position = BlockPos.containing(point.x(), point.y(), point.z());
        return level.getWorldBorder().isWithinBounds(position);
    }

    public static double getCenterX(PocketFactorySavedData.FactoryRecord factory) {
        return getFactoryBaseChunk(factory).getMinBlockX() + 8.5D;
    }

    public static double getCenterZ(PocketFactorySavedData.FactoryRecord factory) {
        return getFactoryBaseChunk(factory).getMinBlockZ() + 8.5D;
    }

    public static @javax.annotation.Nullable PocketFactorySavedData.FactoryRecord findFactoryAt(PocketFactorySavedData savedData, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        for (PocketFactorySavedData.FactoryRecord factory : savedData.getFactories()) {
            ChunkPos baseChunk = getFactoryBaseChunk(factory);
            int relativeChunkX = chunkX - baseChunk.x;
            int relativeChunkZ = chunkZ - baseChunk.z;
            if (factory.containsChunk(relativeChunkX, relativeChunkZ) && pos.getY() >= factory.minY() && pos.getY() <= factory.maxY()) {
                return factory;
            }
        }

        return null;
    }

    public static PocketFactorySavedData.FactoryChunkOffset getChunkOffsetAt(PocketFactorySavedData.FactoryRecord factory, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ChunkPos baseChunk = getFactoryBaseChunk(factory);
        return new PocketFactorySavedData.FactoryChunkOffset(chunkX - baseChunk.x, chunkZ - baseChunk.z);
    }

    public static boolean isValidHorizontalExpansionFace(PocketFactorySavedData.FactoryRecord factory, BlockPos pos, Direction expansionDirection) {
        PocketFactorySavedData.FactoryChunkOffset chunkOffset = getChunkOffsetAt(factory, pos);
        ChunkPos factoryChunk = getFactoryChunk(factory, chunkOffset);

        return switch (expansionDirection) {
            case EAST -> pos.getX() == factoryChunk.getMaxBlockX() && !factory.containsChunk(chunkOffset.x() + 1, chunkOffset.z());
            case WEST -> pos.getX() == factoryChunk.getMinBlockX() && !factory.containsChunk(chunkOffset.x() - 1, chunkOffset.z());
            case SOUTH -> pos.getZ() == factoryChunk.getMaxBlockZ() && !factory.containsChunk(chunkOffset.x(), chunkOffset.z() + 1);
            case NORTH -> pos.getZ() == factoryChunk.getMinBlockZ() && !factory.containsChunk(chunkOffset.x(), chunkOffset.z() - 1);
            default -> false;
        };
    }

    public static boolean isBottomBoundary(PocketFactorySavedData.FactoryRecord factory, BlockPos pos) {
        return pos.getY() == factory.minY();
    }

    public static boolean isTopBoundary(PocketFactorySavedData.FactoryRecord factory, BlockPos pos) {
        return pos.getY() == factory.maxY();
    }

    private static void applyFactoryShell(ServerLevel level, @javax.annotation.Nullable PocketFactorySavedData.FactoryRecord previousFactory,
                                          PocketFactorySavedData.FactoryRecord factory) {
        BlockState blockA = ModBlocks.POCKET_FACTORY_BLOCK_A.get().defaultBlockState();
        BlockState blockB = ModBlocks.POCKET_FACTORY_BLOCK_B.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int yStart = previousFactory == null ? factory.minY() : Math.min(previousFactory.minY(), factory.minY());
        int yEnd = previousFactory == null ? factory.maxY() : Math.max(previousFactory.maxY(), factory.maxY());

        for (PocketFactorySavedData.FactoryChunkOffset chunkOffset : factory.occupiedChunks()) {
            boolean chunkPreviouslyPresent = previousFactory != null && previousFactory.containsChunk(chunkOffset.x(), chunkOffset.z());
            ChunkPos factoryChunk = getFactoryChunk(factory, chunkOffset);
            int minX = factoryChunk.getMinBlockX();
            int maxX = factoryChunk.getMaxBlockX();
            int minZ = factoryChunk.getMinBlockZ();
            int maxZ = factoryChunk.getMaxBlockZ();
            boolean westExposed = !factory.containsChunk(chunkOffset.x() - 1, chunkOffset.z());
            boolean eastExposed = !factory.containsChunk(chunkOffset.x() + 1, chunkOffset.z());
            boolean northExposed = !factory.containsChunk(chunkOffset.x(), chunkOffset.z() - 1);
            boolean southExposed = !factory.containsChunk(chunkOffset.x(), chunkOffset.z() + 1);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = yStart; y <= yEnd; y++) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        boolean withinCurrentBounds = y >= factory.minY() && y <= factory.maxY();

                        if (!withinCurrentBounds) {
                            if (isFactoryBoundary(level.getBlockState(blockPos))) {
                                level.setBlock(blockPos, air, WALL_FLAG);
                            }
                            continue;
                        }

                        boolean floor = y == factory.minY();
                        boolean shell = y == factory.maxY()
                                || (westExposed && x == minX)
                                || (eastExposed && x == maxX)
                                || (northExposed && z == minZ)
                                || (southExposed && z == maxZ);
                        BlockState currentState = level.getBlockState(blockPos);

                        if (floor || shell) {
                            BlockState desiredState = checkerboardState(x + y, z, blockA, blockB);
                            if (!currentState.is(desiredState.getBlock())) {
                                level.setBlock(blockPos, desiredState, WALL_FLAG);
                            }
                        } else if (!chunkPreviouslyPresent || isFactoryBoundary(currentState)) {
                            if (!currentState.isAir()) {
                                level.setBlock(blockPos, air, WALL_FLAG);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isStarterRoomGenerated(ServerLevel level, PocketFactorySavedData.FactoryRecord factory) {
        ChunkPos factoryChunk = getFactoryBaseChunk(factory);
        BlockPos floorMarker = new BlockPos(factoryChunk.getMinBlockX() + 8, factory.minY(), factoryChunk.getMinBlockZ() + 8);
        BlockPos ceilingMarker = new BlockPos(factoryChunk.getMinBlockX() + 8, factory.maxY(), factoryChunk.getMinBlockZ() + 8);
        return (level.getBlockState(floorMarker).is(ModBlocks.POCKET_FACTORY_BLOCK_A.get())
                || level.getBlockState(floorMarker).is(ModBlocks.POCKET_FACTORY_BLOCK_B.get()))
                && (level.getBlockState(ceilingMarker).is(ModBlocks.POCKET_FACTORY_BLOCK_A.get())
                || level.getBlockState(ceilingMarker).is(ModBlocks.POCKET_FACTORY_BLOCK_B.get()));
    }

    public static ChunkPos getFactoryBaseChunk(PocketFactorySavedData.FactoryRecord factory) {
        int minX = FACTORY_BASE_OFFSET_BLOCKS + (factory.slotX() * FACTORY_SPACING_BLOCKS);
        int minZ = FACTORY_BASE_OFFSET_BLOCKS + (factory.slotZ() * FACTORY_SPACING_BLOCKS);
        return new ChunkPos(minX >> 4, minZ >> 4);
    }

    public static ChunkPos getFactoryChunk(PocketFactorySavedData.FactoryRecord factory, PocketFactorySavedData.FactoryChunkOffset chunkOffset) {
        ChunkPos baseChunk = getFactoryBaseChunk(factory);
        return new ChunkPos(baseChunk.x + chunkOffset.x(), baseChunk.z + chunkOffset.z());
    }

    private static boolean isFactoryBoundary(BlockState state) {
        return state.is(ModBlocks.POCKET_FACTORY_BLOCK_A.get()) || state.is(ModBlocks.POCKET_FACTORY_BLOCK_B.get());
    }

    private static BlockState checkerboardState(int first, int second, BlockState a, BlockState b) {
        return ((first + second) & 1) == 0 ? a : b;
    }

    private static BlockPos findPreferredTeleportPos(ServerLevel level, PocketFactorySavedData.FactoryRecord factory) {
        BlockPos carpetPos = findFirstMatchingTeleportPos(level, factory, true);
        if (carpetPos != null) {
            return carpetPos;
        }

        BlockPos openPos = findFirstMatchingTeleportPos(level, factory, false);
        if (openPos != null) {
            return openPos;
        }

        return BlockPos.containing(getCenterX(factory), factory.minY() + 1, getCenterZ(factory));
    }

    private static BlockPos findFirstMatchingTeleportPos(ServerLevel level, PocketFactorySavedData.FactoryRecord factory, boolean requireCarpet) {
        for (PocketFactorySavedData.FactoryChunkOffset chunkOffset : factory.occupiedChunks()) {
            ChunkPos factoryChunk = getFactoryChunk(factory, chunkOffset);
            int minX = factoryChunk.getMinBlockX() + 1;
            int maxX = factoryChunk.getMaxBlockX() - 1;
            int minZ = factoryChunk.getMinBlockZ() + 1;
            int maxZ = factoryChunk.getMaxBlockZ() - 1;
            for (int y = factory.minY() + 1; y < factory.maxY(); y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos feetPos = new BlockPos(x, y, z);
                        if (!isSafeTeleportPos(level, feetPos, requireCarpet)) {
                            continue;
                        }
                        return feetPos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafeTeleportPos(ServerLevel level, BlockPos feetPos, boolean requireCarpet) {
        BlockPos belowPos = feetPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(feetPos.above());

        if (requireCarpet) {
            if (!belowState.is(BlockTags.WOOL_CARPETS)) {
                return false;
            }
        } else {
            if (belowState.isAir() || belowState.is(BlockTags.WOOL_CARPETS)) {
                return false;
            }
            if (!belowState.blocksMotion()) {
                return false;
            }
        }

        return !feetState.blocksMotion() && !headState.blocksMotion();
    }
}