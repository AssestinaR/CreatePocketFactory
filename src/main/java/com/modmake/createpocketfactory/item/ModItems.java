package com.assestinar.createpocketfactory.item;

import com.assestinar.createpocketfactory.block.ModBlocks;
import com.assestinar.createpocketfactory.CreatePocketFactory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePocketFactory.MOD_ID);

    public static final DeferredItem<PocketFactoryItem> POCKET_FACTORY_EYE = ITEMS.registerItem(
            "pocket_factory_eye",
            PocketFactoryItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
    );

        public static final DeferredItem<PocketFactoryCoreItem> POCKET_FACTORY_INTERNAL_EYE = ITEMS.registerItem(
            "pocket_factory_internal_eye",
            PocketFactoryCoreItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
        );

        public static final DeferredItem<PocketFactoryEntranceBlockItem> POCKET_FACTORY_ENTRANCE = ITEMS.register(
            "pocket_factory_entrance",
            () -> new PocketFactoryEntranceBlockItem(ModBlocks.POCKET_FACTORY_ENTRANCE.get(), new Item.Properties())
        );
        public static final DeferredItem<LinkedChuteBlockItem> LINKED_CHUTE = ITEMS.register(
            "linked_chute",
            () -> new LinkedChuteBlockItem(ModBlocks.LINKED_CHUTE.get(), linkedProperties())
        );
    public static final DeferredItem<BlockItem> LINKED_CLUTCH = registerLinkedBlockItem(ModBlocks.LINKED_CLUTCH);
    public static final DeferredItem<BlockItem> LINKED_MECHANICAL_PUMP = registerLinkedBlockItem(ModBlocks.LINKED_MECHANICAL_PUMP);
    public static final DeferredItem<BlockItem> POCKET_FACTORY_BLOCK_A = registerBlockItem(ModBlocks.POCKET_FACTORY_BLOCK_A);
    public static final DeferredItem<BlockItem> POCKET_FACTORY_BLOCK_B = registerBlockItem(ModBlocks.POCKET_FACTORY_BLOCK_B);
        public static final DeferredItem<LinkedItemVaultBlockItem> LINKED_ITEM_VAULT = ITEMS.register(
            "linked_item_vault",
            () -> new LinkedItemVaultBlockItem(ModBlocks.LINKED_ITEM_VAULT.get(), linkedProperties())
        );
        public static final DeferredItem<LinkedFluidTankBlockItem> LINKED_FLUID_TANK = ITEMS.register(
            "linked_fluid_tank",
            () -> new LinkedFluidTankBlockItem(ModBlocks.LINKED_FLUID_TANK.get(), linkedProperties())
        );

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(POCKET_FACTORY_EYE.get());
        }

        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(POCKET_FACTORY_INTERNAL_EYE.get());
        }

        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.POCKET_FACTORY_ENTRANCE.get());
            event.accept(ModBlocks.POCKET_FACTORY_BLOCK_A.get());
            event.accept(ModBlocks.POCKET_FACTORY_BLOCK_B.get());
        }
    }

    private static Item.Properties linkedProperties() {
        return new Item.Properties();
    }

    private static DeferredItem<BlockItem> registerBlockItem(DeferredBlock<? extends Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static DeferredItem<BlockItem> registerLinkedBlockItem(DeferredBlock<? extends Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new LinkedBlockItem(block.get(), linkedProperties()));
    }
}