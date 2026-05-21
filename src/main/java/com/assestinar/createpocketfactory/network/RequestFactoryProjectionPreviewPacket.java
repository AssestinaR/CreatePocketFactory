package com.assestinar.createpocketfactory.network;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.block.entity.PocketFactoryPreviewHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestFactoryProjectionPreviewPacket(int factoryId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestFactoryProjectionPreviewPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID, "request_factory_projection_preview"));
    public static final StreamCodec<ByteBuf, RequestFactoryProjectionPreviewPacket> STREAM_CODEC =
            ByteBufCodecs.INT.map(RequestFactoryProjectionPreviewPacket::new, RequestFactoryProjectionPreviewPacket::factoryId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestFactoryProjectionPreviewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || packet.factoryId() <= 0) {
                return;
            }

            PacketDistributor.sendToPlayer(player, new SyncFactoryProjectionPreviewPacket(
                    packet.factoryId(),
                    PocketFactoryPreviewHelper.writePreviewBlocks(PocketFactoryPreviewHelper.sampleFactoryPreview(player.serverLevel(), packet.factoryId()))));
        });
    }
}