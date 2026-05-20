package com.assestinar.createpocketfactory.client.render;

import com.assestinar.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.assestinar.createpocketfactory.config.ModConfigs;
import com.assestinar.createpocketfactory.item.ModItems;
import com.assestinar.createpocketfactory.network.RequestEntrancePreviewPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PocketFactoryEntranceRenderer implements BlockEntityRenderer<PocketFactoryEntranceBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;
    private final ItemRenderer itemRenderer;
    private static final ItemStack CORE_STACK = new ItemStack(ModItems.POCKET_FACTORY_INTERNAL_EYE.get());

    public PocketFactoryEntranceRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(PocketFactoryEntranceBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        boolean showPreview = ModConfigs.showEntrancePreview();
        if (showPreview && blockEntity.getLevel() != null) {
            long gameTime = blockEntity.getLevel().getGameTime();
            if (blockEntity.shouldRequestPreview(gameTime)) {
                blockEntity.markPreviewRequest(gameTime);
                PacketDistributor.sendToServer(new RequestEntrancePreviewPacket(blockEntity.getBlockPos()));
            }
        }

        if (!showPreview) {
            renderCoreModel(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        if (!blockEntity.hasPreviewBlocks()) {
            return;
        }

        float rotation = ModConfigs.rotateEntrancePreview()
                ? ((blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime()) + partialTick) * 2.0F
                : 0.0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.505D, 0.5D);
        if (rotation != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        }

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