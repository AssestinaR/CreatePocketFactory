package com.assestinar.createpocketfactory.client;

import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.block.entity.LinkedChuteBlockEntity;
import com.assestinar.createpocketfactory.block.entity.LinkedClutchBlockEntity;
import com.assestinar.createpocketfactory.block.entity.LinkedFluidTankBlockEntity;
import com.assestinar.createpocketfactory.block.entity.LinkedItemVaultBlockEntity;
import com.assestinar.createpocketfactory.block.entity.LinkedPumpBlockEntity;
import com.assestinar.createpocketfactory.config.ModConfigs;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CreatePocketFactory.MOD_ID, value = Dist.CLIENT)
public final class LinkedBlockParticleHandler {
    private static final int SCAN_INTERVAL_TICKS = 6;
    private static final int HASH_PERIOD = 12;
    private static final int MAX_SPAWNS_PER_SCAN = 1;
    private static final double MAX_DISTANCE_SQR = 12.0D * 12.0D;
    private static final double MIN_VIEW_DOT = 0.2D;
    private static final DustParticleOptions MARKER_PARTICLE = new DustParticleOptions(new Vector3f(0.62F, 0.38F, 0.88F), 0.7F);

    private LinkedBlockParticleHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (minecraft.isPaused() || player == null || level == null || !ModConfigs.showLinkedBlockParticles()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % SCAN_INTERVAL_TICKS != 0L) {
            return;
        }

        Vec3 eyePosition = player.getEyePosition();
        Vec3 lookDirection = player.getViewVector(1.0F).normalize();
        ChunkPos playerChunk = new ChunkPos(BlockPos.containing(eyePosition));
        int chunkRadius = 1;
        int spawned = 0;
        Set<BlockPos> visitedControllers = new HashSet<>();

        for (int chunkX = playerChunk.x - chunkRadius; chunkX <= playerChunk.x + chunkRadius; chunkX++) {
            for (int chunkZ = playerChunk.z - chunkRadius; chunkZ <= playerChunk.z + chunkRadius; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockEntity target = resolveParticleTarget(blockEntity);
                    if (target == null || !visitedControllers.add(target.getBlockPos())) {
                        continue;
                    }

                    Vec3 spawnPosition = resolveSpawnPosition(level, target);
                    Vec3 toParticle = spawnPosition.subtract(eyePosition);
                    double distanceSqr = toParticle.lengthSqr();
                    if (distanceSqr > MAX_DISTANCE_SQR || distanceSqr < 0.0001D) {
                        continue;
                    }

                    Vec3 toParticleDirection = toParticle.normalize();
                    if (toParticleDirection.dot(lookDirection) < MIN_VIEW_DOT) {
                        continue;
                    }

                    if (!shouldEmitThisScan(target.getBlockPos(), gameTime)) {
                        continue;
                    }

                    spawnMarkerParticle(level, spawnPosition);
                    spawned++;
                    if (spawned >= MAX_SPAWNS_PER_SCAN) {
                        return;
                    }
                }
            }
        }
    }

    private static BlockEntity resolveParticleTarget(BlockEntity blockEntity) {
        if (blockEntity instanceof LinkedChuteBlockEntity chute) {
            return chute.hasBinding() ? chute : null;
        }
        if (blockEntity instanceof LinkedClutchBlockEntity clutch) {
            return clutch.hasBinding() ? clutch : null;
        }
        if (blockEntity instanceof LinkedPumpBlockEntity pump) {
            return pump.hasBinding() ? pump : null;
        }
        if (blockEntity instanceof LinkedItemVaultBlockEntity vault) {
            if (vault.isController()) {
                return vault.hasBinding() ? vault : null;
            }
            BlockEntity controller = vault.getControllerBE();
            return controller instanceof LinkedItemVaultBlockEntity linkedController && linkedController.hasBinding()
                    ? linkedController
                    : null;
        }
        if (blockEntity instanceof LinkedFluidTankBlockEntity tank) {
            if (tank.isController()) {
                return tank.hasBinding() ? tank : null;
            }
            BlockEntity controller = tank.getControllerBE();
            return controller instanceof LinkedFluidTankBlockEntity linkedController && linkedController.hasBinding()
                    ? linkedController
                    : null;
        }
        return null;
    }

    private static boolean shouldEmitThisScan(BlockPos pos, long gameTime) {
        long scanIndex = gameTime / SCAN_INTERVAL_TICKS;
        return Math.floorMod((int) scanIndex + pos.hashCode(), HASH_PERIOD) == 0;
    }

    private static Vec3 resolveSpawnPosition(Level level, BlockEntity target) {
        if (target instanceof LinkedItemVaultBlockEntity vault) {
            ItemVaultBlockEntity controller = vault.isController() ? vault : vault.getControllerBE();
            if (controller != null) {
                return resolveTopPlaneSpawnPosition(level, resolveVaultBounds(controller));
            }
        }

        if (target instanceof LinkedFluidTankBlockEntity tank) {
            FluidTankBlockEntity controller = tank.isController() ? tank : tank.getControllerBE();
            if (controller != null) {
                return resolveTopPlaneSpawnPosition(level, resolveTankBounds(controller));
            }
        }

        BlockPos pos = target.getBlockPos();
        BlockState state = target.getBlockState();
        BlockPos abovePos = pos.above();
        if (isOpenSpawnSpace(level, abovePos)) {
            return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.02D, pos.getZ() + 0.5D);
        }

        Direction[] horizontalOrder = horizontalDirectionsFor(pos);
        for (Direction direction : horizontalOrder) {
            BlockPos sidePos = pos.relative(direction);
            if (isOpenSpawnSpace(level, sidePos)) {
                Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
                return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.72D, pos.getZ() + 0.5D).add(normal.scale(0.56D));
            }
        }

        double topY = pos.getY() + Math.max(0.75D, state.getShape(level, pos).bounds().maxY + 0.02D);
        return new Vec3(pos.getX() + 0.5D, topY, pos.getZ() + 0.5D);
    }

    private static Vec3 resolveTopPlaneSpawnPosition(Level level, AABB bounds) {
        double margin = 0.15D;
        double minX = bounds.minX + margin;
        double maxX = bounds.maxX - margin;
        double minZ = bounds.minZ + margin;
        double maxZ = bounds.maxZ - margin;

        if (minX > maxX) {
            minX = maxX = (bounds.minX + bounds.maxX) * 0.5D;
        }
        if (minZ > maxZ) {
            minZ = maxZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        }

        double x = minX + level.random.nextDouble() * Math.max(0.0001D, maxX - minX);
        double z = minZ + level.random.nextDouble() * Math.max(0.0001D, maxZ - minZ);
        double y = bounds.maxY + 0.02D;
        return new Vec3(x, y, z);
    }

    private static AABB resolveVaultBounds(ItemVaultBlockEntity controller) {
        BlockPos origin = controller.getBlockPos();
        int width = controller.getWidth();
        int height = controller.getHeight();
        return switch (controller.getMainConnectionAxis()) {
            case X -> blockBounds(origin, height, width, width);
            case Y -> blockBounds(origin, width, height, width);
            case Z -> blockBounds(origin, width, width, height);
        };
    }

    private static AABB resolveTankBounds(FluidTankBlockEntity controller) {
        return blockBounds(controller.getBlockPos(), controller.getWidth(), controller.getHeight(), controller.getWidth());
    }

    private static AABB blockBounds(BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + sizeX, origin.getY() + sizeY, origin.getZ() + sizeZ);
    }

    private static boolean isOpenSpawnSpace(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static Direction[] horizontalDirectionsFor(BlockPos pos) {
        int index = Math.floorMod(pos.hashCode(), 4);
        return switch (index) {
            case 0 -> new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            case 1 -> new Direction[] {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
            case 2 -> new Direction[] {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
            default -> new Direction[] {Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};
        };
    }

    private static void spawnMarkerParticle(Level level, Vec3 pos) {
        double upwardSpeed = 0.0125D + level.random.nextDouble() * 0.005D;
        level.addParticle(MARKER_PARTICLE, pos.x, pos.y, pos.z, 0.0D, upwardSpeed, 0.0D);
    }
}