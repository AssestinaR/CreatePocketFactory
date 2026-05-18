package com.modmake.createpocketfactory.client;

import com.modmake.createpocketfactory.item.PocketFactoryCoreItem;
import com.modmake.createpocketfactory.item.PocketFactoryCoreItem.HoveredEndpoint;
import com.modmake.createpocketfactory.item.PocketFactoryCoreItem.SelectedEndpoint;
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

@EventBusSubscriber(modid = com.modmake.createpocketfactory.CreatePocketFactory.MOD_ID, value = Dist.CLIENT)
public final class CoreSelectionFeedbackHandler {
    private static final String SELECTED_OUTLINE_SLOT = "create_pocket_factory_core_selection";
    private static final String HOVERED_OUTLINE_SLOT = "create_pocket_factory_core_hover";
    private static final int SELECTED_COLOR = 0xD2B46D;
    private static final int AVAILABLE_COLOR = 0x5DBB63;
    private static final int BLOCKED_COLOR = 0xD95C5C;

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
            drawOutline(SELECTED_OUTLINE_SLOT, resolveBounds(level, endpoint.pos()), SELECTED_COLOR);
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        HoveredEndpoint hoveredEndpoint = PocketFactoryCoreItem.getHoveredEndpoint(level, blockHitResult.getBlockPos(), endpoint);
        if (hoveredEndpoint == null || endpoint != null && endpoint.matches(hoveredEndpoint)) {
            return;
        }

        drawOutline(HOVERED_OUTLINE_SLOT, resolveBounds(level, hoveredEndpoint.pos()), hoveredEndpoint.isAvailable() ? AVAILABLE_COLOR : BLOCKED_COLOR);
    }

    private static void drawOutline(String slot, AABB bounds, int color) {
        Outliner.getInstance().showAABB(slot, bounds)
                .colored(color)
                .lineWidth(1 / 16f);
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