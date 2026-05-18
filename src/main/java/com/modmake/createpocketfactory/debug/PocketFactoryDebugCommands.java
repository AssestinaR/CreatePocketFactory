package com.modmake.createpocketfactory.debug;

import com.modmake.createpocketfactory.block.ModBlocks;
import com.modmake.createpocketfactory.debug.PocketFactoryDebugMenu;
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
    private static final SimpleCommandExceptionType REQUIRES_PORTABLE_CORE = new SimpleCommandExceptionType(
            Component.translatable("debug.create_pocket_factory.menu.requires_eye")
    );

    private PocketFactoryDebugCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("pocketfactory")
            .then(portableRoot())
            .then(debugRoot());
        }

        private static LiteralArgumentBuilder<CommandSourceStack> debugRoot() {
        return Commands.literal("debug")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("open")
                .executes(context -> openMenu(context.getSource().getPlayerOrException())))
            .then(Commands.literal("tp")
                .then(Commands.argument("factoryId", IntegerArgumentType.integer(1))
                    .executes(context -> teleportToFactory(
                        context.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(context, "factoryId")
                    ))))
            .then(Commands.literal("recover")
                .then(Commands.argument("factoryId", IntegerArgumentType.integer(1))
                .executes(context -> recoverEntrance(
                    context.getSource().getPlayerOrException(),
                    IntegerArgumentType.getInteger(context, "factoryId")
                ))))
            .then(Commands.literal("return")
                .executes(context -> returnFromFactory(context.getSource().getPlayerOrException())));
        }

        private static LiteralArgumentBuilder<CommandSourceStack> portableRoot() {
        return Commands.literal("portable")
            .then(Commands.literal("open")
                .executes(context -> openPortableMenu(requirePortableUser(context.getSource()))))
            .then(Commands.literal("tp")
                .then(Commands.argument("factoryId", IntegerArgumentType.integer(1))
                    .executes(context -> teleportToFactory(
                        requirePortableUser(context.getSource()),
                        IntegerArgumentType.getInteger(context, "factoryId")
                    ))))
            .then(Commands.literal("return")
                .executes(context -> returnFromFactory(requirePortableUser(context.getSource()))));
    }

    private static int openMenu(ServerPlayer player) {
        PocketFactoryDebugMenu.openAdmin(player);
        return 1;
    }

        private static int openPortableMenu(ServerPlayer player) {
        PocketFactoryDebugMenu.openPortable(player);
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

    private static ServerPlayer requirePortableUser(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PocketFactoryDebugMenu.canUsePortable(player)) {
            throw REQUIRES_PORTABLE_CORE.create();
        }
        return player;
    }
}