package com.assestinar.createpocketfactory.foundation.ponder.scenes;

import com.assestinar.createpocketfactory.block.ModBlocks;
import com.assestinar.createpocketfactory.item.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.gauge.GaugeBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.logistics.chute.ChuteBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class PocketFactoryScenes {
    private PocketFactoryScenes() {
    }

    public static void coreExpand(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("pocket_factory_core_expand", "Extending a Pocket Factory with the Core");
        scene.configureBasePlate(0, 0, 5);
        buildFactoryShell(scene, util);
        scene.world().showSection(util.select().fromTo(0, 0, 0, 4, 4, 4), Direction.DOWN);
        scene.idle(20);

        BlockPos floorA = util.grid().at(2, 0, 2);
        BlockPos wallA = util.grid().at(4, 2, 2);

        scene.overlay().showText(80)
            .text("The Pocket Factory Core is used to expand the factory itself.")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(floorA), Pointing.DOWN, 45)
            .rightClick()
            .withItem(ModItems.POCKET_FACTORY_INTERNAL_EYE.get().getDefaultInstance());
        scene.overlay().showText(80)
            .text("Right-clicking a white floor block can extend the factory downward.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(floorA));
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(wallA, Direction.WEST), Pointing.LEFT, 45)
            .rightClick()
            .withItem(ModItems.POCKET_FACTORY_INTERNAL_EYE.get().getDefaultInstance());
        scene.overlay().showText(90)
            .text("Right-clicking a white wall block can extend the factory sideways.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(wallA, Direction.WEST));
        scene.idle(100);

        scene.overlay().showText(80)
            .colored(PonderPalette.BLUE)
            .text("Horizontal expansion consumes a core. Vertical expansion grows one block at a time.")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(1, 1, 1));
        scene.idle(90);
    }

    public static void coreLink(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("pocket_factory_core_link", "Creating Linked Blocks with the Core");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos vault = util.grid().at(1, 1, 1);
        BlockPos tank = util.grid().at(3, 1, 1);
        BlockPos chute = util.grid().at(1, 1, 3);
        BlockPos pump = util.grid().at(2, 1, 3);
        BlockPos clutch = util.grid().at(3, 1, 3);

        place(scene, util, vault, AllBlocks.ITEM_VAULT.get());
        place(scene, util, tank, AllBlocks.FLUID_TANK.get());
        place(scene, util, chute, AllBlocks.CHUTE.get());
        place(scene, util, pump, AllBlocks.MECHANICAL_PUMP.get());
        place(scene, util, clutch, AllBlocks.CLUTCH.get());
        scene.idle(15);

        ItemStack core = ModItems.POCKET_FACTORY_INTERNAL_EYE.get().getDefaultInstance();
        scene.overlay().showControls(util.vector().topOf(vault), Pointing.DOWN, 35).rightClick().withItem(core);
        scene.overlay().showControls(util.vector().topOf(tank), Pointing.DOWN, 35).rightClick().withItem(core);
        scene.overlay().showControls(util.vector().topOf(chute), Pointing.DOWN, 35).rightClick().withItem(core);
        scene.overlay().showControls(util.vector().topOf(pump), Pointing.DOWN, 35).rightClick().withItem(core);
        scene.overlay().showControls(util.vector().topOf(clutch), Pointing.DOWN, 35).rightClick().withItem(core);
        scene.overlay().showText(90)
            .text("The core can also bind compatible Create blocks into linked variants.")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(100);

        scene.world().setBlock(vault, ModBlocks.LINKED_ITEM_VAULT.get().defaultBlockState(), true);
        scene.world().setBlock(tank, ModBlocks.LINKED_FLUID_TANK.get().defaultBlockState(), true);
        scene.world().setBlock(chute, ModBlocks.LINKED_CHUTE.get().defaultBlockState(), true);
        scene.world().setBlock(pump, ModBlocks.LINKED_MECHANICAL_PUMP.get().defaultBlockState(), true);
        scene.world().setBlock(clutch, ModBlocks.LINKED_CLUTCH.get().defaultBlockState(), true);
        scene.idle(20);

        scene.overlay().showText(90)
            .colored(PonderPalette.BLUE)
            .text("Linked blocks cannot be crafted directly. They are created by binding two matching endpoints.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(pump));
        scene.idle(100);
    }

    public static void entranceOverview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("pocket_factory_entrance", "Using the Pocket Factory Entrance");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos entrance = util.grid().at(2, 1, 2);
        place(scene, util, entrance, ModBlocks.POCKET_FACTORY_ENTRANCE.get());
        scene.idle(10);

        scene.overlay().showText(70)
            .text("放下入口后，它会绑定一个专属的口袋工厂。")
            .placeNearTarget()
            .pointAt(util.vector().topOf(entrance));
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(entrance), Pointing.DOWN, 45)
            .rightClick();
        scene.overlay().showText(90)
            .attachKeyFrame()
            .text("右键入口即可进入工厂。返回时会优先回到进入前记录的位置。")
            .placeNearTarget()
            .pointAt(util.vector().topOf(entrance));
        scene.idle(100);

        scene.overlay().showText(80)
            .colored(PonderPalette.GREEN)
            .text("即使入口被拆除，只要重新放下对应入口，仍可继续访问原工厂。")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(entrance, Direction.NORTH));
        scene.idle(90);
    }

    public static void entranceExitOverview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("pocket_factory_entrance_exit", "Leaving the Pocket Factory");
        scene.configureBasePlate(0, 0, 5);
        buildFactoryShell(scene, util);
        scene.world().showSection(util.select().fromTo(0, 0, 0, 4, 4, 4), Direction.DOWN);
        scene.idle(20);

        BlockPos grayFloor = util.grid().at(1, 0, 2);
        BlockPos grayWall = util.grid().at(4, 1, 1);

        scene.overlay().showText(80)
            .text("Inside the Pocket Factory, right-clicking any gray block returns you to the outside world.")
            .placeNearTarget()
            .pointAt(util.vector().topOf(grayFloor));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(grayFloor), Pointing.DOWN, 45)
            .rightClick();
        scene.overlay().showControls(util.vector().blockSurface(grayWall, Direction.WEST), Pointing.LEFT, 45)
            .rightClick();
        scene.overlay().showText(80)
            .attachKeyFrame()
            .text("Any Pocket Factory Block B surface can be used as an exit.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(grayWall, Direction.WEST));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(grayFloor), Pointing.DOWN, 55)
            .rightClick()
            .whileSneaking()
            .withItem(Blocks.COPPER_BLOCK.asItem().getDefaultInstance());
        scene.overlay().showText(90)
            .colored(PonderPalette.BLUE)
            .attachKeyFrame()
            .text("If you need to place blocks on the gray exit surface, hold Shift while right-clicking.")
            .placeNearTarget()
            .pointAt(util.vector().topOf(grayFloor));
        scene.idle(100);
    }

    public static void linkedChuteOverview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("linked_chute", "Binding Linked Chutes");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos leftVault = util.grid().at(1, 1, 2);
        BlockPos leftLower = util.grid().at(1, 2, 2);
        BlockPos leftUpper = util.grid().at(1, 3, 2);
        BlockPos chest = util.grid().at(1, 4, 2);

        BlockPos rightVault = util.grid().at(3, 1, 2);
        BlockPos rightLower = util.grid().at(3, 2, 2);
        BlockPos rightUpper = util.grid().at(3, 3, 2);

        place(scene, util, leftVault, AllBlocks.ITEM_VAULT.get());
        place(scene, util, leftLower, AllBlocks.CHUTE.get());
        place(scene, util, leftUpper, AllBlocks.CHUTE.get());
        place(scene, util, chest, Blocks.CHEST);
        place(scene, util, rightVault, AllBlocks.ITEM_VAULT.get());
        place(scene, util, rightLower, AllBlocks.CHUTE.get());
        place(scene, util, rightUpper, AllBlocks.CHUTE.get());
        scene.idle(15);

        scene.overlay().showControls(util.vector().blockSurface(leftLower, Direction.WEST), Pointing.RIGHT, 35)
            .rightClick()
            .withItem(AllItems.WRENCH.asStack());
        scene.overlay().showControls(util.vector().blockSurface(rightLower, Direction.EAST), Pointing.LEFT, 35)
            .rightClick()
            .withItem(AllItems.WRENCH.asStack());
        scene.world().modifyBlock(leftLower, state -> state.setValue(ChuteBlock.SHAPE, ChuteBlock.Shape.WINDOW), false);
        scene.world().modifyBlock(rightLower, state -> state.setValue(ChuteBlock.SHAPE, ChuteBlock.Shape.WINDOW), false);
        scene.overlay().showText(90)
            .text("Use a Wrench on the lower chutes to turn them into windowed chutes.")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 2, 2));
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(chest), Pointing.DOWN, 35)
            .rightClick()
            .withItem(new ItemStack(Items.IRON_INGOT));
        scene.overlay().showText(90)
            .text("With ordinary chutes, items simply fall straight down into the vault below.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().topOf(chest));
        scene.idle(20);
        scene.world().createItemEntity(util.vector().topOf(chest).add(0, 0.1, 0), util.vector().of(0, -0.12, 0), new ItemStack(Items.IRON_INGOT));
        scene.idle(18);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().centerOf(leftLower).add(0, -0.1, 0), util.vector().of(0, -0.08, 0), new ItemStack(Items.IRON_INGOT));
        scene.idle(18);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().centerOf(leftVault).add(0, 0.35, 0), util.vector().of(0, 0, 0), new ItemStack(Items.IRON_INGOT));
        scene.idle(35);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);

        ItemStack core = ModItems.POCKET_FACTORY_INTERNAL_EYE.get().getDefaultInstance();
        scene.overlay().showControls(util.vector().topOf(leftUpper), Pointing.DOWN, 35).rightClick().withItem(core);
        scene.overlay().showControls(util.vector().topOf(rightUpper), Pointing.DOWN, 35).rightClick().withItem(core);
        scene.overlay().showText(90)
            .text("Use the core on the upper two chutes to bind them into a linked pair.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 3, 2));
        scene.idle(100);

        scene.world().setBlock(leftUpper, ModBlocks.LINKED_CHUTE.get().defaultBlockState(), true);
        scene.world().setBlock(rightUpper, ModBlocks.LINKED_CHUTE.get().defaultBlockState(), true);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(chest), Pointing.DOWN, 35)
            .rightClick()
            .withItem(new ItemStack(Items.GOLD_INGOT));
        scene.overlay().showText(90)
            .colored(PonderPalette.BLUE)
            .text("After binding, items entering the left upper chute are sent to the other column and fall into the second vault.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(rightLower));
        scene.idle(20);
        scene.world().createItemEntity(util.vector().topOf(chest).add(0, 0.1, 0), util.vector().of(0, -0.12, 0), new ItemStack(Items.GOLD_INGOT));
        scene.idle(15);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().centerOf(rightUpper).add(0, -0.1, 0), util.vector().of(0, -0.08, 0), new ItemStack(Items.GOLD_INGOT));
        scene.idle(18);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().centerOf(rightLower).add(0, -0.1, 0), util.vector().of(0, -0.08, 0), new ItemStack(Items.GOLD_INGOT));
        scene.idle(18);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().centerOf(rightVault).add(0, 0.35, 0), util.vector().of(0, 0, 0), new ItemStack(Items.GOLD_INGOT));
        scene.idle(40);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
    }

    public static void linkedChuteFanOverview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("linked_chute_fan", "Linked Chutes and Encased Fans");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos leftBottom = util.grid().at(1, 1, 2);
        BlockPos leftMiddle = util.grid().at(1, 2, 2);
        BlockPos leftTop = util.grid().at(1, 3, 2);
        BlockPos rightLower = util.grid().at(3, 2, 2);
        BlockPos rightUpper = util.grid().at(3, 3, 2);
        BlockPos fan = util.grid().at(3, 1, 2);

        place(scene, util, leftBottom, AllBlocks.CHUTE.get());
        place(scene, util, leftMiddle, AllBlocks.CHUTE.get());
        place(scene, util, leftTop, AllBlocks.CHUTE.get());
        place(scene, util, rightLower, AllBlocks.CHUTE.get());
        place(scene, util, rightUpper, AllBlocks.CHUTE.get());
        scene.world().modifyBlock(leftTop, state -> state.setValue(ChuteBlock.SHAPE, ChuteBlock.Shape.WINDOW), false);
        scene.world().modifyBlock(rightLower, state -> state.setValue(ChuteBlock.SHAPE, ChuteBlock.Shape.WINDOW), false);
        scene.world().setBlock(fan, AllBlocks.ENCASED_FAN.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP), false);
        scene.world().showSection(util.select().position(fan), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(fan), 32);
        scene.idle(15);

        scene.effects().rotationDirectionIndicator(fan);
        scene.overlay().showText(90)
            .text("One column can be fed directly from an Encased Fan, while the other side is lowered by one block and capped with a windowed chute.")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(fan));
        scene.idle(80);

        ItemStack core = ModItems.POCKET_FACTORY_INTERNAL_EYE.get().getDefaultInstance();
        scene.overlay().showControls(util.vector().topOf(leftMiddle), Pointing.DOWN, 35)
            .rightClick()
            .withItem(core);
        scene.overlay().showControls(util.vector().topOf(rightUpper), Pointing.DOWN, 35)
            .rightClick()
            .withItem(core);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .text("Bind the raised chute above the fan to the middle chute of the opposite column.")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 2, 2));
        scene.idle(60);

        scene.world().setBlock(leftMiddle, ModBlocks.LINKED_CHUTE.get().defaultBlockState(), true);
        scene.world().setBlock(rightUpper, ModBlocks.LINKED_CHUTE.get().defaultBlockState(), true);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(rightLower), Pointing.DOWN, 35)
            .rightClick()
            .withItem(new ItemStack(Items.IRON_INGOT));
        scene.overlay().showText(90)
            .colored(PonderPalette.BLUE)
            .attachKeyFrame()
            .text("After binding, the airflow climbs the fan column, crosses through the linked pair, and bursts out of the top windowed chute on the opposite side.")
            .placeNearTarget()
            .pointAt(util.vector().topOf(leftTop));
        scene.idle(20);
        scene.world().createItemEntity(util.vector().centerOf(rightLower).add(0, -0.2, 0), util.vector().of(0, 0.08, 0), new ItemStack(Items.IRON_INGOT));
        scene.idle(18);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().centerOf(rightUpper).add(0, 0.2, 0), util.vector().of(0, 0.1, 0), new ItemStack(Items.IRON_INGOT));
        scene.idle(15);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().centerOf(leftMiddle).add(0, 0.1, 0), util.vector().of(0, 0.12, 0), new ItemStack(Items.IRON_INGOT));
        scene.idle(15);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
        scene.world().createItemEntity(util.vector().topOf(leftTop).add(0, 0.25, 0), util.vector().of(0, 0.18, 0), new ItemStack(Items.IRON_INGOT));
        scene.idle(40);
        scene.world().modifyEntities(ItemEntity.class, Entity::discard);
    }

    public static void linkedPumpOverview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("linked_pump", "Linked Mechanical Pumps across Dimensions");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos waterSource = util.grid().at(1, 1, 1);
        BlockPos lavaSource = util.grid().at(1, 1, 3);
        BlockPos waterPump = util.grid().at(2, 1, 1);
        BlockPos lavaPump = util.grid().at(2, 1, 3);
        BlockPos waterOutput = util.grid().at(3, 1, 1);
        BlockPos lavaOutput = util.grid().at(3, 1, 3);

        Selection sourceFluids = util.select().position(waterSource).add(util.select().position(lavaSource));
        Selection pumpsAndOutputs = util.select().position(waterPump)
            .add(util.select().position(lavaPump))
            .add(util.select().position(waterOutput))
            .add(util.select().position(lavaOutput));
        Selection pumps = util.select().position(waterPump).add(util.select().position(lavaPump));

        scene.world().setBlock(waterSource, Blocks.WATER.defaultBlockState(), false);
        scene.world().setBlock(lavaSource, Blocks.LAVA.defaultBlockState(), false);
        scene.world().setBlock(waterPump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST), false);
        scene.world().setBlock(lavaPump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST), false);
        scene.world().setBlock(waterOutput, Blocks.WATER.defaultBlockState(), false);
        scene.world().setBlock(lavaOutput, Blocks.LAVA.defaultBlockState(), false);

        scene.world().showSection(sourceFluids, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(pumpsAndOutputs, Direction.WEST);
        scene.idle(15);

        scene.world().setKineticSpeed(pumps, 0);
        scene.idle(10);

        scene.overlay().showText(90)
            .text("先看普通动力泵。这里不再展示管道，只保留两台泵尾部的水和岩浆，以及前方固定的输出结果。")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(20);
        scene.world().setKineticSpeed(pumps, 16);
        scene.effects().rotationDirectionIndicator(waterPump);
        scene.effects().rotationDirectionIndicator(lavaPump);
        scene.effects().indicateSuccess(waterOutput);
        scene.effects().indicateSuccess(lavaOutput);
        scene.overlay().showText(90)
            .colored(PonderPalette.BLUE)
            .text("普通状态下，两台泵都会把尾部的流体送到自己前方，所以水还是水，岩浆还是岩浆。")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(3, 1, 2));
        scene.idle(100);

        ItemStack core = ModItems.POCKET_FACTORY_INTERNAL_EYE.get().getDefaultInstance();
        scene.overlay().showControls(util.vector().topOf(waterPump), Pointing.DOWN, 35)
            .rightClick()
            .withItem(core);
        scene.overlay().showControls(util.vector().topOf(lavaPump), Pointing.DOWN, 35)
            .rightClick()
            .withItem(core);
        scene.overlay().showText(90)
            .text("接着使用核心，把这两台普通动力泵绑定成一对关联动力泵。")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(40);

        scene.world().setKineticSpeed(pumps, 0);
        scene.world().setBlock(waterPump, ModBlocks.LINKED_MECHANICAL_PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST), true);
        scene.world().setBlock(lavaPump, ModBlocks.LINKED_MECHANICAL_PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST), true);
        scene.idle(40);

        scene.overlay().showText(90)
            .text("绑定之后，不再看复杂流路，只直接把前方的输出结果手动清掉，再按关联后的尾部互换规则重新摆放。")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(20);
        scene.world().destroyBlock(waterOutput);
        scene.world().destroyBlock(lavaOutput);
        scene.idle(20);

        scene.overlay().showText(100)
            .colored(PonderPalette.BLUE)
            .text("因为关联动力泵会交换两侧尾部，所以绑定后，上方泵前面要改成岩浆，下方泵前面要改成水。")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(3, 1, 2));
        scene.idle(20);
        scene.world().setKineticSpeed(pumps, 16);
        scene.effects().rotationDirectionIndicator(waterPump);
        scene.effects().rotationDirectionIndicator(lavaPump);
        scene.idle(20);
        scene.world().setBlock(waterOutput, Blocks.LAVA.defaultBlockState(), true);
        scene.world().setBlock(lavaOutput, Blocks.WATER.defaultBlockState(), true);
        scene.effects().indicateSuccess(waterOutput);
        scene.effects().indicateSuccess(lavaOutput);
        scene.idle(110);
    }

    public static void linkedClutchOverview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("linked_clutch", "Linked Clutches in Local and Cross Modes");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos motor = util.grid().at(0, 1, 1);
        BlockPos topInput = util.grid().at(1, 1, 1);
        BlockPos topClutch = util.grid().at(2, 1, 1);
        BlockPos topOutput = util.grid().at(3, 1, 1);
        BlockPos topGauge = util.grid().at(4, 1, 1);

        BlockPos bottomInput = util.grid().at(1, 1, 3);
        BlockPos bottomClutch = util.grid().at(2, 1, 3);
        BlockPos bottomOutput = util.grid().at(3, 1, 3);
        BlockPos bottomGauge = util.grid().at(4, 1, 3);
        BlockPos bottomRedstone = util.grid().at(2, 1, 4);

        Selection topLine = util.select().position(motor)
            .add(util.select().position(topInput))
            .add(util.select().position(topClutch))
            .add(util.select().position(topOutput))
            .add(util.select().position(topGauge));
        Selection bottomLine = util.select().position(bottomInput)
            .add(util.select().position(bottomClutch))
            .add(util.select().position(bottomOutput))
            .add(util.select().position(bottomGauge));
        Selection topPowered = util.select().position(motor)
            .add(util.select().position(topInput))
            .add(util.select().position(topClutch))
            .add(util.select().position(topOutput))
            .add(util.select().position(topGauge));
        Selection bottomPowered = util.select().position(bottomClutch)
            .add(util.select().position(bottomOutput))
            .add(util.select().position(bottomGauge));
        Selection idleLine = util.select().position(bottomInput)
            .add(util.select().position(bottomClutch))
            .add(util.select().position(bottomOutput))
            .add(util.select().position(bottomGauge));

        scene.world().setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST), false);
        scene.world().setBlock(topInput, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Axis.X), false);
        scene.world().setBlock(topClutch, ModBlocks.LINKED_CLUTCH.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Axis.X), false);
        scene.world().setBlock(topOutput, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Axis.X), false);
        scene.world().setBlock(topGauge, AllBlocks.SPEEDOMETER.getDefaultState().setValue(GaugeBlock.FACING, Direction.WEST), false);

        scene.world().setBlock(bottomInput, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Axis.X), false);
        scene.world().setBlock(bottomClutch, ModBlocks.LINKED_CLUTCH.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Axis.X), false);
        scene.world().setBlock(bottomOutput, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Axis.X), false);
        scene.world().setBlock(bottomGauge, AllBlocks.SPEEDOMETER.getDefaultState().setValue(GaugeBlock.FACING, Direction.WEST), false);

        scene.world().showSection(topLine, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(bottomLine, Direction.DOWN);
        scene.idle(10);

        scene.world().setKineticSpeed(topPowered, 16);
        scene.world().setKineticSpeed(idleLine, 0);
        scene.effects().rotationSpeedIndicator(motor);
        scene.effects().indicateSuccess(topGauge);
        scene.overlay().showText(100)
            .text("这对关联离合器一开始就已经绑定好。上方线路接入创造马达，下方线路则没有任何应力输入。")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(topClutch));
        scene.idle(110);

        scene.overlay().showText(100)
            .text("当两侧红石状态一致时，关联离合器处于直通模式，输入动力只会沿本地这一条线路继续传到同侧速度表。")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(topGauge));
        scene.idle(110);

        scene.world().setBlock(bottomRedstone, Blocks.REDSTONE_BLOCK.defaultBlockState(), true);
        scene.world().showSection(util.select().position(bottomRedstone), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(topOutput).add(util.select().position(topGauge)), 0);
        scene.world().setKineticSpeed(bottomPowered, 16);
        scene.effects().indicateRedstone(bottomRedstone);
        scene.effects().indicateSuccess(bottomGauge);
        scene.overlay().showText(110)
            .colored(PonderPalette.BLUE)
            .text("只有一侧被激活时，它们会切换到交叉模式。此时上方线路输入的转速与应力会从另一侧末端输出到下方速度表。")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(bottomClutch));
        scene.idle(120);

        scene.overlay().showText(100)
            .colored(PonderPalette.RED)
            .text("所以关联离合器可以在两条线路之间切换动力去向：同信号时本地直通，不同信号时跨端交叉。")
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(110);
    }

    private static void place(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Block block) {
        scene.world().setBlock(pos, block.defaultBlockState(), false);
        scene.world().showSection(util.select().position(pos), Direction.DOWN);
    }

    private static void buildFactoryShell(CreateSceneBuilder scene, SceneBuildingUtil util) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                scene.world().setBlock(util.grid().at(x, 0, z), shellState(x, z), false);
            }
        }

        for (int y = 0; y < 5; y++) {
            for (int z = 0; z < 5; z++) {
                scene.world().setBlock(util.grid().at(4, y, z), shellState(y, z), false);
            }
        }

        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                scene.world().setBlock(util.grid().at(x, y, 4), shellState(x, y), false);
            }
        }
    }

    private static BlockState shellState(int first, int second) {
        return ((first + second) & 1) == 0
            ? ModBlocks.POCKET_FACTORY_BLOCK_A.get().defaultBlockState()
            : ModBlocks.POCKET_FACTORY_BLOCK_B.get().defaultBlockState();
    }

}