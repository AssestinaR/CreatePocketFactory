package com.modmake.createpocketfactory.block.entity;

import javax.annotation.Nullable;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;

import net.minecraft.core.Direction;

public interface LinkedPipeEndpoint {
    int getBindingId();

    int getFactoryId();

    boolean hasBinding();

    boolean isInternalEndpoint();

    void setBinding(int bindingId, int factoryId, boolean internalEndpoint);

    LinkedPipeTransportHelper.BridgeMode getBridgeMode();

    @Nullable Direction getBridgeInputFace();

    float getMirroredBridgePressure();

    void updateBridgeState(LinkedPipeTransportHelper.BridgeMode mode, @Nullable Direction inputFace, float mirroredPressure);

    FluidTransportBehaviour getTransportBehaviour();
}
