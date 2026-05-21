package com.assestinar.createpocketfactory.network;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.client.EntranceProjectionHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncFactoryProjectionPreviewPacket(int factoryId, CompoundTag previewTag) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncFactoryProjectionPreviewPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID, "sync_factory_projection_preview"));
    public static final StreamCodec<ByteBuf, SyncFactoryProjectionPreviewPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SyncFactoryProjectionPreviewPacket::factoryId,
            ByteBufCodecs.COMPOUND_TAG,
            SyncFactoryProjectionPreviewPacket::previewTag,
            SyncFactoryProjectionPreviewPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncFactoryProjectionPreviewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> EntranceProjectionHandler.acceptPreview(packet.factoryId(), packet.previewTag()));
    }
}