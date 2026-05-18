package com.modmake.createpocketfactory.debug;

import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.item.PocketFactoryEntranceBlockItem;
import com.modmake.createpocketfactory.world.PocketFactoryTeleporter;
import com.modmake.createpocketfactory.world.PocketFactorySavedData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class PocketFactoryDebugCommands {
    private static final SimpleCommandExceptionType REQUIRES_EYE = new SimpleCommandExceptionType(
            Component.translatable("debug.create_pocket_factory.menu.requires_eye")
    );

    private PocketFactoryDebugCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("pocketfactory")
                .then(Commands.literal("debug")
                        .then(Commands.literal("open")
                                .executes(context -> openMenu(requireDebugUser(context.getSource()))))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("factoryId", IntegerArgumentType.integer(1))
                                        .executes(context -> teleportToFactory(
                                                requireDebugUser(context.getSource()),
                                                IntegerArgumentType.getInteger(context, "factoryId")
                                        ))))
                        .then(Commands.literal("recover")
                            .then(Commands.argument("factoryId", IntegerArgumentType.integer(1))
                                .executes(context -> recoverEntrance(
                                    requireDebugUser(context.getSource()),
                                    IntegerArgumentType.getInteger(context, "factoryId")
                                ))))
                        .then(Commands.literal("return")
                                .executes(context -> returnFromFactory(requireDebugUser(context.getSource())))));
    }

    private static int openMenu(ServerPlayer player) {
        PocketFactoryDebugMenu.open(player);
        return 1;
    }

    private static int teleportToFactory(ServerPlayer player, int factoryId) {
        return PocketFactoryTeleporter.enterFactory(player, factoryId) ? 1 : 0;
    }

    private static int returnFromFactory(ServerPlayer player) {
        return PocketFactoryTeleporter.returnFromFactory(player) ? 1 : 0;
    }

    private static int recoverEntrance(ServerPlayer player, int factoryId) {
        PocketFactorySavedData.FactoryRecord factory = PocketFactorySavedData.get(player.server).getFactory(factoryId);
        if (factory == null) {
            player.sendSystemMessage(Component.translatable("debug.create_pocket_factory.menu.factory_missing", factoryId));
            return 0;
        }

        if (factory.hasEntrance()) {
            player.sendSystemMessage(Component.translatable("debug.create_pocket_factory.menu.factory_has_entrance", factoryId));
            return 0;
        }

        player.addItem(PocketFactoryEntranceBlockItem.createBoundStack(ModBlocks.POCKET_FACTORY_ENTRANCE.get(), factoryId));
        player.sendSystemMessage(Component.translatable("debug.create_pocket_factory.menu.recover_granted", factoryId));
        return 1;
    }

    private static ServerPlayer requireDebugUser(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PocketFactoryDebugMenu.canUse(player)) {
            throw REQUIRES_EYE.create();
        }
        return player;
    }
}