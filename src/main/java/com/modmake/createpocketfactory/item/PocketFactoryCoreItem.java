package com.modmake.createpocketfactory.item;

import com.modmake.createpocketfactory.block.LinkedChuteBlock;
import com.modmake.createpocketfactory.block.entity.LinkedClutchBindingHelper;
import com.modmake.createpocketfactory.block.entity.LinkedPumpBindingHelper;
import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.block.PocketFactoryEntranceBlock;
import com.modmake.createpocketfactory.block.PocketFactoryPortalBlock;
import com.modmake.createpocketfactory.block.entity.LinkedChuteBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedClutchEndpoint;
import com.modmake.createpocketfactory.block.entity.LinkedFluidTankBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedItemVaultBlockEntity;
import com.modmake.createpocketfactory.block.entity.LinkedPumpEndpoint;
import com.modmake.createpocketfactory.block.entity.BindingEndpointHelper;
import com.modmake.createpocketfactory.block.entity.LinkedStorageBindingHelper;
import com.modmake.createpocketfactory.block.entity.PocketFactoryPortalBlockEntity;
import com.modmake.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.modmake.createpocketfactory.world.LinkedStorageManualBindingHelper;
import com.modmake.createpocketfactory.world.PocketFactoryDimensions;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.logistics.chute.ChuteBlock;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

public final class PocketFactoryCoreItem extends Item {
    private static final String PENDING_ENDPOINT_TAG = "PendingEndpoint";
    private static final String TARGET_DIMENSION_TAG = "Dimension";
    private static final String TARGET_X_TAG = "X";
    private static final String TARGET_Y_TAG = "Y";
    private static final String TARGET_Z_TAG = "Z";
    private static final String TARGET_KIND_TAG = "Kind";
    private static final Style SELECTION_TOOLTIP_STYLE = Style.EMPTY.withColor(TextColor.fromRgb(0xB7BFD0));

    public PocketFactoryCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        BlockState state = level.getBlockState(origin);
        ItemStack stack = context.getItemInHand();

        LinkedStorageManualBindingHelper.StorageKind storageKind = LinkedStorageManualBindingHelper.getStorageKind(state);
        if (storageKind != null && LinkedStorageManualBindingHelper.isNormalStorage(state)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return handleStorageClick((ServerLevel) level, origin, storageKind, stack, context.getPlayer());
        }

        if (state.getBlock() instanceof PocketFactoryEntranceBlock) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return handleEntranceClick((ServerLevel) level, origin, stack, context.getPlayer());
        }

        if (state.getBlock() instanceof PocketFactoryPortalBlock) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return handlePortalClick((ServerLevel) level, origin, stack, context.getPlayer());
        }

        if (isBindableChute(state) || state.getBlock() instanceof LinkedChuteBlock) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return handleChuteClick((ServerLevel) level, origin, stack, context.getPlayer());
        }

        if (LinkedClutchBindingHelper.isBindableClutch(state) || LinkedClutchBindingHelper.isLinkedClutch(state)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return handleClutchClick((ServerLevel) level, origin, stack, context.getPlayer());
        }

        if (LinkedPumpBindingHelper.isBindablePump(state) || LinkedPumpBindingHelper.isLinkedPump(state)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return handlePumpClick((ServerLevel) level, origin, stack, context.getPlayer());
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_internal_eye.expand"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_internal_eye.shift"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_internal_eye.link_vault"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_internal_eye.link_tank"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_internal_eye.link_chute"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_internal_eye.link_clutch"));
        tooltipComponents.add(Component.translatable("tooltip.create_pocket_factory.pocket_factory_internal_eye.link_pump"));

        SelectedEndpoint endpoint = getSelectedEndpoint(stack);
        if (endpoint != null) {
            tooltipComponents.add(Component.translatable(endpoint.kind().tooltipKey()).withStyle(SELECTION_TOOLTIP_STYLE));
            tooltipComponents.add(Component.literal(endpoint.pos().getX() + ", " + endpoint.pos().getY() + ", " + endpoint.pos().getZ())
                    .withStyle(SELECTION_TOOLTIP_STYLE));
        }
    }

    private static InteractionResult handleStorageClick(ServerLevel level, BlockPos origin,
                                                        LinkedStorageManualBindingHelper.StorageKind kind,
                                                        ItemStack stack, @Nullable Player player) {
        PendingEndpoint pendingEndpoint = getPendingEndpoint(stack);
        if (pendingEndpoint != null && pendingEndpoint.kind().storageKind() != null) {
            if (pendingEndpoint.dimension().equals(level.dimension()) && pendingEndpoint.pos().equals(origin)
                    && pendingEndpoint.storageKind() == kind) {
                clearPendingEndpoint(stack);
                notifyPlayer(player, "Storage selection cleared.");
                return InteractionResult.CONSUME;
            }

            if (pendingEndpoint.storageKind() == kind) {
                return handleDirectStorageBinding(level, origin, kind, stack, player, pendingEndpoint);
            }

            notifyPlayer(player, "Select another storage of the same type.");
            return InteractionResult.FAIL;
        }

        setPendingEndpoint(stack, level, origin, EndpointKind.fromStorageKind(kind));
        notifyPlayer(player, "Storage selected. Use the core on another matching storage to finish linking.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult handleDirectStorageBinding(ServerLevel level, BlockPos origin,
                                                                LinkedStorageManualBindingHelper.StorageKind kind,
                                                                ItemStack stack, @Nullable Player player,
                                                                PendingEndpoint pendingEndpoint) {
        ServerLevel pendingLevel = level.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Selected storage is no longer available.");
            return InteractionResult.FAIL;
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(level.dimension());
        if (pendingInsidePocket == currentInsidePocket) {
            notifyPlayer(player, "Select one storage inside a pocket factory and one storage outside it.");
            return InteractionResult.FAIL;
        }

        ServerLevel internalLevel = pendingInsidePocket ? pendingLevel : level;
        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : origin;
        ServerLevel externalLevel = pendingInsidePocket ? level : pendingLevel;
        BlockPos externalPos = pendingInsidePocket ? origin : pendingEndpoint.pos();

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        StoragePairValidation validation = validateDirectStoragePair(level, origin, kind, pendingEndpoint);
        if (!validation.valid()) {
            notifyPlayer(player, validation.message());
            return InteractionResult.FAIL;
        }
        PocketFactorySavedData.FactoryRecord factory = validation.factory();
        if (factory == null) {
            notifyPlayer(player, "No pocket factory found for the selected internal storage.");
            return InteractionResult.FAIL;
        }

        int bindingId = savedData.createBinding(factory.id(), kind.bindingChannel());
        LinkedStorageBindingHelper.BindingTarget internalTarget = new LinkedStorageBindingHelper.BindingTarget(
                bindingId,
                factory.id(),
                PocketFactoryDimensions.getChunkOffsetAt(factory, internalPos)
        );
        if (!LinkedStorageManualBindingHelper.bind(internalLevel, internalPos, kind, internalTarget)) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the internal storage.");
            return InteractionResult.FAIL;
        }

        LinkedStorageBindingHelper.BindingTarget externalTarget = new LinkedStorageBindingHelper.BindingTarget(bindingId, factory.id(), null);
        if (!LinkedStorageManualBindingHelper.bind(externalLevel, externalPos, kind, externalTarget)) {
            rollbackDirectStorageBinding(internalLevel, internalPos, kind, bindingId, level.registryAccess());
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the second storage. The first storage was rolled back.");
            return InteractionResult.FAIL;
        }

        clearPendingEndpoint(stack);
        notifyPlayer(player, "Storage link created.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult handleEntranceClick(ServerLevel level, BlockPos entrancePos, ItemStack stack,
                                                         @Nullable Player player) {
        PendingEndpoint pendingEndpoint = getPendingEndpoint(stack);
        if (pendingEndpoint == null) {
            return InteractionResult.PASS;
        }

        return switch (pendingEndpoint.kind()) {
            case ITEM_VAULT, FLUID_TANK -> {
                notifyPlayer(player, "Storage linking now requires selecting the second matching storage directly.");
                yield InteractionResult.FAIL;
            }
            case CHUTE -> {
                notifyPlayer(player, "Linked chute linking now requires selecting the second linked chute directly.");
                yield InteractionResult.FAIL;
            }
            case CLUTCH -> {
                notifyPlayer(player, "Linked clutch linking now requires selecting the second clutch directly.");
                yield InteractionResult.FAIL;
            }
            case PUMP -> {
                notifyPlayer(player, "Linked pump linking now requires selecting the second mechanical pump directly.");
                yield InteractionResult.FAIL;
            }
            case PORTAL -> handlePortalBindingThroughEntrance(level, entrancePos, stack, player, pendingEndpoint);
        };
    }

    private static InteractionResult handlePortalClick(ServerLevel level, BlockPos portalPos, ItemStack stack,
                                                       @Nullable Player player) {
        if (!(level.getBlockEntity(portalPos) instanceof PocketFactoryPortalBlockEntity portal)) {
            return InteractionResult.FAIL;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        if (PocketFactoryDimensions.LEVEL_KEY.equals(level.dimension())) {
            PocketFactorySavedData.FactoryRecord factory = PocketFactoryDimensions.findFactoryAt(savedData, portalPos);
            if (factory == null) {
                notifyPlayer(player, "No pocket factory found here.");
                return InteractionResult.FAIL;
            }

            if (portal.hasFactoryId()) {
                savedData.clearPortalEndpointIfMatches(portal.getFactoryId(),
                        portal.isInternalEndpoint() ? PocketFactorySavedData.PortalEndpoint.INTERNAL : PocketFactorySavedData.PortalEndpoint.EXTERNAL,
                        level.dimension(), portalPos);
            }
            portal.setBinding(factory.id(), true);
            savedData.registerPortalEndpoint(factory.id(), PocketFactorySavedData.PortalEndpoint.INTERNAL, level.dimension(), portalPos);
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Internal portal linked.");
            return InteractionResult.CONSUME;
        }

        setPendingEndpoint(stack, level, portalPos, EndpointKind.PORTAL);
        notifyPlayer(player, "Portal selected. Use the core on a factory entrance to finish linking.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult handlePortalBindingThroughEntrance(ServerLevel level, BlockPos entrancePos, ItemStack stack,
                                                                        @Nullable Player player, PendingEndpoint pendingEndpoint) {
        if (!(level.getBlockEntity(entrancePos) instanceof PocketFactoryEntranceBlockEntity entrance) || !entrance.hasFactoryId()) {
            notifyPlayer(player, "This entrance is not bound to a pocket factory.");
            return InteractionResult.FAIL;
        }

        ServerLevel targetLevel = level.getServer().getLevel(pendingEndpoint.dimension());
        if (targetLevel == null) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Selected portal is no longer available.");
            return InteractionResult.FAIL;
        }

        if (!(targetLevel.getBlockEntity(pendingEndpoint.pos()) instanceof PocketFactoryPortalBlockEntity portal)) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Selected portal is no longer available.");
            return InteractionResult.FAIL;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        if (portal.hasFactoryId()) {
            savedData.clearPortalEndpointIfMatches(portal.getFactoryId(),
                    portal.isInternalEndpoint() ? PocketFactorySavedData.PortalEndpoint.INTERNAL : PocketFactorySavedData.PortalEndpoint.EXTERNAL,
                    pendingEndpoint.dimension(), pendingEndpoint.pos());
        }
        portal.setBinding(entrance.getFactoryId(), false);
        savedData.registerPortalEndpoint(entrance.getFactoryId(), PocketFactorySavedData.PortalEndpoint.EXTERNAL,
                pendingEndpoint.dimension(), pendingEndpoint.pos());

        clearPendingEndpoint(stack);
        notifyPlayer(player, "External portal linked.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult handleChuteClick(ServerLevel level, BlockPos origin, ItemStack stack, @Nullable Player player) {
        if (level.getBlockState(origin).getBlock() instanceof LinkedChuteBlock) {
            notifyPlayer(player, "This chute is already bound.");
            return InteractionResult.FAIL;
        }

        if (!isBindableChute(level.getBlockState(origin))) {
            return InteractionResult.FAIL;
        }

        PendingEndpoint pendingEndpoint = getPendingEndpoint(stack);
        if (pendingEndpoint != null) {
            if (pendingEndpoint.kind() != EndpointKind.CHUTE) {
                notifyPlayer(player, "Select another chute or clear the current selection first.");
                return InteractionResult.FAIL;
            }
            if (pendingEndpoint.dimension().equals(level.dimension()) && pendingEndpoint.pos().equals(origin)) {
                clearPendingEndpoint(stack);
                notifyPlayer(player, "Chute selection cleared.");
                return InteractionResult.CONSUME;
            }
            return handleDirectChuteBinding(level, origin, stack, player, pendingEndpoint);
        }

        setPendingEndpoint(stack, level, origin, EndpointKind.CHUTE);
        notifyPlayer(player, "Chute selected. Use the core on another chute to finish linking.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult handlePumpClick(ServerLevel level, BlockPos origin, ItemStack stack, @Nullable Player player) {
        BlockState state = level.getBlockState(origin);
        return handleEndpointSelectionClick(
                level,
                origin,
                stack,
                player,
                EndpointKind.PUMP,
                "pump",
                "another normal mechanical pump",
                LinkedPumpBindingHelper.isLinkedPump(state),
                LinkedPumpBindingHelper.isBindablePump(state),
                PocketFactoryCoreItem::handleDirectPumpBinding
        );
    }

    private static InteractionResult handleClutchClick(ServerLevel level, BlockPos origin, ItemStack stack, @Nullable Player player) {
        BlockState state = level.getBlockState(origin);
        return handleEndpointSelectionClick(
                level,
                origin,
                stack,
                player,
                EndpointKind.CLUTCH,
                "clutch",
                "another normal clutch",
                LinkedClutchBindingHelper.isLinkedClutch(state),
                LinkedClutchBindingHelper.isBindableClutch(state),
                PocketFactoryCoreItem::handleDirectClutchBinding
        );
    }

    private static InteractionResult handleEndpointSelectionClick(ServerLevel level, BlockPos origin, ItemStack stack,
                                                                  @Nullable Player player, EndpointKind kind,
                                                                  String endpointName, String completionTargetDescription,
                                                                  boolean alreadyBound, boolean bindable,
                                                                  PendingEndpointBindingHandler bindingHandler) {
        if (alreadyBound) {
            notifyPlayer(player, "This " + endpointName + " is already bound.");
            return InteractionResult.FAIL;
        }

        if (!bindable) {
            return InteractionResult.FAIL;
        }

        PendingEndpoint pendingEndpoint = getPendingEndpoint(stack);
        if (pendingEndpoint != null) {
            if (pendingEndpoint.kind() != kind) {
                notifyPlayer(player, "Select another " + endpointName + " or clear the current selection first.");
                return InteractionResult.FAIL;
            }
            if (pendingEndpoint.dimension().equals(level.dimension()) && pendingEndpoint.pos().equals(origin)) {
                clearPendingEndpoint(stack);
                notifyPlayer(player, capitalize(endpointName) + " selection cleared.");
                return InteractionResult.CONSUME;
            }
            return bindingHandler.bind(level, origin, stack, player, pendingEndpoint);
        }

        setPendingEndpoint(stack, level, origin, kind);
        notifyPlayer(player, capitalize(endpointName) + " selected. Use the core on " + completionTargetDescription + " to finish linking.");
        return InteractionResult.CONSUME;
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @FunctionalInterface
    private interface PendingEndpointBindingHandler {
        InteractionResult bind(ServerLevel level, BlockPos origin, ItemStack stack, @Nullable Player player,
                               PendingEndpoint pendingEndpoint);
    }

    private static InteractionResult handleDirectPumpBinding(ServerLevel level, BlockPos origin, ItemStack stack,
                                                             @Nullable Player player, PendingEndpoint pendingEndpoint) {
        PumpPairValidation validation = validateDirectPumpPair(level, origin, pendingEndpoint);
        if (!validation.valid()) {
            notifyPlayer(player, validation.message());
            return InteractionResult.FAIL;
        }

        PocketFactorySavedData.FactoryRecord factory = validation.factory();
        if (factory == null) {
            notifyPlayer(player, "No pocket factory found for the selected internal pump.");
            return InteractionResult.FAIL;
        }

        ServerLevel pendingLevel = level.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Selected pump is no longer available.");
            return InteractionResult.FAIL;
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        ServerLevel internalLevel = pendingInsidePocket ? pendingLevel : level;
        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : origin;
        ServerLevel externalLevel = pendingInsidePocket ? level : pendingLevel;
        BlockPos externalPos = pendingInsidePocket ? origin : pendingEndpoint.pos();

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        int bindingId = savedData.createBinding(factory.id(), PocketFactorySavedData.BindingChannel.LINKED_PUMP);
        if (!bindPumpEndpoint(internalLevel, internalPos, bindingId, factory.id(), true)) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the internal pump.");
            return InteractionResult.FAIL;
        }

        if (!bindPumpEndpoint(externalLevel, externalPos, bindingId, factory.id(), false)) {
            rollbackDirectPumpBinding(internalLevel, internalPos, bindingId);
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the second pump. The first pump was rolled back.");
            return InteractionResult.FAIL;
        }

        clearPendingEndpoint(stack);
        notifyPlayer(player, "Pump link created.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult handleDirectClutchBinding(ServerLevel level, BlockPos origin, ItemStack stack,
                                                               @Nullable Player player, PendingEndpoint pendingEndpoint) {
        ClutchPairValidation validation = validateDirectClutchPair(level, origin, pendingEndpoint);
        if (!validation.valid()) {
            notifyPlayer(player, validation.message());
            return InteractionResult.FAIL;
        }

        PocketFactorySavedData.FactoryRecord factory = validation.factory();
        if (factory == null) {
            notifyPlayer(player, "No pocket factory found for the selected internal clutch.");
            return InteractionResult.FAIL;
        }

        ServerLevel pendingLevel = level.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Selected clutch is no longer available.");
            return InteractionResult.FAIL;
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        ServerLevel internalLevel = pendingInsidePocket ? pendingLevel : level;
        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : origin;
        ServerLevel externalLevel = pendingInsidePocket ? level : pendingLevel;
        BlockPos externalPos = pendingInsidePocket ? origin : pendingEndpoint.pos();

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        int bindingId = savedData.createBinding(factory.id(), PocketFactorySavedData.BindingChannel.LINKED_CLUTCH);
        if (!bindClutchEndpoint(internalLevel, internalPos, bindingId, factory.id(), true)) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the internal clutch.");
            return InteractionResult.FAIL;
        }

        if (!bindClutchEndpoint(externalLevel, externalPos, bindingId, factory.id(), false)) {
            rollbackDirectClutchBinding(internalLevel, internalPos, bindingId);
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the second clutch. The first clutch was rolled back.");
            return InteractionResult.FAIL;
        }

        clearPendingEndpoint(stack);
        notifyPlayer(player, "Clutch link created.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult handleDirectChuteBinding(ServerLevel level, BlockPos origin, ItemStack stack,
                                                              @Nullable Player player, PendingEndpoint pendingEndpoint) {
        ChutePairValidation validation = validateDirectChutePair(level, origin, pendingEndpoint);
        if (!validation.valid()) {
            notifyPlayer(player, validation.message());
            return InteractionResult.FAIL;
        }

        PocketFactorySavedData.FactoryRecord factory = validation.factory();
        if (factory == null) {
            notifyPlayer(player, "No pocket factory found for the selected internal chute.");
            return InteractionResult.FAIL;
        }

        ServerLevel pendingLevel = level.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Selected chute is no longer available.");
            return InteractionResult.FAIL;
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        ServerLevel internalLevel = pendingInsidePocket ? pendingLevel : level;
        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : origin;
        ServerLevel externalLevel = pendingInsidePocket ? level : pendingLevel;
        BlockPos externalPos = pendingInsidePocket ? origin : pendingEndpoint.pos();

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        int bindingId = savedData.createBinding(factory.id(), PocketFactorySavedData.BindingChannel.LINKED_CHUTE);
        if (!bindChuteEndpoint(internalLevel, internalPos, bindingId, factory.id(), true)) {
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the internal chute.");
            return InteractionResult.FAIL;
        }

        if (!bindChuteEndpoint(externalLevel, externalPos, bindingId, factory.id(), false)) {
            rollbackDirectChuteBinding(internalLevel, internalPos, bindingId);
            clearPendingEndpoint(stack);
            notifyPlayer(player, "Binding failed while linking the second chute. The first chute was rolled back.");
            return InteractionResult.FAIL;
        }

        clearPendingEndpoint(stack);
        notifyPlayer(player, "Chute link created.");
        return InteractionResult.CONSUME;
    }

    private static void setPendingEndpoint(ItemStack stack, Level level, BlockPos pos, EndpointKind kind) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tag = new CompoundTag();
        tag.putString(TARGET_DIMENSION_TAG, level.dimension().location().toString());
        tag.putInt(TARGET_X_TAG, pos.getX());
        tag.putInt(TARGET_Y_TAG, pos.getY());
        tag.putInt(TARGET_Z_TAG, pos.getZ());
        tag.putString(TARGET_KIND_TAG, kind.name());
        root.put(PENDING_ENDPOINT_TAG, tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void clearPendingEndpoint(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(PENDING_ENDPOINT_TAG)) {
            return;
        }
        root.remove(PENDING_ENDPOINT_TAG);
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    public static @Nullable SelectedEndpoint getSelectedEndpoint(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(PENDING_ENDPOINT_TAG)) {
            return null;
        }
        CompoundTag tag = root.getCompound(PENDING_ENDPOINT_TAG);
        if (!tag.contains(TARGET_DIMENSION_TAG) || !tag.contains(TARGET_KIND_TAG)) {
            return null;
        }

        ResourceLocation location = ResourceLocation.tryParse(tag.getString(TARGET_DIMENSION_TAG));
        if (location == null) {
            return null;
        }

        EndpointKind kind;
        try {
            kind = EndpointKind.valueOf(tag.getString(TARGET_KIND_TAG));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        return new SelectedEndpoint(
                ResourceKey.create(Registries.DIMENSION, location),
                new BlockPos(tag.getInt(TARGET_X_TAG), tag.getInt(TARGET_Y_TAG), tag.getInt(TARGET_Z_TAG)),
                kind
        );
    }

    public static @Nullable HoveredEndpoint getHoveredEndpoint(Level level, BlockPos pos) {
        return getHoveredEndpoint(level, pos, null);
    }

    public static @Nullable HoveredEndpoint getHoveredEndpoint(Level level, BlockPos pos, @Nullable SelectedEndpoint selectedEndpoint) {
        BlockState state = level.getBlockState(pos);

        LinkedStorageManualBindingHelper.StorageKind storageKind = LinkedStorageManualBindingHelper.getStorageKind(state);
        if (storageKind != null) {
            HoverState hoverState = resolveStorageHoverState(level, pos, storageKind, selectedEndpoint, state);
            return new HoveredEndpoint(pos, EndpointKind.fromStorageKind(storageKind), hoverState);
        }

        if (state.getBlock() instanceof PocketFactoryPortalBlock) {
            if (!(level.getBlockEntity(pos) instanceof PocketFactoryPortalBlockEntity portal)) {
                return null;
            }
            return new HoveredEndpoint(pos, EndpointKind.PORTAL, portal.hasFactoryId() ? HoverState.BLOCKED : HoverState.AVAILABLE);
        }

        if (state.getBlock() instanceof LinkedChuteBlock) {
            return new HoveredEndpoint(pos, EndpointKind.CHUTE, HoverState.BLOCKED);
        }

        if (isBindableChute(state)) {
            return new HoveredEndpoint(pos, EndpointKind.CHUTE, resolveChuteHoverState(level, pos, selectedEndpoint));
        }

        if (LinkedClutchBindingHelper.isLinkedClutch(state)) {
            return new HoveredEndpoint(pos, EndpointKind.CLUTCH, HoverState.BLOCKED);
        }

        if (LinkedClutchBindingHelper.isBindableClutch(state)) {
            return new HoveredEndpoint(pos, EndpointKind.CLUTCH, resolveClutchHoverState(level, pos, selectedEndpoint));
        }

        if (LinkedPumpBindingHelper.isLinkedPump(state)) {
            return new HoveredEndpoint(pos, EndpointKind.PUMP, HoverState.BLOCKED);
        }

        if (LinkedPumpBindingHelper.isBindablePump(state)) {
            return new HoveredEndpoint(pos, EndpointKind.PUMP, resolvePumpHoverState(level, pos, selectedEndpoint));
        }

        return null;
    }

    private static HoverState resolveStorageHoverState(Level level, BlockPos pos,
                                                       LinkedStorageManualBindingHelper.StorageKind storageKind,
                                                       @Nullable SelectedEndpoint selectedEndpoint,
                                                       BlockState state) {
        if (!LinkedStorageManualBindingHelper.isNormalStorage(state)
                || !LinkedStorageManualBindingHelper.canBindAt(level, pos, storageKind)) {
            return HoverState.BLOCKED;
        }

        if (selectedEndpoint == null || selectedEndpoint.kind().storageKind() == null) {
            return HoverState.AVAILABLE;
        }

        if (selectedEndpoint.kind().storageKind() != storageKind) {
            return HoverState.BLOCKED;
        }

        if (selectedEndpoint.dimension().equals(level.dimension()) && selectedEndpoint.pos().equals(pos)) {
            return HoverState.BLOCKED;
        }

        boolean selectedInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(selectedEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(level.dimension());
        return selectedInsidePocket == currentInsidePocket ? HoverState.BLOCKED : HoverState.AVAILABLE;
    }

    private static HoverState resolveChuteHoverState(Level level, BlockPos pos,
                                                     @Nullable SelectedEndpoint selectedEndpoint) {
        if (selectedEndpoint == null) {
            return HoverState.AVAILABLE;
        }

        if (selectedEndpoint.kind() != EndpointKind.CHUTE) {
            return HoverState.BLOCKED;
        }

        if (selectedEndpoint.dimension().equals(level.dimension()) && selectedEndpoint.pos().equals(pos)) {
            return HoverState.BLOCKED;
        }

        boolean selectedInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(selectedEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(level.dimension());
        return selectedInsidePocket == currentInsidePocket ? HoverState.BLOCKED : HoverState.AVAILABLE;
    }

    private static HoverState resolvePumpHoverState(Level level, BlockPos pos,
                                                    @Nullable SelectedEndpoint selectedEndpoint) {
        if (selectedEndpoint == null) {
            return HoverState.AVAILABLE;
        }

        if (selectedEndpoint.kind() != EndpointKind.PUMP) {
            return HoverState.BLOCKED;
        }

        if (selectedEndpoint.dimension().equals(level.dimension()) && selectedEndpoint.pos().equals(pos)) {
            return HoverState.BLOCKED;
        }

        boolean selectedInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(selectedEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(level.dimension());
        return selectedInsidePocket == currentInsidePocket ? HoverState.BLOCKED : HoverState.AVAILABLE;
    }

    private static HoverState resolveClutchHoverState(Level level, BlockPos pos,
                                                      @Nullable SelectedEndpoint selectedEndpoint) {
        if (selectedEndpoint == null) {
            return HoverState.AVAILABLE;
        }

        if (selectedEndpoint.kind() != EndpointKind.CLUTCH) {
            return HoverState.BLOCKED;
        }

        if (selectedEndpoint.dimension().equals(level.dimension()) && selectedEndpoint.pos().equals(pos)) {
            return HoverState.BLOCKED;
        }

        boolean selectedInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(selectedEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(level.dimension());
        return selectedInsidePocket == currentInsidePocket ? HoverState.BLOCKED : HoverState.AVAILABLE;
    }

    private static StoragePairValidation validateDirectStoragePair(ServerLevel currentLevel, BlockPos currentPos,
                                                                   LinkedStorageManualBindingHelper.StorageKind kind,
                                                                   PendingEndpoint pendingEndpoint) {
        ServerLevel pendingLevel = currentLevel.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            return StoragePairValidation.invalid("Selected storage is no longer available.");
        }

        if (pendingEndpoint.storageKind() != kind) {
            return StoragePairValidation.invalid("Select another storage of the same type.");
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(currentLevel.dimension());
        if (pendingInsidePocket == currentInsidePocket) {
            return StoragePairValidation.invalid("Select one storage inside a pocket factory and one storage outside it.");
        }

        ServerLevel internalLevel = pendingInsidePocket ? pendingLevel : currentLevel;
        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : currentPos;
        ServerLevel externalLevel = pendingInsidePocket ? currentLevel : pendingLevel;
        BlockPos externalPos = pendingInsidePocket ? currentPos : pendingEndpoint.pos();

        PocketFactorySavedData savedData = PocketFactorySavedData.get(currentLevel.getServer());
        PocketFactorySavedData.FactoryRecord factory = PocketFactoryDimensions.findFactoryAt(savedData, internalPos);
        if (factory == null) {
            return StoragePairValidation.invalid("No pocket factory found for the selected internal storage.");
        }

        if (!LinkedStorageManualBindingHelper.canBindAt(internalLevel, internalPos, kind)
                || !LinkedStorageManualBindingHelper.canBindAt(externalLevel, externalPos, kind)) {
            return StoragePairValidation.invalid("Binding failed. Both storages must still be empty and available.");
        }

        return StoragePairValidation.valid(factory);
    }

    private static void rollbackDirectStorageBinding(ServerLevel level, BlockPos origin,
                                                     LinkedStorageManualBindingHelper.StorageKind kind,
                                                     int bindingId,
                                                     net.minecraft.core.HolderLookup.Provider registries) {
        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        switch (kind) {
            case ITEM_VAULT -> {
                savedData.disposeItemBinding(bindingId, registries);
                BlockEntity blockEntity = level.getBlockEntity(origin);
                if (blockEntity instanceof LinkedItemVaultBlockEntity linkedVault) {
                    LinkedItemVaultBlockEntity controller = resolveLinkedItemController(linkedVault);
                    if (controller != null) {
                        LinkedStorageManualBindingHelper.collapseItemVaultFromPlayer(controller, origin);
                    }
                }
            }
            case FLUID_TANK -> {
                savedData.disposeFluidBinding(bindingId);
                BlockEntity blockEntity = level.getBlockEntity(origin);
                if (blockEntity instanceof LinkedFluidTankBlockEntity linkedTank) {
                    LinkedFluidTankBlockEntity controller = resolveLinkedFluidController(linkedTank);
                    if (controller != null) {
                        LinkedStorageManualBindingHelper.collapseFluidTankFromPlayer(controller, origin);
                    }
                }
            }
        }
    }

    private static ChutePairValidation validateDirectChutePair(ServerLevel currentLevel, BlockPos currentPos,
                                                               PendingEndpoint pendingEndpoint) {
        ServerLevel pendingLevel = currentLevel.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            return ChutePairValidation.invalid("Selected chute is no longer available.");
        }

        if (pendingEndpoint.kind() != EndpointKind.CHUTE) {
            return ChutePairValidation.invalid("Select another chute.");
        }

        if (!isBindableChute(currentLevel.getBlockState(currentPos))
                || !isBindableChute(pendingLevel.getBlockState(pendingEndpoint.pos()))) {
            return ChutePairValidation.invalid("Both targets must still be normal vertical chutes.");
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(currentLevel.dimension());
        if (pendingInsidePocket == currentInsidePocket) {
            return ChutePairValidation.invalid("Select one chute inside a pocket factory and one chute outside it.");
        }

        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : currentPos;
        PocketFactorySavedData savedData = PocketFactorySavedData.get(currentLevel.getServer());
        PocketFactorySavedData.FactoryRecord factory = PocketFactoryDimensions.findFactoryAt(savedData, internalPos);
        if (factory == null) {
            return ChutePairValidation.invalid("No pocket factory found for the selected internal chute.");
        }

        return ChutePairValidation.valid(factory);
    }

    private static PumpPairValidation validateDirectPumpPair(ServerLevel currentLevel, BlockPos currentPos,
                                                             PendingEndpoint pendingEndpoint) {
        ServerLevel pendingLevel = currentLevel.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            return PumpPairValidation.invalid("Selected pump is no longer available.");
        }

        if (pendingEndpoint.kind() != EndpointKind.PUMP) {
            return PumpPairValidation.invalid("Select another normal mechanical pump.");
        }

        if (!LinkedPumpBindingHelper.isBindablePump(currentLevel.getBlockState(currentPos))
                || !LinkedPumpBindingHelper.isBindablePump(pendingLevel.getBlockState(pendingEndpoint.pos()))) {
            return PumpPairValidation.invalid("Both targets must still be normal mechanical pumps.");
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(currentLevel.dimension());
        if (pendingInsidePocket == currentInsidePocket) {
            return PumpPairValidation.invalid("Select one pump inside a pocket factory and one pump outside it.");
        }

        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : currentPos;
        PocketFactorySavedData savedData = PocketFactorySavedData.get(currentLevel.getServer());
        PocketFactorySavedData.FactoryRecord factory = PocketFactoryDimensions.findFactoryAt(savedData, internalPos);
        if (factory == null) {
            return PumpPairValidation.invalid("No pocket factory found for the selected internal pump.");
        }

        return PumpPairValidation.valid(factory);
    }

    private static ClutchPairValidation validateDirectClutchPair(ServerLevel currentLevel, BlockPos currentPos,
                                                                 PendingEndpoint pendingEndpoint) {
        ServerLevel pendingLevel = currentLevel.getServer().getLevel(pendingEndpoint.dimension());
        if (pendingLevel == null) {
            return ClutchPairValidation.invalid("Selected clutch is no longer available.");
        }

        if (pendingEndpoint.kind() != EndpointKind.CLUTCH) {
            return ClutchPairValidation.invalid("Select another normal clutch.");
        }

        if (!LinkedClutchBindingHelper.isBindableClutch(currentLevel.getBlockState(currentPos))
            || !LinkedClutchBindingHelper.isBindableClutch(pendingLevel.getBlockState(pendingEndpoint.pos()))) {
            return ClutchPairValidation.invalid("Both targets must still be normal clutches.");
        }

        boolean pendingInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(pendingEndpoint.dimension());
        boolean currentInsidePocket = PocketFactoryDimensions.LEVEL_KEY.equals(currentLevel.dimension());
        if (pendingInsidePocket == currentInsidePocket) {
            return ClutchPairValidation.invalid("Select one clutch inside a pocket factory and one clutch outside it.");
        }

        BlockPos internalPos = pendingInsidePocket ? pendingEndpoint.pos() : currentPos;
        PocketFactorySavedData savedData = PocketFactorySavedData.get(currentLevel.getServer());
        PocketFactorySavedData.FactoryRecord factory = PocketFactoryDimensions.findFactoryAt(savedData, internalPos);
        if (factory == null) {
            return ClutchPairValidation.invalid("No pocket factory found for the selected internal clutch.");
        }

        return ClutchPairValidation.valid(factory);
    }

    private static boolean bindChuteEndpoint(ServerLevel level, BlockPos pos, int bindingId, int factoryId, boolean internalEndpoint) {
        BlockState state = level.getBlockState(pos);
        if (!isBindableChute(state)) {
            return false;
        }

        level.setBlock(pos, ModBlocks.LINKED_CHUTE.get().defaultBlockState()
                .setValue(LinkedChuteBlock.FACING, state.getValue(ChuteBlock.FACING))
                .setValue(LinkedChuteBlock.SHAPE, state.getValue(ChuteBlock.SHAPE))
                .setValue(LinkedChuteBlock.WATERLOGGED, state.getValue(ChuteBlock.WATERLOGGED)), net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);

        LinkedChuteBlockEntity chute = getLinkedChute(level, pos);
        if (chute == null) {
            level.setBlock(pos, AllBlocks.CHUTE.getDefaultState()
                    .setValue(ChuteBlock.FACING, state.getValue(ChuteBlock.FACING))
                    .setValue(ChuteBlock.SHAPE, state.getValue(ChuteBlock.SHAPE))
                    .setValue(ChuteBlock.WATERLOGGED, state.getValue(ChuteBlock.WATERLOGGED)), net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
            return false;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        String endpointKey = LinkedStorageBindingHelper.endpointKey(level, pos);
        int resolvedBindingId = savedData.bindEndpointToBinding(
                bindingId,
                factoryId,
                PocketFactorySavedData.BindingChannel.LINKED_CHUTE,
                internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL,
                endpointKey
        );
        if (resolvedBindingId <= 0) {
            return false;
        }

        chute.setBinding(resolvedBindingId, factoryId, internalEndpoint);
        return true;
    }

    private static void rollbackDirectChuteBinding(ServerLevel level, BlockPos pos, int bindingId) {
        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        savedData.disposeBinding(bindingId, PocketFactorySavedData.BindingChannel.LINKED_CHUTE);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LinkedChuteBlock) {
            level.setBlock(pos, LinkedChuteBlock.toVanillaChuteState(state), net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    private static boolean bindPumpEndpoint(ServerLevel level, BlockPos pos, int bindingId, int factoryId, boolean internalEndpoint) {
        BlockState state = level.getBlockState(pos);
        if (!LinkedPumpBindingHelper.isBindablePump(state)) {
            return false;
        }

        level.setBlock(pos, LinkedPumpBindingHelper.toLinkedState(state), net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
        if (!(level.getBlockEntity(pos) instanceof LinkedPumpEndpoint linkedPump)) {
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
            return false;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        String endpointKey = BindingEndpointHelper.endpointKey(level, pos);
        int resolvedBindingId = savedData.bindEndpointToBinding(
                bindingId,
                factoryId,
                PocketFactorySavedData.BindingChannel.LINKED_PUMP,
                internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL,
                endpointKey
        );
        if (resolvedBindingId <= 0) {
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
            return false;
        }

        linkedPump.setBinding(resolvedBindingId, factoryId, internalEndpoint);
        return true;
    }

    private static void rollbackDirectPumpBinding(ServerLevel level, BlockPos pos, int bindingId) {
        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        savedData.disposeBinding(bindingId, PocketFactorySavedData.BindingChannel.LINKED_PUMP);
        BlockState state = level.getBlockState(pos);
        if (LinkedPumpBindingHelper.isLinkedPump(state)) {
            level.setBlock(pos, LinkedPumpBindingHelper.toVanillaState(state), net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    private static boolean bindClutchEndpoint(ServerLevel level, BlockPos pos, int bindingId, int factoryId, boolean internalEndpoint) {
        BlockState state = level.getBlockState(pos);
        if (!LinkedClutchBindingHelper.isBindableClutch(state)) {
            return false;
        }

        level.setBlock(pos, LinkedClutchBindingHelper.toLinkedState(state), net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
        if (!(level.getBlockEntity(pos) instanceof LinkedClutchEndpoint linkedClutch)) {
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
            return false;
        }

        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        String endpointKey = BindingEndpointHelper.endpointKey(level, pos);
        int resolvedBindingId = savedData.bindEndpointToBinding(
                bindingId,
                factoryId,
                PocketFactorySavedData.BindingChannel.LINKED_CLUTCH,
                internalEndpoint ? PocketFactorySavedData.EndpointRole.INTERNAL : PocketFactorySavedData.EndpointRole.EXTERNAL,
                endpointKey
        );
        if (resolvedBindingId <= 0) {
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
            return false;
        }

        linkedClutch.setBinding(resolvedBindingId, factoryId, internalEndpoint);
        return true;
    }

    private static void rollbackDirectClutchBinding(ServerLevel level, BlockPos pos, int bindingId) {
        PocketFactorySavedData savedData = PocketFactorySavedData.get(level.getServer());
        savedData.disposeBinding(bindingId, PocketFactorySavedData.BindingChannel.LINKED_CLUTCH);
        BlockState state = level.getBlockState(pos);
        if (LinkedClutchBindingHelper.isLinkedClutch(state)) {
            level.setBlock(pos, LinkedClutchBindingHelper.toVanillaState(state), net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    private static @Nullable LinkedItemVaultBlockEntity resolveLinkedItemController(LinkedItemVaultBlockEntity blockEntity) {
        if (blockEntity.isController()) {
            return blockEntity;
        }
        ItemVaultBlockEntity controller = blockEntity.getControllerBE();
        return controller instanceof LinkedItemVaultBlockEntity linkedController ? linkedController : null;
    }

    private static @Nullable LinkedFluidTankBlockEntity resolveLinkedFluidController(LinkedFluidTankBlockEntity blockEntity) {
        if (blockEntity.isController()) {
            return blockEntity;
        }
        FluidTankBlockEntity controller = blockEntity.getControllerBE();
        return controller instanceof LinkedFluidTankBlockEntity linkedController ? linkedController : null;
    }

    private static @Nullable LinkedChuteBlockEntity getLinkedChute(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LinkedChuteBlockEntity chute ? chute : null;
    }

    private static boolean isBindableChute(BlockState state) {
        return AllBlocks.CHUTE.has(state) && AbstractChuteBlock.getChuteFacing(state) == Direction.DOWN;
    }

    private static @Nullable PendingEndpoint getPendingEndpoint(ItemStack stack) {
        SelectedEndpoint selected = getSelectedEndpoint(stack);
        return selected == null ? null : new PendingEndpoint(selected.dimension(), selected.pos(), selected.kind());
    }

    private static void notifyPlayer(@Nullable Player player, String message) {
        if (player != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    public enum EndpointKind {
        ITEM_VAULT,
        FLUID_TANK,
        CHUTE,
        CLUTCH,
        PUMP,
        PORTAL;

        private static EndpointKind fromStorageKind(LinkedStorageManualBindingHelper.StorageKind kind) {
            return switch (kind) {
                case ITEM_VAULT -> ITEM_VAULT;
                case FLUID_TANK -> FLUID_TANK;
            };
        }

        private @Nullable LinkedStorageManualBindingHelper.StorageKind storageKind() {
            return switch (this) {
                case ITEM_VAULT -> LinkedStorageManualBindingHelper.StorageKind.ITEM_VAULT;
                case FLUID_TANK -> LinkedStorageManualBindingHelper.StorageKind.FLUID_TANK;
                case CHUTE, CLUTCH, PUMP, PORTAL -> null;
            };
        }

        private String tooltipKey() {
            return switch (this) {
                case ITEM_VAULT -> "tooltip.create_pocket_factory.pocket_factory_internal_eye.selected_item_vault";
                case FLUID_TANK -> "tooltip.create_pocket_factory.pocket_factory_internal_eye.selected_fluid_tank";
                case CHUTE -> "tooltip.create_pocket_factory.pocket_factory_internal_eye.selected_linked_chute";
                case CLUTCH -> "tooltip.create_pocket_factory.pocket_factory_internal_eye.selected_linked_clutch";
                case PUMP -> "tooltip.create_pocket_factory.pocket_factory_internal_eye.selected_linked_pump";
                case PORTAL -> "tooltip.create_pocket_factory.pocket_factory_internal_eye.selected_portal";
            };
        }
    }

    public enum HoverState {
        AVAILABLE,
        BLOCKED
    }

    public record HoveredEndpoint(BlockPos pos, EndpointKind kind, HoverState state) {
        public boolean isAvailable() {
            return state == HoverState.AVAILABLE;
        }
    }

    private record StoragePairValidation(boolean valid, @Nullable PocketFactorySavedData.FactoryRecord factory, @Nullable String message) {
        private static StoragePairValidation valid(PocketFactorySavedData.FactoryRecord factory) {
            return new StoragePairValidation(true, factory, null);
        }

        private static StoragePairValidation invalid(String message) {
            return new StoragePairValidation(false, null, message);
        }
    }

    private record ChutePairValidation(boolean valid, @Nullable PocketFactorySavedData.FactoryRecord factory, @Nullable String message) {
        private static ChutePairValidation valid(PocketFactorySavedData.FactoryRecord factory) {
            return new ChutePairValidation(true, factory, null);
        }

        private static ChutePairValidation invalid(String message) {
            return new ChutePairValidation(false, null, message);
        }
    }

    private record PumpPairValidation(boolean valid, @Nullable PocketFactorySavedData.FactoryRecord factory, @Nullable String message) {
        private static PumpPairValidation valid(PocketFactorySavedData.FactoryRecord factory) {
            return new PumpPairValidation(true, factory, null);
        }

        private static PumpPairValidation invalid(String message) {
            return new PumpPairValidation(false, null, message);
        }
    }

    private record ClutchPairValidation(boolean valid, @Nullable PocketFactorySavedData.FactoryRecord factory, @Nullable String message) {
        private static ClutchPairValidation valid(PocketFactorySavedData.FactoryRecord factory) {
            return new ClutchPairValidation(true, factory, null);
        }

        private static ClutchPairValidation invalid(String message) {
            return new ClutchPairValidation(false, null, message);
        }
    }

    private record PendingEndpoint(ResourceKey<Level> dimension, BlockPos pos, EndpointKind kind) {
        private LinkedStorageManualBindingHelper.StorageKind storageKind() {
            LinkedStorageManualBindingHelper.StorageKind storageKind = kind.storageKind();
            if (storageKind == null) {
                throw new IllegalStateException("Pending endpoint is not a storage target: " + kind);
            }
            return storageKind;
        }
    }

    public record SelectedEndpoint(ResourceKey<Level> dimension, BlockPos pos, EndpointKind kind) {
        public boolean isItemVault() {
            return kind == EndpointKind.ITEM_VAULT;
        }

        public boolean isFluidTank() {
            return kind == EndpointKind.FLUID_TANK;
        }

        public boolean isPortal() {
            return kind == EndpointKind.PORTAL;
        }

        public boolean isPump() {
            return kind == EndpointKind.PUMP;
        }

        public boolean isClutch() {
            return kind == EndpointKind.CLUTCH;
        }

        public String tooltipKey() {
            return kind.tooltipKey();
        }

        public boolean matches(HoveredEndpoint endpoint) {
            return pos.equals(endpoint.pos()) && kind == endpoint.kind();
        }
    }
}