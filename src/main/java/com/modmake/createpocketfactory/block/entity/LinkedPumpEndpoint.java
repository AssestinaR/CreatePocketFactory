package com.assestinar.createpocketfactory.block.entity;

public interface LinkedPumpEndpoint {
    int getBindingId();

    int getFactoryId();

    boolean hasBinding();

    boolean isInternalEndpoint();

    void setBinding(int bindingId, int factoryId, boolean internalEndpoint);
}