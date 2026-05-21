package com.assestinar.createpocketfactory.network;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.item.PocketFactoryEntranceBlockItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetEntranceProjectionAnchorPacket(boolean hasAnchor, BlockPos anchor) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetEntranceProjectionAnchorPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID, "set_entrance_projection_anchor"));
    public static final StreamCodec<ByteBuf, SetEntranceProjectionAnchorPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SetEntranceProjectionAnchorPacket::hasAnchor,
            BlockPos.STREAM_CODEC,
            SetEntranceProjectionAnchorPacket::anchor,
            SetEntranceProjectionAnchorPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetEntranceProjectionAnchorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof PocketFactoryEntranceBlockItem)) {
                return;
            }

            if (packet.hasAnchor()) {
                PocketFactoryEntranceBlockItem.setProjectionAnchor(stack, packet.anchor());
            } else {
                PocketFactoryEntranceBlockItem.clearProjectionAnchor(stack);
            }
        });
    }
}