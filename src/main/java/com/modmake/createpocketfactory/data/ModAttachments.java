package com.modmake.createpocketfactory.data;

import com.modmake.createpocketfactory.CreatePocketFactory;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CreatePocketFactory.MOD_ID);

    public static final Supplier<AttachmentType<ReturnPointData>> RETURN_POINT = ATTACHMENT_TYPES.register(
            "return_point",
            () -> AttachmentType.builder(ReturnPointData::empty)
                    .serialize(ReturnPointData.CODEC)
                    .copyOnDeath()
                    .build()
    );

    private ModAttachments() {
    }
}