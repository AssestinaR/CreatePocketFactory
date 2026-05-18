package com.modmake.createpocketfactory.block.entity;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;

public interface LinkedClutchEndpoint {
    int getBindingId();

    int getFactoryId();

    boolean hasBinding();

    boolean isInternalEndpoint();

    void setBinding(int bindingId, int factoryId, boolean internalEndpoint);

    @Nullable
    DriveInput describeLocalDriveInput();

    record DriveInput(float speed, Direction.AxisDirection axisDirection, float availableStress) {
    }
}