package com.modmake.createpocketfactory.client.render;

import com.modmake.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.modmake.createpocketfactory.network.RequestEntrancePreviewPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PocketFactoryEntranceRenderer implements BlockEntityRenderer<PocketFactoryEntranceBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public PocketFactoryEntranceRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PocketFactoryEntranceBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        if (blockEntity.getLevel() != null) {
            long gameTime = blockEntity.getLevel().getGameTime();
            if (blockEntity.shouldRequestPreview(gameTime)) {
                blockEntity.markPreviewRequest(gameTime);
                PacketDistributor.sendToServer(new RequestEntrancePreviewPacket(blockEntity.getBlockPos()));
            }
        }

        if (!blockEntity.hasPreviewBlocks()) {
            return;
        }

        float rotation = ((blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime()) + partialTick) * 2.0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.52D, 0.5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(18.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        float scale = 0.055F;
        poseStack.scale(scale, scale, scale);
        for (PocketFactoryEntranceBlockEntity.PreviewBlock previewBlock : blockEntity.getPreviewBlocks()) {
            poseStack.pushPose();
            poseStack.translate(previewBlock.x() - 7.5F, previewBlock.y() - 7.5F, previewBlock.z() - 7.5F);
            blockRenderer.renderSingleBlock(previewBlock.state(), poseStack, bufferSource, packedLight, packedOverlay);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}