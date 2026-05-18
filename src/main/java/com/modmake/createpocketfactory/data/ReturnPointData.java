package com.modmake.createpocketfactory.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record ReturnPointData(
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        float yRot,
        float xRot,
        boolean valid
) {
    private static final ResourceLocation DEFAULT_DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    public static final Codec<ReturnPointData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("dimension", DEFAULT_DIMENSION).forGetter(ReturnPointData::dimensionId),
            Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(ReturnPointData::x),
            Codec.DOUBLE.optionalFieldOf("y", 0.0D).forGetter(ReturnPointData::y),
            Codec.DOUBLE.optionalFieldOf("z", 0.0D).forGetter(ReturnPointData::z),
            Codec.FLOAT.optionalFieldOf("y_rot", 0.0F).forGetter(ReturnPointData::yRot),
            Codec.FLOAT.optionalFieldOf("x_rot", 0.0F).forGetter(ReturnPointData::xRot),
            Codec.BOOL.optionalFieldOf("valid", false).forGetter(ReturnPointData::valid)
    ).apply(instance, ReturnPointData::new));

    public static ReturnPointData empty() {
        return new ReturnPointData(DEFAULT_DIMENSION, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, false);
    }

    public static ReturnPointData capture(ServerPlayer player) {
        return new ReturnPointData(
                player.level().dimension().location(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                true
        );
    }

    public ResourceKey<Level> dimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, this.dimensionId);
    }
}