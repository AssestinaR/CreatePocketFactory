package com.assestinar.createpocketfactory.client.render;

import com.assestinar.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.createmod.catnip.render.ShadedBlockSbbBuilder;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class PocketFactoryProjectionCache {
    private static final ThreadLocal<ThreadLocalObjects> THREAD_LOCAL_OBJECTS = ThreadLocal.withInitial(ThreadLocalObjects::new);
    private static final float STATIC_KINETIC_SPEED = 1.0E-6F;

    private final VirtualRenderWorld renderWorld;
    private final List<BlockPos> renderedPositions;
    private final List<BlockEntity> renderedBlockEntities = new ArrayList<>();
    private final BitSet shouldRenderBlockEntities = new BitSet();
    private final BitSet scratchErroredBlockEntities = new BitSet();
    private final Map<RenderType, SuperByteBuffer> bufferCache = new LinkedHashMap<>();
    private final AABB bounds;

    public PocketFactoryProjectionCache(Level level, List<PocketFactoryEntranceBlockEntity.PreviewBlock> previewBlocks) {
        int maxY = 1;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        renderedPositions = new ArrayList<>(previewBlocks.size());

        for (PocketFactoryEntranceBlockEntity.PreviewBlock previewBlock : previewBlocks) {
            minX = Math.min(minX, previewBlock.x());
            minY = Math.min(minY, previewBlock.y());
            minZ = Math.min(minZ, previewBlock.z());
            maxX = Math.max(maxX, previewBlock.x());
            maxY = Math.max(maxY, previewBlock.y());
            maxZ = Math.max(maxZ, previewBlock.z());
        }

        int worldHeight = previewBlocks.isEmpty() ? 16 : Math.max(16, maxY + 2);
        renderWorld = new VirtualRenderWorld(level, 0, worldHeight, BlockPos.ZERO, () -> { }) {
            @Override
            public boolean supportsVisualization() {
                return false;
            }
        };

        for (PocketFactoryEntranceBlockEntity.PreviewBlock previewBlock : previewBlocks) {
            BlockPos localPos = new BlockPos(previewBlock.x(), previewBlock.y(), previewBlock.z());
            renderWorld.setBlock(localPos, previewBlock.state(), 0);
            renderedPositions.add(localPos);
            BlockEntity blockEntity = readBlockEntity(renderWorld, localPos, previewBlock.state(), previewBlock.blockEntityTag());
            if (blockEntity != null) {
                renderWorld.setBlockEntity(blockEntity);
                if (!shouldSkipDynamicBlockEntity(blockEntity)) {
                    renderedBlockEntities.add(blockEntity);
                }
            }
        }

        fixMultiBlockControllers();
        shouldRenderBlockEntities.set(0, renderedBlockEntities.size());
        renderWorld.runLightEngine();
        bounds = previewBlocks.isEmpty()
                ? new AABB(BlockPos.ZERO)
                : new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        redraw();
    }

    public AABB getBounds() {
        return bounds;
    }

    public float getMiniatureScale() {
        double maxSize = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        return (float) (0.85D / Math.max(1.0D, maxSize));
    }

    public void applyTransform(PoseStack poseStack, int rotationQuarterTurns, boolean flipX, boolean flipZ) {
        if (bounds.getXsize() <= 0.0D || bounds.getZsize() <= 0.0D) {
            return;
        }

        double pivotX = bounds.getCenter().x;
        double pivotZ = bounds.getCenter().z;
        poseStack.translate(pivotX, 0.0D, pivotZ);
        poseStack.scale(flipX ? -1.0F : 1.0F, 1.0F, flipZ ? -1.0F : 1.0F);
        if (rotationQuarterTurns != 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F * Math.floorMod(rotationQuarterTurns, 4)));
        }
        poseStack.translate(-pivotX, 0.0D, -pivotZ);
    }

    public AABB getTransformedBounds(BlockPos anchor, int rotationQuarterTurns, boolean flipX, boolean flipZ) {
        if (bounds.getXsize() <= 0.0D || bounds.getYsize() <= 0.0D || bounds.getZsize() <= 0.0D) {
            return new AABB(anchor);
        }

        double pivotX = bounds.getCenter().x;
        double pivotZ = bounds.getCenter().z;
        int normalizedRotation = Math.floorMod(rotationQuarterTurns, 4);

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (double x : new double[] {bounds.minX, bounds.maxX}) {
            for (double y : new double[] {bounds.minY, bounds.maxY}) {
                for (double z : new double[] {bounds.minZ, bounds.maxZ}) {
                    double transformedX = x - pivotX;
                    double transformedZ = z - pivotZ;
                    if (flipX) {
                        transformedX = -transformedX;
                    }
                    if (flipZ) {
                        transformedZ = -transformedZ;
                    }

                    double rotatedX;
                    double rotatedZ;
                    switch (normalizedRotation) {
                        case 1 -> {
                            rotatedX = -transformedZ;
                            rotatedZ = transformedX;
                        }
                        case 2 -> {
                            rotatedX = -transformedX;
                            rotatedZ = -transformedZ;
                        }
                        case 3 -> {
                            rotatedX = transformedZ;
                            rotatedZ = -transformedX;
                        }
                        default -> {
                            rotatedX = transformedX;
                            rotatedZ = transformedZ;
                        }
                    }

                    double worldX = rotatedX + pivotX + anchor.getX() + 0.5D;
                    double worldY = y + anchor.getY();
                    double worldZ = rotatedZ + pivotZ + anchor.getZ() + 0.5D;

                    minX = Math.min(minX, worldX);
                    minY = Math.min(minY, worldY);
                    minZ = Math.min(minZ, worldZ);
                    maxX = Math.max(maxX, worldX);
                    maxY = Math.max(maxY, worldY);
                    maxZ = Math.max(maxZ, worldZ);
                }
            }
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Level realLevel, float partialTick,
                       @Nullable Matrix4f lightTransform, boolean renderBlockEntities, boolean animateDynamicBlockEntities) {
        bufferCache.forEach((layer, buffer) -> buffer.renderInto(poseStack, bufferSource.getBuffer(layer)));
        if (renderBlockEntities && !renderedBlockEntities.isEmpty()) {
            renderBlockEntities(poseStack, bufferSource, partialTick, animateDynamicBlockEntities);
        }
    }

    private void renderBlockEntities(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick,
                                     boolean animateDynamicBlockEntities) {
        scratchErroredBlockEntities.clear();
        List<KineticSpeedSnapshot> frozenKinetics = animateDynamicBlockEntities ? List.of() : freezeKineticBlockEntities();
        try {
            BlockEntityRenderHelper.renderBlockEntities(
                    renderedBlockEntities,
                    shouldRenderBlockEntities,
                    scratchErroredBlockEntities,
                    null,
                    renderWorld,
                    poseStack,
                    null,
                    bufferSource,
                    partialTick
            );
        } finally {
            restoreKineticBlockEntities(frozenKinetics);
        }
        shouldRenderBlockEntities.andNot(scratchErroredBlockEntities);
    }

    private List<KineticSpeedSnapshot> freezeKineticBlockEntities() {
        List<KineticSpeedSnapshot> snapshots = new ArrayList<>();
        for (BlockEntity blockEntity : renderedBlockEntities) {
            if (!(blockEntity instanceof KineticBlockEntity kineticBlockEntity)) {
                continue;
            }

            float originalSpeed = kineticBlockEntity.getTheoreticalSpeed();
            if (originalSpeed == 0.0F) {
                continue;
            }

            snapshots.add(new KineticSpeedSnapshot(kineticBlockEntity, originalSpeed));
            kineticBlockEntity.setSpeed(Math.copySign(STATIC_KINETIC_SPEED, originalSpeed));
        }
        return snapshots;
    }

    private void restoreKineticBlockEntities(List<KineticSpeedSnapshot> snapshots) {
        for (KineticSpeedSnapshot snapshot : snapshots) {
            snapshot.blockEntity().setSpeed(snapshot.speed());
        }
    }

    private void redraw() {
        bufferCache.clear();
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            SuperByteBuffer buffer = drawLayer(layer);
            if (!buffer.isEmpty()) {
                bufferCache.put(layer, buffer);
            }
        }
    }

    private SuperByteBuffer drawLayer(RenderType layer) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        ModelBlockRenderer renderer = dispatcher.getModelRenderer();
        ThreadLocalObjects objects = THREAD_LOCAL_OBJECTS.get();

        PoseStack poseStack = objects.poseStack;
        RandomSource random = objects.random;
        ShadedBlockSbbBuilder sbbBuilder = objects.sbbBuilder;
        sbbBuilder.begin();

        ModelBlockRenderer.enableCaching();
        for (BlockPos pos : renderedPositions) {
            BlockState state = renderWorld.getBlockState(pos);
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }

            BakedModel model = dispatcher.getBlockModel(state);
            ModelData modelData = renderWorld.getModelData(pos);
            modelData = model.getModelData(renderWorld, pos, state, modelData);
            long randomSeed = state.getSeed(pos);
            random.setSeed(randomSeed);
            if (!model.getRenderTypes(state, random, modelData).contains(layer)) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            renderer.tesselateBlock(renderWorld, model, state, pos, poseStack, sbbBuilder, true, random, randomSeed,
                    OverlayTexture.NO_OVERLAY, modelData, layer);
            poseStack.popPose();
        }
        ModelBlockRenderer.clearCache();

        return sbbBuilder.end();
    }

    private void fixMultiBlockControllers() {
        for (BlockEntity blockEntity : renderedBlockEntities) {
            if (!(blockEntity instanceof IMultiBlockEntityContainer multiBlockEntity)) {
                continue;
            }

            BlockPos lastKnown = multiBlockEntity.getLastKnownPos();
            BlockPos current = blockEntity.getBlockPos();
            if (lastKnown == null || current == null || multiBlockEntity.isController()) {
                continue;
            }

            if (!lastKnown.equals(current)) {
                multiBlockEntity.setController(multiBlockEntity.getController().offset(current.subtract(lastKnown)));
            }
        }
    }

    private static boolean shouldSkipDynamicBlockEntity(BlockEntity blockEntity) {
        return blockEntity instanceof FunnelBlockEntity
                || blockEntity instanceof SmartChuteBlockEntity;
    }

    private static @Nullable BlockEntity readBlockEntity(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.nbt.CompoundTag tag) {
        if (tag == null || !state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }

        BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
        if (blockEntity == null) {
            return null;
        }

        net.minecraft.nbt.CompoundTag loadTag = tag.copy();
        loadTag.putInt("x", pos.getX());
        loadTag.putInt("y", pos.getY());
        loadTag.putInt("z", pos.getZ());

        blockEntity.setLevel(level);
        blockEntity.setBlockState(state);
        if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
            smartBlockEntity.markVirtual();
        }
        blockEntity.loadWithComponents(loadTag, level.registryAccess());
        blockEntity.handleUpdateTag(loadTag.copy(), level.registryAccess());
        blockEntity.setChanged();
        return blockEntity;
    }

    private static final class ThreadLocalObjects {
        private final PoseStack poseStack = new PoseStack();
        private final RandomSource random = RandomSource.createNewThreadLocalInstance();
        private final ShadedBlockSbbBuilder sbbBuilder = ShadedBlockSbbBuilder.create();
    }

    private record KineticSpeedSnapshot(KineticBlockEntity blockEntity, float speed) {
    }
}