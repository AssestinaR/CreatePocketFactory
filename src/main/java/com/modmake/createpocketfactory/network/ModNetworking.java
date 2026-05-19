package com.assestinar.createpocketfactory.network;

import com.assestinar.createpocketfactory.CreatePocketFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CreatePocketFactory.MOD_ID).versioned("1");
        registrar.playToServer(
                RequestEntrancePreviewPacket.TYPE,
                RequestEntrancePreviewPacket.STREAM_CODEC,
                RequestEntrancePreviewPacket::handle
        );
    }
}
