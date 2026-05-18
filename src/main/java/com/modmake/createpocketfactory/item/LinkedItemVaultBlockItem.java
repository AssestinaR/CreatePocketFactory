package com.modmake.createpocketfactory.item;

import com.modmake.createpocketfactory.block.LinkedItemVaultBlock;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.modmake.createpocketfactory.block.entity.LinkedItemVaultBlockEntity;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LinkedItemVaultBlockItem extends BlockItem {
    public LinkedItemVaultBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult initialResult = super.place(context);
        if (!initialResult.consumesAction()) {
            return initialResult;
        }
        tryMultiPlace(context);
        return initialResult;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos blockPos, Level level, Player player, ItemStack itemStack, BlockState blockState) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }
        CustomData blockEntityData = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData != null) {
            CompoundTag nbt = blockEntityData.copyTag();
            nbt.remove("Length");
            nbt.remove("Size");
            nbt.remove("Controller");
            nbt.remove("LastKnownPos");
            BlockEntity.addEntityType(nbt, ((IBE<?>) getBlock()).getBlockEntityType());
            itemStack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(nbt));
        }
        return super.updateCustomBlockEntityTag(blockPos, level, player, itemStack, blockState);
    }

    private void tryMultiPlace(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        Direction face = context.getClickedFace();
        ItemStack stack = context.getItemInHand();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos placedOnPos = pos.relative(face.getOpposite());
        BlockState placedOnState = world.getBlockState(placedOnPos);

        if (placedOnState.getBlock() != getBlock()) {
            return;
        }
        if (SymmetryWandItem.presentInHotbar(player)) {
            return;
        }

        LinkedItemVaultBlockEntity vaultAt = ConnectivityHandler.partAt(ModBlockEntities.LINKED_ITEM_VAULT.get(), world, placedOnPos);
        if (vaultAt == null) {
            return;
        }
        LinkedItemVaultBlockEntity controller = (LinkedItemVaultBlockEntity) vaultAt.getControllerBE();
        if (controller == null) {
            return;
        }

        int width = controller.getWidth();
        if (width == 1) {
            return;
        }

        Axis vaultAxis = LinkedItemVaultBlock.getVaultAxis(placedOnState);
        if (vaultAxis == null || face.getAxis() != vaultAxis) {
            return;
        }

        Direction vaultFacing = Direction.fromAxisAndDirection(vaultAxis, AxisDirection.POSITIVE);
        BlockPos startPos = face == vaultFacing.getOpposite()
                ? controller.getBlockPos().relative(vaultFacing.getOpposite())
            : controller.getBlockPos().relative(vaultFacing, controller.getHeight());

        if (VecHelper.getCoordinate(startPos, vaultAxis) != VecHelper.getCoordinate(pos, vaultAxis)) {
            return;
        }

        int blocksToPlace = 0;
        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = vaultAxis == Axis.X ? startPos.offset(0, xOffset, zOffset) : startPos.offset(xOffset, zOffset, 0);
                BlockState blockState = world.getBlockState(offsetPos);
                if (blockState.getBlock() == getBlock()) {
                    continue;
                }
                if (!blockState.canBeReplaced()) {
                    return;
                }
                blocksToPlace++;
            }
        }

        if (!player.isCreative() && stack.getCount() < blocksToPlace) {
            return;
        }

        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = vaultAxis == Axis.X ? startPos.offset(0, xOffset, zOffset) : startPos.offset(xOffset, zOffset, 0);
                if (world.getBlockState(offsetPos).getBlock() == getBlock()) {
                    continue;
                }
                BlockPlaceContext multiPlaceContext = BlockPlaceContext.at(context, offsetPos, face);
                player.getPersistentData().putBoolean("SilenceVaultSound", true);
                super.place(multiPlaceContext);
                player.getPersistentData().remove("SilenceVaultSound");
            }
        }
    }
}