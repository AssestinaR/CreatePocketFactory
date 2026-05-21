package com.assestinar.createpocketfactory.client.render;

import com.assestinar.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.assestinar.createpocketfactory.config.ModConfigs;
import com.assestinar.createpocketfactory.item.ModItems;
import com.assestinar.createpocketfactory.network.RequestEntrancePreviewPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

public final class PocketFactoryEntranceRenderer implements BlockEntityRenderer<PocketFactoryEntranceBlockEntity> {
    private final ItemRenderer itemRenderer;
    private static final ItemStack CORE_STACK = new ItemStack(ModItems.POCKET_FACTORY_INTERNAL_EYE.get());
    private final Map<PocketFactoryEntranceBlockEntity, CachedProjection> projectionCache = new WeakHashMap<>();

    public PocketFactoryEntranceRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(PocketFactoryEntranceBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        boolean showPreview = ModConfigs.entranceProjectionMode().showsMiniaturePreview();
        if (showPreview && blockEntity.getLevel() != null) {
            long gameTime = blockEntity.getLevel().getGameTime();
            if (blockEntity.shouldRequestPreview(gameTime)) {
                blockEntity.markPreviewRequest(gameTime);
                PacketDistributor.sendToServer(new RequestEntrancePreviewPacket(blockEntity.getBlockPos()));
            }
        }

        if (!showPreview) {
            renderCoreModel(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            renderLargeProjection(blockEntity, poseStack, bufferSource, partialTick, null);
            return;
        }

        if (!blockEntity.hasPreviewBlocks()) {
            renderCoreModel(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            renderLargeProjection(blockEntity, poseStack, bufferSource, partialTick, null);
            return;
        }

        float rotation = ModConfigs.rotateEntrancePreview()
                ? ((blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime()) + partialTick) * 2.0F
                : 0.0F;
        PocketFactoryProjectionCache cache = getProjectionCache(blockEntity);
        if (cache == null) {
            renderCoreModel(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            renderLargeProjection(blockEntity, poseStack, bufferSource, partialTick, null);
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.505D, 0.5D);
        if (rotation != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        }

        float scale = cache.getMiniatureScale();
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-cache.getBounds().getCenter().x, -cache.getBounds().minY, -cache.getBounds().getCenter().z);
        cache.render(poseStack, bufferSource, blockEntity.getLevel(), partialTick,
            new Matrix4f().translation(blockEntity.getBlockPos().getX(), blockEntity.getBlockPos().getY(), blockEntity.getBlockPos().getZ()),
            ModConfigs.renderProjectionBlockEntities(),
            ModConfigs.renderDynamicProjectionParts());
        poseStack.popPose();
        renderLargeProjection(blockEntity, poseStack, bufferSource, partialTick, cache);
    }

    @Override
    public boolean shouldRender(PocketFactoryEntranceBlockEntity blockEntity, Vec3 cameraPos) {
        if (Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPos, getViewDistance())) {
            return true;
        }

        return blockEntity.hasProjectionAnchor() && Vec3.atCenterOf(blockEntity.getProjectionAnchor()).closerThan(cameraPos, getViewDistance());
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private void renderLargeProjection(PocketFactoryEntranceBlockEntity blockEntity, PoseStack poseStack,
                                       MultiBufferSource bufferSource, float partialTick, PocketFactoryProjectionCache cache) {
        if (!ModConfigs.entranceProjectionMode().showsLargeProjection() || !blockEntity.hasProjectionAnchor() || !blockEntity.hasPreviewBlocks()) {
            return;
        }

        if (cache == null) {
            cache = getProjectionCache(blockEntity);
            if (cache == null) {
                return;
            }
        }

        BlockPos projectionAnchor = blockEntity.getProjectionAnchor();
        Vec3 offset = Vec3.atCenterOf(projectionAnchor).subtract(Vec3.atCenterOf(blockEntity.getBlockPos()));

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        cache.applyTransform(poseStack,
            blockEntity.getProjectionRotationQuarterTurns(),
            blockEntity.isProjectionFlipX(),
            blockEntity.isProjectionFlipZ());
        cache.render(poseStack, bufferSource, blockEntity.getLevel(), partialTick,
            new Matrix4f().translation(projectionAnchor.getX(), projectionAnchor.getY(), projectionAnchor.getZ()),
            ModConfigs.renderProjectionBlockEntities(),
            ModConfigs.renderDynamicProjectionParts());
        poseStack.popPose();
    }

    private PocketFactoryProjectionCache getProjectionCache(PocketFactoryEntranceBlockEntity blockEntity) {
        if (blockEntity.getLevel() == null || !blockEntity.hasPreviewBlocks()) {
            projectionCache.remove(blockEntity);
            return null;
        }

        CachedProjection cached = projectionCache.get(blockEntity);
        if (cached != null && cached.hash == blockEntity.getPreviewHash()) {
            return cached.cache;
        }

        PocketFactoryProjectionCache rebuilt = new PocketFactoryProjectionCache(blockEntity.getLevel(), blockEntity.getPreviewBlocks());
        projectionCache.put(blockEntity, new CachedProjection(blockEntity.getPreviewHash(), rebuilt));
        return rebuilt;
    }

    private record CachedProjection(int hash, PocketFactoryProjectionCache cache) {
    }

    private void renderCoreModel(PocketFactoryEntranceBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float rotation = ModConfigs.rotateEntrancePreview()
                ? ((blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime()) + partialTick) * 4.0F
                : 0.0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.08D, 0.5D);
        if (rotation != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        }
        poseStack.scale(0.85F, 0.85F, 0.85F);
        itemRenderer.renderStatic(
                CORE_STACK,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                0
        );
        poseStack.popPose();
    }
}