package com.modmake.createpocketfactory.item;

import com.modmake.createpocketfactory.block.entity.LinkedFluidTankBlockEntity;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.fluids.FluidStack;

public class LinkedFluidTankBlockItem extends BlockItem {
    public LinkedFluidTankBlockItem(Block block, Properties properties) {
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
            nbt.remove("Luminosity");
            nbt.remove("Size");
            nbt.remove("Height");
            nbt.remove("Controller");
            nbt.remove("LastKnownPos");
            if (nbt.contains("TankContent")) {
                FluidStack fluid = FluidStack.parseOptional(server.registryAccess(), nbt.getCompound("TankContent"));
                if (!fluid.isEmpty()) {
                    fluid.setAmount(Math.min(FluidTankBlockEntity.getCapacityMultiplier(), fluid.getAmount()));
                    nbt.put("TankContent", fluid.saveOptional(server.registryAccess()));
                }
            }
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
        if (!face.getAxis().isVertical()) {
            return;
        }
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

        LinkedFluidTankBlockEntity tankAt = ConnectivityHandler.partAt(ModBlockEntities.LINKED_FLUID_TANK.get(), world, placedOnPos);
        if (tankAt == null) {
            return;
        }
        FluidTankBlockEntity controller = tankAt.getControllerBE();
        if (!(controller instanceof LinkedFluidTankBlockEntity linkedController)) {
            return;
        }

        int width = linkedController.getWidth();
        if (width == 1) {
            return;
        }

        BlockPos startPos = face == Direction.DOWN ? linkedController.getBlockPos().below() : linkedController.getBlockPos().above(linkedController.getHeight());
        if (startPos.getY() != pos.getY()) {
            return;
        }

        int blocksToPlace = 0;
        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
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
                BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
                if (world.getBlockState(offsetPos).getBlock() == getBlock()) {
                    continue;
                }
                BlockPlaceContext multiPlaceContext = BlockPlaceContext.at(context, offsetPos, face);
                player.getPersistentData().putBoolean("SilenceTankSound", true);
                super.place(multiPlaceContext);
                player.getPersistentData().remove("SilenceTankSound");
            }
        }
    }
}