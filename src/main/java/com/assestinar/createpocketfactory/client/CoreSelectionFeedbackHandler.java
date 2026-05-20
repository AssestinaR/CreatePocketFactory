package com.assestinar.createpocketfactory.client;

import com.assestinar.createpocketfactory.block.entity.LinkedClutchBlockEntity;
import com.assestinar.createpocketfactory.item.PocketFactoryCoreItem;
import com.assestinar.createpocketfactory.item.PocketFactoryCoreItem.EndpointKind;
import com.assestinar.createpocketfactory.item.PocketFactoryCoreItem.HoveredEndpoint;
import com.assestinar.createpocketfactory.item.PocketFactoryCoreItem.SelectedEndpoint;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = com.assestinar.createpocketfactory.CreatePocketFactory.MOD_ID, value = Dist.CLIENT)
public final class CoreSelectionFeedbackHandler {
    private static final String SELECTED_OUTLINE_SLOT = "create_pocket_factory_core_selection";
    private static final String HOVERED_OUTLINE_SLOT = "create_pocket_factory_core_hover";
    private static final int SELECTED_COLOR = 0xD2B46D;
    private static final int AVAILABLE_COLOR = 0x5DBB63;
    private static final int OUTPUT_COLOR = 0x4A90E2;
    private static final int BLOCKED_COLOR = 0xD95C5C;
    private static final double FACE_SLICE_THICKNESS = 1 / 16d;

    private CoreSelectionFeedbackHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof PocketFactoryCoreItem)) {
            return;
        }

        SelectedEndpoint endpoint = PocketFactoryCoreItem.getSelectedEndpoint(heldItem);
        if (endpoint != null && level.dimension().equals(endpoint.dimension())) {
            drawSelectedFeedback(level, endpoint);
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        HoveredEndpoint hoveredEndpoint = PocketFactoryCoreItem.getHoveredEndpoint(level, blockHitResult.getBlockPos(), endpoint);
        if (hoveredEndpoint == null || endpoint != null && endpoint.matches(hoveredEndpoint)) {
            return;
        }

        drawHoveredFeedback(level, blockHitResult, hoveredEndpoint);
    }

    private static void drawOutline(String slot, AABB bounds, int color) {
        Outliner.getInstance().showAABB(slot, bounds)
                .colored(color)
                .lineWidth(1 / 16f);
    }

    private static void drawSelectedFeedback(Level level, SelectedEndpoint endpoint) {
        if (endpoint.kind() == EndpointKind.CLUTCH && drawClutchRoleOutlines(level, endpoint.pos(), SELECTED_OUTLINE_SLOT)) {
            return;
        }

        drawOutline(SELECTED_OUTLINE_SLOT, resolveBounds(level, endpoint.pos()), SELECTED_COLOR);
    }

    private static void drawHoveredFeedback(Level level, BlockHitResult hitResult, HoveredEndpoint hoveredEndpoint) {
        if (hoveredEndpoint.kind() == EndpointKind.CLUTCH) {
            drawFaceOutline(HOVERED_OUTLINE_SLOT, fullBlockBounds(hoveredEndpoint.pos()), hitResult.getDirection(),
                    hoveredEndpoint.isAvailable() ? AVAILABLE_COLOR : BLOCKED_COLOR);
            drawClutchRoleOutlines(level, hoveredEndpoint.pos(), HOVERED_OUTLINE_SLOT + "_role");
            return;
        }

        drawOutline(HOVERED_OUTLINE_SLOT, resolveBounds(level, hoveredEndpoint.pos()), hoveredEndpoint.isAvailable() ? AVAILABLE_COLOR : BLOCKED_COLOR);
    }

    private static boolean drawClutchRoleOutlines(Level level, BlockPos pos, String slotPrefix) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof LinkedClutchBlockEntity clutch)) {
            return false;
        }

        AABB bounds = fullBlockBounds(pos);
        boolean drewAny = false;

        if (clutch.getDisplayedInputFace() != null) {
            drawFaceOutline(slotPrefix + "_input", bounds, clutch.getDisplayedInputFace(), AVAILABLE_COLOR);
            drewAny = true;
        }

        if (clutch.getDisplayedOutputFace() != null) {
            drawFaceOutline(slotPrefix + "_output", bounds, clutch.getDisplayedOutputFace(), OUTPUT_COLOR);
            drewAny = true;
        }

        return drewAny;
    }

    private static void drawFaceOutline(String slot, AABB bounds, net.minecraft.core.Direction face, int color) {
        drawOutline(slot, resolveFaceBounds(bounds, face), color);
    }

    private static AABB resolveFaceBounds(AABB bounds, net.minecraft.core.Direction face) {
        double minX = bounds.minX;
        double minY = bounds.minY;
        double minZ = bounds.minZ;
        double maxX = bounds.maxX;
        double maxY = bounds.maxY;
        double maxZ = bounds.maxZ;
        double thickness = Math.min(FACE_SLICE_THICKNESS, Math.min(maxX - minX, Math.min(maxY - minY, maxZ - minZ)));

        return switch (face) {
            case DOWN -> new AABB(minX, minY, minZ, maxX, minY + thickness, maxZ);
            case UP -> new AABB(minX, maxY - thickness, minZ, maxX, maxY, maxZ);
            case NORTH -> new AABB(minX, minY, minZ, maxX, maxY, minZ + thickness);
            case SOUTH -> new AABB(minX, minY, maxZ - thickness, maxX, maxY, maxZ);
            case WEST -> new AABB(minX, minY, minZ, minX + thickness, maxY, maxZ);
            case EAST -> new AABB(maxX - thickness, minY, minZ, maxX, maxY, maxZ);
        };
    }

    private static AABB fullBlockBounds(BlockPos pos) {
        return blockBounds(pos, 1, 1, 1);
    }

    private static AABB resolveBounds(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ItemVaultBlockEntity vault) {
            ItemVaultBlockEntity controller = vault.getControllerBE();
            if (controller != null) {
                int width = controller.getWidth();
                int height = controller.getHeight();
                return switch (controller.getMainConnectionAxis()) {
                    case X -> blockBounds(controller.getBlockPos(), height, width, width);
                    case Y -> blockBounds(controller.getBlockPos(), width, height, width);
                    case Z -> blockBounds(controller.getBlockPos(), width, width, height);
                };
            }
        }

        if (blockEntity instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity controller = tank.getControllerBE();
            if (controller != null) {
                return blockBounds(controller.getBlockPos(), controller.getWidth(), controller.getHeight(), controller.getWidth());
            }
        }

        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    private static AABB blockBounds(BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + sizeX, origin.getY() + sizeY, origin.getZ() + sizeZ);
    }
}