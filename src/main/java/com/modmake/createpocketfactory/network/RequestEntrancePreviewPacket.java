package com.modmake.createpocketfactory.network;

import com.modmake.createpocketfactory.CreatePocketFactory;
import com.modmake.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestEntrancePreviewPacket(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestEntrancePreviewPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID, "request_entrance_preview"));
    public static final StreamCodec<ByteBuf, RequestEntrancePreviewPacket> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(RequestEntrancePreviewPacket::new, RequestEntrancePreviewPacket::pos);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestEntrancePreviewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(packet.pos())) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pos()) instanceof PocketFactoryEntranceBlockEntity entrance)) {
                return;
            }
            entrance.refreshPreviewSnapshot();
        });
    }
}
