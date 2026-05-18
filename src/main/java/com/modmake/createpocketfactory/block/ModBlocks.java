package com.modmake.createpocketfactory.block;

import com.modmake.createpocketfactory.CreatePocketFactory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePocketFactory.MOD_ID);

    private static final BlockBehaviour.Properties FACTORY_BLOCK_PROPERTIES = BlockBehaviour.Properties.of()
            .strength(-1.0F, 3600000.0F)
            .sound(SoundType.GLASS)
            .pushReaction(PushReaction.BLOCK)
            .lightLevel(state -> 15);

    private static final BlockBehaviour.Properties ENTRANCE_BLOCK_PROPERTIES = BlockBehaviour.Properties.of()
            .strength(5.0F, 1200.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> 10)
            .noOcclusion();

    private static final BlockBehaviour.Properties PORTAL_BLOCK_PROPERTIES = BlockBehaviour.Properties.of()
            .strength(5.0F, 1200.0F)
            .sound(SoundType.GLASS)
            .lightLevel(state -> 10)
            .noOcclusion()
            .noCollission();

    private static final BlockBehaviour.Properties LINKED_VAULT_PROPERTIES = BlockBehaviour.Properties.of()
            .strength(5.0F, 6.0F)
            .sound(SoundType.NETHERITE_BLOCK);

    private static final BlockBehaviour.Properties LINKED_TANK_PROPERTIES = BlockBehaviour.Properties.of()
            .strength(3.0F, 6.0F)
            .sound(SoundType.COPPER)
            .noOcclusion();

    private static final BlockBehaviour.Properties LINKED_CHUTE_PROPERTIES = BlockBehaviour.Properties.of()
            .strength(2.0F, 6.0F)
            .sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion();

        private static final BlockBehaviour.Properties LINKED_PUMP_PROPERTIES = BlockBehaviour.Properties.of()
                        .strength(2.0F, 6.0F)
                        .sound(SoundType.COPPER)
                        .noOcclusion();

    public static final DeferredBlock<Block> POCKET_FACTORY_BLOCK_A = BLOCKS.register(
            "pocket_factory_block_a",
            () -> new PocketFactoryBoundaryBlock(FACTORY_BLOCK_PROPERTIES)
    );

    public static final DeferredBlock<Block> POCKET_FACTORY_BLOCK_B = BLOCKS.register(
            "pocket_factory_block_b",
            () -> new PocketFactoryExitBlock(FACTORY_BLOCK_PROPERTIES)
    );

    public static final DeferredBlock<BaseEntityBlock> POCKET_FACTORY_ENTRANCE = BLOCKS.register(
            "pocket_factory_entrance",
            () -> new PocketFactoryEntranceBlock(ENTRANCE_BLOCK_PROPERTIES)
    );

    public static final DeferredBlock<BaseEntityBlock> POCKET_FACTORY_PORTAL = BLOCKS.register(
            "pocket_factory_portal",
            () -> new PocketFactoryPortalBlock(PORTAL_BLOCK_PROPERTIES)
    );

    public static final DeferredBlock<LinkedChuteBlock> LINKED_CHUTE = BLOCKS.register(
            "linked_chute",
            () -> new LinkedChuteBlock(LINKED_CHUTE_PROPERTIES)
    );

    public static final DeferredBlock<LinkedMechanicalPumpBlock> LINKED_MECHANICAL_PUMP = BLOCKS.register(
            "linked_mechanical_pump",
            () -> new LinkedMechanicalPumpBlock(LINKED_PUMP_PROPERTIES)
    );

    public static final DeferredBlock<LinkedItemVaultBlock> LINKED_ITEM_VAULT = BLOCKS.register(
            "linked_item_vault",
            () -> new LinkedItemVaultBlock(LINKED_VAULT_PROPERTIES)
    );

    public static final DeferredBlock<LinkedFluidTankBlock> LINKED_FLUID_TANK = BLOCKS.register(
            "linked_fluid_tank",
            () -> new LinkedFluidTankBlock(LINKED_TANK_PROPERTIES)
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
