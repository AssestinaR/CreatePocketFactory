package com.modmake.createpocketfactory.block.entity;

import com.modmake.createpocketfactory.CreatePocketFactory;
import com.modmake.createpocketfactory.block.ModBlocks;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePocketFactory.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PocketFactoryEntranceBlockEntity>> POCKET_FACTORY_ENTRANCE = BLOCK_ENTITY_TYPES.register(
            "pocket_factory_entrance",
            () -> BlockEntityType.Builder.of(PocketFactoryEntranceBlockEntity::new, ModBlocks.POCKET_FACTORY_ENTRANCE.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PocketFactoryPortalBlockEntity>> POCKET_FACTORY_PORTAL = BLOCK_ENTITY_TYPES.register(
            "pocket_factory_portal",
            () -> BlockEntityType.Builder.of(PocketFactoryPortalBlockEntity::new, ModBlocks.POCKET_FACTORY_PORTAL.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkedChuteBlockEntity>> LINKED_CHUTE = BLOCK_ENTITY_TYPES.register(
            "linked_chute",
            () -> BlockEntityType.Builder.of(LinkedChuteBlockEntity::new, ModBlocks.LINKED_CHUTE.get()).build(null)
    );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkedItemVaultBlockEntity>> LINKED_ITEM_VAULT = BLOCK_ENTITY_TYPES.register(
            "linked_item_vault",
            () -> BlockEntityType.Builder.of(LinkedItemVaultBlockEntity::new, ModBlocks.LINKED_ITEM_VAULT.get()).build(null)
        );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkedFluidTankBlockEntity>> LINKED_FLUID_TANK = BLOCK_ENTITY_TYPES.register(
            "linked_fluid_tank",
            () -> BlockEntityType.Builder.of(LinkedFluidTankBlockEntity::new, ModBlocks.LINKED_FLUID_TANK.get()).build(null)
        );

            public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkedPumpBlockEntity>> LINKED_MECHANICAL_PUMP = BLOCK_ENTITY_TYPES.register(
                "linked_mechanical_pump",
                () -> BlockEntityType.Builder.of((pos, state) -> new LinkedPumpBlockEntity(ModBlockEntities.LINKED_MECHANICAL_PUMP.get(), pos, state), ModBlocks.LINKED_MECHANICAL_PUMP.get()).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(LinkedItemVaultBlockEntity::registerCapabilities);
        modBus.addListener(LinkedFluidTankBlockEntity::registerCapabilities);
        modBus.addListener(LinkedPumpBlockEntity::registerCapabilities);
    }
}