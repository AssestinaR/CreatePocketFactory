package com.modmake.createpocketfactory.block;

import com.modmake.createpocketfactory.block.entity.LinkedPipeBindingHelper;
import com.modmake.createpocketfactory.block.entity.LinkedPipeEndpoint;
import com.modmake.createpocketfactory.block.entity.LinkedStraightPipeBlockEntity;
import com.modmake.createpocketfactory.block.entity.ModBlockEntities;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.createmod.catnip.data.Iterate;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class LinkedGlassFluidPipeBlock extends GlassFluidPipeBlock {
    public LinkedGlassFluidPipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<StraightPipeBlockEntity> getBlockEntityClass() {
        return (Class<StraightPipeBlockEntity>) (Class<?>) LinkedStraightPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LinkedStraightPipeBlockEntity> getBlockEntityType() {
        return ModBlockEntities.LINKED_GLASS_FLUID_PIPE.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && !(level.getBlockEntity(pos) instanceof LinkedPipeEndpoint linkedPipe && linkedPipe.hasBinding())) {
            level.setBlock(pos, LinkedPipeBindingHelper.toVanillaState(state), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !world.isClientSide && world.getServer() != null
                && !LinkedPipeBindingHelper.isLinkedPipe(newState)
                && world.getBlockEntity(pos) instanceof LinkedPipeEndpoint linkedPipe
                && linkedPipe.hasBinding()) {
            PocketFactorySavedData savedData = PocketFactorySavedData.get(world.getServer());
            PocketFactorySavedData.BindingEndpoints endpoints = savedData.getBindingEndpoints(
                    linkedPipe.getBindingId(),
                    PocketFactorySavedData.BindingChannel.LINKED_PIPE);
            savedData.disposeBinding(linkedPipe.getBindingId(), PocketFactorySavedData.BindingChannel.LINKED_PIPE);
            LinkedPipeBindingHelper.restoreOppositeEndpoint(world, pos, endpoints);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (tryRemoveBracket(context)) {
            return InteractionResult.SUCCESS;
        }
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!world.isClientSide) {
            LinkedPipeEndpoint linkedPipe = LinkedPipeBindingHelper.getLinkedPipe(world, pos);
            int bindingId = linkedPipe == null ? -1 : linkedPipe.getBindingId();
            int factoryId = linkedPipe == null ? -1 : linkedPipe.getFactoryId();
            boolean internalEndpoint = linkedPipe != null && linkedPipe.isInternalEndpoint();
            BlockState newState = LinkedPipeBindingHelper.toLinkedRegularState(world, pos, state);
            LinkedPipeBindingHelper.switchVariant(world, pos, newState, bindingId, factoryId, internalEndpoint);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (!AllBlocks.COPPER_CASING.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        LinkedPipeEndpoint linkedPipe = LinkedPipeBindingHelper.getLinkedPipe(level, pos);
        int bindingId = linkedPipe == null ? -1 : linkedPipe.getBindingId();
        int factoryId = linkedPipe == null ? -1 : linkedPipe.getFactoryId();
        boolean internalEndpoint = linkedPipe != null && linkedPipe.isInternalEndpoint();
        BlockState newState = ModBlocks.LINKED_ENCASED_FLUID_PIPE.get().defaultBlockState();
        for (net.minecraft.core.Direction direction : Iterate.directionsInAxis(state.getValue(AxisPipeBlock.AXIS))) {
            newState = newState.setValue(com.simibubi.create.content.fluids.pipes.EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(direction), true);
        }
        LinkedPipeBindingHelper.switchVariant(level, pos, newState, bindingId, factoryId, internalEndpoint);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(AllBlocks.FLUID_PIPE.asStack());
    }
}
