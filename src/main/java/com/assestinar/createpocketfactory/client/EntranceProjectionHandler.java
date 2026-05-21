package com.assestinar.createpocketfactory.client;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.assestinar.createpocketfactory.block.entity.PocketFactoryPreviewHelper;
import com.assestinar.createpocketfactory.client.render.PocketFactoryProjectionCache;
import com.assestinar.createpocketfactory.config.ModConfigs;
import com.assestinar.createpocketfactory.item.PocketFactoryCoreItem;
import com.assestinar.createpocketfactory.network.RequestFactoryProjectionPreviewPacket;
import com.assestinar.createpocketfactory.network.SetBoundEntranceProjectionStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.schematics.client.ToolSelectionScreen;
import com.simibubi.create.content.schematics.client.tools.ToolType;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CreatePocketFactory.MOD_ID, value = Dist.CLIENT)
public final class EntranceProjectionHandler {
    private static final int REQUEST_INTERVAL = 200;
    private static final int DEFAULT_SELECTION_RANGE = 8;
    private static final ResourceLocation OVERLAY_ID = ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID, "entrance_projection_tools");
    private static final List<ToolType> DEPLOY_ONLY_TOOLS = List.of(ToolType.DEPLOY);
    private static final List<ToolType> DEPLOYED_TOOLS = List.of(ToolType.MOVE, ToolType.MOVE_Y, ToolType.DEPLOY, ToolType.ROTATE, ToolType.FLIP);
    private static final LayeredDraw.Layer OVERLAY = EntranceProjectionHandler::renderOverlay;

    private static int activeFactoryId = -1;
    private static List<PocketFactoryEntranceBlockEntity.PreviewBlock> previewBlocks = List.of();
    private static BlockPos activeEntrancePos;
    private static net.minecraft.resources.ResourceKey<Level> activeDimension;
    private static BlockPos deployPos;
    private static BlockPos committedAnchor;
    private static int selectionRange = DEFAULT_SELECTION_RANGE;
    private static long lastRequestTick = -REQUEST_INTERVAL;
    private static PocketFactoryProjectionCache previewCache;
    private static int previewCacheHash;
    private static int previewRotationQuarterTurns;
    private static boolean previewFlipX;
    private static boolean previewFlipZ;
    private static ToolType currentTool = ToolType.DEPLOY;
    private static ToolSelectionScreen selectionScreen = new ToolSelectionScreen(DEPLOY_ONLY_TOOLS, EntranceProjectionHandler::equip);

    private EntranceProjectionHandler() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, OVERLAY_ID, OVERLAY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            clearTransientSelection();
            return;
        }

        ProjectionContext context = getProjectionContext(player.getMainHandItem());
        if (context == null) {
            clearTransientSelection();
            return;
        }

        if (activeFactoryId != context.factoryId()
                || !Objects.equals(activeEntrancePos, context.entrancePos())
                || !Objects.equals(activeDimension, context.dimension())) {
            activeFactoryId = context.factoryId();
            activeEntrancePos = context.entrancePos();
            activeDimension = context.dimension();
            previewBlocks = List.of();
            previewCache = null;
            previewCacheHash = 0;
            deployPos = null;
            committedAnchor = null;
            selectionRange = DEFAULT_SELECTION_RANGE;
            lastRequestTick = -REQUEST_INTERVAL;
            previewRotationQuarterTurns = 0;
            previewFlipX = false;
            previewFlipZ = false;
            syncStateFromEntrance(minecraft.level, context);
            refreshSelectionScreen();
        }

        if (!minecraft.level.dimension().equals(context.dimension())) {
            deployPos = null;
            return;
        }

        updateTargetPos(minecraft, player);
        selectionScreen.update();

        long gameTime = minecraft.level.getGameTime();
        if (gameTime - lastRequestTick >= REQUEST_INTERVAL) {
            lastRequestTick = gameTime;
            PacketDistributor.sendToServer(new RequestFactoryProjectionPreviewPacket(context.factoryId()));
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (getProjectionContext(minecraft.player.getMainHandItem()) == null) {
            return;
        }

        event.setCanceled(mouseScrolled(event.getScrollDeltaY()));
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }

        onKeyInput(event.getKey(), event.getAction() != 0);
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }

        if (onMouseInput(event.getButton(), event.getAction() != 0)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || previewBlocks.isEmpty()) {
            return;
        }

        if (getProjectionContext(minecraft.player.getMainHandItem()) == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        PocketFactoryProjectionCache cache = getProjectionCache(minecraft);
        if (cache == null) {
            return;
        }

        if (currentTool == ToolType.DEPLOY) {
            return;
        }

        BlockPos anchor = getCurrentAnchor(minecraft.player.getMainHandItem());
        if (anchor == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(anchor.getX() + 0.5D - camera.x, anchor.getY() - camera.y, anchor.getZ() + 0.5D - camera.z);
        cache.applyTransform(poseStack, previewRotationQuarterTurns, previewFlipX, previewFlipZ);
        cache.render(poseStack, bufferSource, minecraft.level, minecraft.getTimer().getGameTimeDeltaPartialTick(false), null,
            ModConfigs.renderProjectionBlockEntities(),
            ModConfigs.renderDynamicProjectionParts());
        poseStack.popPose();
        bufferSource.endBatch();
    }

    public static void acceptPreview(int factoryId, CompoundTag previewTag) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || activeFactoryId != factoryId) {
            return;
        }

        previewBlocks = PocketFactoryPreviewHelper.readPreviewBlocks(previewTag, minecraft.level.registryAccess());
        previewCache = null;
        previewCacheHash = 0;
    }

    public static @javax.annotation.Nullable BlockPos getCurrentAnchor(ItemStack stack) {
        ProjectionContext context = getProjectionContext(stack);
        if (context == null) {
            return null;
        }

        if (!isDeployed()) {
            return getDeployAnchor(Minecraft.getInstance());
        }

        return committedAnchor;
    }

    public static @javax.annotation.Nullable AABB getCurrentProjectedBounds(Minecraft minecraft, ItemStack stack) {
        if (getProjectionContext(stack) == null || minecraft.level == null) {
            return null;
        }

        PocketFactoryProjectionCache cache = getProjectionCache(minecraft);
        if (cache == null) {
            return null;
        }

        BlockPos anchor = getCurrentAnchor(stack);
        if (anchor == null) {
            return null;
        }

        return cache.getTransformedBounds(anchor, previewRotationQuarterTurns, previewFlipX, previewFlipZ);
    }

    private static void updateTargetPos(Minecraft minecraft, LocalPlayer player) {
        if (AllKeys.ACTIVATE_TOOL.isPressed()) {
            Vec3 eyePosition = player.getEyePosition();
            deployPos = BlockPos.containing(eyePosition.add(player.getLookAngle().scale(selectionRange)));
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult hitResult) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            deployPos = null;
            return;
        }

        BlockPos hit = BlockPos.containing(hitResult.getLocation());
        boolean replaceable = minecraft.level.getBlockState(hit).canBeReplaced();
        if (hitResult.getDirection().getAxis().isVertical() && !replaceable) {
            hit = hit.relative(hitResult.getDirection());
        }
        deployPos = hit;
    }

    private static ProjectionContext getProjectionContext(ItemStack stack) {
        if (!ModConfigs.entranceProjectionMode().showsLargeProjection()) {
            return null;
        }
        if (!(stack.getItem() instanceof PocketFactoryCoreItem)) {
            return null;
        }

        PocketFactoryCoreItem.BoundEntrance boundEntrance = PocketFactoryCoreItem.getBoundEntrance(stack);
        return boundEntrance == null ? null : new ProjectionContext(boundEntrance.factoryId(), boundEntrance.dimension(), boundEntrance.pos());
    }

    private static void clearTransientSelection() {
        deployPos = null;
        committedAnchor = null;
        if (!ModConfigs.entranceProjectionMode().showsLargeProjection()) {
            activeFactoryId = -1;
            activeEntrancePos = null;
            activeDimension = null;
            previewBlocks = List.of();
            previewCache = null;
            previewCacheHash = 0;
            previewRotationQuarterTurns = 0;
            previewFlipX = false;
            previewFlipZ = false;
            currentTool = ToolType.DEPLOY;
            selectionScreen = new ToolSelectionScreen(DEPLOY_ONLY_TOOLS, EntranceProjectionHandler::equip);
        }
    }

    private static void syncStateFromEntrance(Level level, ProjectionContext context) {
        if (!(level.getBlockEntity(context.entrancePos()) instanceof PocketFactoryEntranceBlockEntity entranceBlockEntity)) {
            return;
        }

        committedAnchor = entranceBlockEntity.getProjectionAnchor();
        previewRotationQuarterTurns = entranceBlockEntity.getProjectionRotationQuarterTurns();
        previewFlipX = entranceBlockEntity.isProjectionFlipX();
        previewFlipZ = entranceBlockEntity.isProjectionFlipZ();
    }

    private static boolean moveCommittedAnchor(Minecraft minecraft, int delta, boolean vertical) {
        if (!ensureCommittedAnchor()) {
            return true;
        }

        if (vertical) {
            committedAnchor = committedAnchor.above(delta);
            syncCommittedState();
            return true;
        }

        Direction direction = getSelectedProjectionFace(minecraft);
        if (direction == null || !direction.getAxis().isHorizontal()) {
            return true;
        }

        committedAnchor = committedAnchor.relative(direction, delta);
        syncCommittedState();
        return true;
    }

    private static boolean toggleFlip(Minecraft minecraft) {
        if (!ensureCommittedAnchor()) {
            return true;
        }

        Direction direction = getSelectedProjectionFace(minecraft);
        if (direction == null || !direction.getAxis().isHorizontal()) {
            return true;
        }

        Direction.Axis localAxis = worldAxisToLocal(direction.getAxis());
        if (localAxis == Direction.Axis.X) {
            previewFlipX = !previewFlipX;
        } else if (localAxis == Direction.Axis.Z) {
            previewFlipZ = !previewFlipZ;
        }

        syncCommittedState();
        return true;
    }

    private static boolean ensureCommittedAnchor() {
        if (committedAnchor != null) {
            return true;
        }

        BlockPos alignedAnchor = getDeployAnchor(Minecraft.getInstance());
        if (alignedAnchor == null) {
            return false;
        }

        committedAnchor = alignedAnchor;
        return true;
    }

    private static @javax.annotation.Nullable BlockPos getDeployAnchor(Minecraft minecraft) {
        if (deployPos == null || minecraft.level == null) {
            return null;
        }

        PocketFactoryProjectionCache cache = getProjectionCache(minecraft);
        if (cache == null) {
            return deployPos;
        }

        AABB bounds = cache.getBounds();
        return deployPos.offset(-((int) bounds.getCenter().x), 0, -((int) bounds.getCenter().z));
    }

    private static @javax.annotation.Nullable Direction getSelectedProjectionFace(Minecraft minecraft) {
        if (minecraft.player == null) {
            return null;
        }

        AABB bounds = getCurrentProjectedBounds(minecraft, minecraft.player.getMainHandItem());
        if (bounds == null) {
            return null;
        }

        Vec3 eyePosition = minecraft.player.getEyePosition();
        Vec3 traceEnd = eyePosition.add(minecraft.player.getLookAngle().scale(70.0D));
        Vec3 hitPoint = bounds.clip(eyePosition, traceEnd).orElse(null);
        if (hitPoint == null) {
            return null;
        }

        double epsilon = 1.0E-4D;
        if (Math.abs(hitPoint.x - bounds.minX) < epsilon) {
            return Direction.WEST;
        }
        if (Math.abs(hitPoint.x - bounds.maxX) < epsilon) {
            return Direction.EAST;
        }
        if (Math.abs(hitPoint.z - bounds.minZ) < epsilon) {
            return Direction.NORTH;
        }
        if (Math.abs(hitPoint.z - bounds.maxZ) < epsilon) {
            return Direction.SOUTH;
        }
        if (Math.abs(hitPoint.y - bounds.minY) < epsilon) {
            return Direction.DOWN;
        }
        if (Math.abs(hitPoint.y - bounds.maxY) < epsilon) {
            return Direction.UP;
        }

        return null;
    }

    private static Direction.Axis worldAxisToLocal(Direction.Axis worldAxis) {
        if (worldAxis == Direction.Axis.Y) {
            return Direction.Axis.Y;
        }

        return switch (Math.floorMod(previewRotationQuarterTurns, 4)) {
            case 1, 3 -> worldAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            default -> worldAxis;
        };
    }

    public static void onKeyInput(int key, boolean pressed) {
        if (!hasActiveProjection()) {
            return;
        }

        if (!AllKeys.TOOL_MENU.doesModifierAndCodeMatch(key)) {
            return;
        }

        if (pressed && !selectionScreen.focused) {
            selectionScreen.focused = true;
        }
        if (!pressed && selectionScreen.focused) {
            selectionScreen.focused = false;
            selectionScreen.onClose();
        }
    }

    public static boolean mouseScrolled(double delta) {
        if (!hasActiveProjection()) {
            return false;
        }

        int direction = Mth.sign(delta);
        if (direction == 0) {
            return false;
        }

        if (selectionScreen.focused) {
            selectionScreen.cycle(direction);
            return true;
        }

        if (!AllKeys.ctrlDown()) {
            return false;
        }

        return switch (currentTool) {
            case DEPLOY -> handleDeployScroll(direction);
            case MOVE -> moveCommittedAnchor(Minecraft.getInstance(), direction, false);
            case MOVE_Y -> moveCommittedAnchor(Minecraft.getInstance(), direction, true);
            case ROTATE -> handleRotateScroll(direction);
            case FLIP -> toggleFlip(Minecraft.getInstance());
            default -> false;
        };
    }

    public static boolean onMouseInput(int button, boolean pressed) {
        if (!hasActiveProjection() || !pressed || button != 1) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isShiftKeyDown()) {
            return false;
        }

        if (currentTool == ToolType.DEPLOY) {
            if (!ensureCommittedAnchor()) {
                return false;
            }
            syncCommittedState();
            refreshSelectionScreen();
            return true;
        }

        if (currentTool == ToolType.FLIP) {
            return toggleFlip(minecraft);
        }

        return false;
    }

    private static void syncCommittedState() {
        PacketDistributor.sendToServer(new SetBoundEntranceProjectionStatePacket(
                committedAnchor != null,
                committedAnchor == null ? BlockPos.ZERO : committedAnchor,
                previewRotationQuarterTurns,
                previewFlipX,
                previewFlipZ
        ));
    }

    private static void refreshSelectionScreen() {
        ToolType previousTool = currentTool;
        selectionScreen = new ToolSelectionScreen(isDeployed() ? DEPLOYED_TOOLS : DEPLOY_ONLY_TOOLS, EntranceProjectionHandler::equip);
        if (isDeployed() && DEPLOYED_TOOLS.contains(previousTool)) {
            selectionScreen.setSelectedElement(previousTool);
            equip(previousTool);
        }
    }

    private static void equip(ToolType tool) {
        currentTool = tool;
    }

    private static boolean hasActiveProjection() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && getProjectionContext(minecraft.player.getMainHandItem()) != null;
    }

    private static boolean isDeployed() {
        return committedAnchor != null;
    }

    private static boolean handleDeployScroll(int direction) {
        if (!AllKeys.ACTIVATE_TOOL.isPressed()) {
            return false;
        }

        selectionRange = Mth.clamp(selectionRange + direction, 1, 100);
        return true;
    }

    private static boolean handleRotateScroll(int direction) {
        if (!ensureCommittedAnchor()) {
            return true;
        }

        previewRotationQuarterTurns = Math.floorMod(previewRotationQuarterTurns + direction, 4);
        syncCommittedState();
        return true;
    }

    private record ProjectionContext(int factoryId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                     BlockPos entrancePos) {
    }

    private static void renderOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!hasActiveProjection() || Minecraft.getInstance().options.hideGui) {
            return;
        }

        selectionScreen.renderPassive(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    private static PocketFactoryProjectionCache getProjectionCache(Minecraft minecraft) {
        if (minecraft.level == null || previewBlocks.isEmpty()) {
            previewCache = null;
            previewCacheHash = 0;
            return null;
        }

        int hash = previewBlocks.hashCode();
        if (previewCache != null && previewCacheHash == hash) {
            return previewCache;
        }

        previewCache = new PocketFactoryProjectionCache(minecraft.level, previewBlocks);
        previewCacheHash = hash;
        return previewCache;
    }
}