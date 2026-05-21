package com.assestinar.createpocketfactory.network;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import com.assestinar.createpocketfactory.block.entity.PocketFactoryEntranceBlockEntity;
import com.assestinar.createpocketfactory.item.PocketFactoryCoreItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetBoundEntranceProjectionStatePacket(boolean hasAnchor, BlockPos anchor, int rotationQuarterTurns,
                                                    boolean flipX, boolean flipZ) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetBoundEntranceProjectionStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreatePocketFactory.MOD_ID,
                    "set_bound_entrance_projection_state"));
    public static final StreamCodec<ByteBuf, SetBoundEntranceProjectionStatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SetBoundEntranceProjectionStatePacket::hasAnchor,
            BlockPos.STREAM_CODEC,
            SetBoundEntranceProjectionStatePacket::anchor,
            ByteBufCodecs.INT,
            SetBoundEntranceProjectionStatePacket::rotationQuarterTurns,
            ByteBufCodecs.BOOL,
            SetBoundEntranceProjectionStatePacket::flipX,
            ByteBufCodecs.BOOL,
            SetBoundEntranceProjectionStatePacket::flipZ,
            SetBoundEntranceProjectionStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetBoundEntranceProjectionStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof PocketFactoryCoreItem)) {
                return;
            }

            PocketFactoryCoreItem.BoundEntrance boundEntrance = PocketFactoryCoreItem.getBoundEntrance(stack);
            if (boundEntrance == null) {
                return;
            }

            ServerLevel level = player.server.getLevel(boundEntrance.dimension());
            if (level == null) {
                return;
            }

            if (!(level.getBlockEntity(boundEntrance.pos()) instanceof PocketFactoryEntranceBlockEntity entranceBlockEntity)) {
                return;
            }

            entranceBlockEntity.setProjectionState(
                    packet.hasAnchor() ? packet.anchor() : null,
                    packet.rotationQuarterTurns(),
                    packet.flipX(),
                    packet.flipZ()
            );
        });
    }
}