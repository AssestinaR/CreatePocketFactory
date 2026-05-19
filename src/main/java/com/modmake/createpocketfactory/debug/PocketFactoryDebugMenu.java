package com.assestinar.createpocketfactory.debug;

import com.assestinar.createpocketfactory.item.ModItems;
import com.assestinar.createpocketfactory.world.PocketFactoryDimensions;
import com.assestinar.createpocketfactory.world.PocketFactorySavedData;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PocketFactoryDebugMenu {
    private PocketFactoryDebugMenu() {
    }

    public static boolean canUsePortable(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.POCKET_FACTORY_EYE.get())) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.POCKET_FACTORY_EYE.get())) {
                return true;
            }
        }
        return false;
    }

    public static void openPortable(ServerPlayer player) {
        open(player, false, "/pocketfactory portable");
    }

    public static void openAdmin(ServerPlayer player) {
        open(player, true, "/pocketfactory debug");
    }

    private static void open(ServerPlayer player, boolean allowRecovery, String commandRoot) {
        PocketFactorySavedData savedData = PocketFactorySavedData.get(player.server);

        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.translatable("debug.create_pocket_factory.menu.title").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable(
                "debug.create_pocket_factory.menu.context",
                player.level().dimension().location().toString(),
                savedData.getFactories().size()
        ).withStyle(ChatFormatting.GRAY));

        if (player.level().dimension() == PocketFactoryDimensions.LEVEL_KEY) {
            player.sendSystemMessage(actionLine(
                    Component.translatable("debug.create_pocket_factory.menu.action.return"),
                commandRoot + " return"
            ));
        }

        player.sendSystemMessage(actionLine(
                Component.translatable("debug.create_pocket_factory.menu.action.refresh"),
            commandRoot + " open"
        ));

        savedData.getFactories().stream()
                .sorted(Comparator.comparingInt(PocketFactorySavedData.FactoryRecord::id))
            .forEach(factory -> player.sendSystemMessage(factoryLine(factory, allowRecovery, commandRoot)));

        if (savedData.getFactories().isEmpty()) {
            player.sendSystemMessage(Component.translatable("debug.create_pocket_factory.menu.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static MutableComponent actionLine(Component label, String command) {
        return Component.literal("- ")
                .append(clickable(label, command, ChatFormatting.AQUA));
    }

    private static MutableComponent factoryLine(PocketFactorySavedData.FactoryRecord factory, boolean allowRecovery, String commandRoot) {
        MutableComponent line = Component.literal("#" + factory.id() + " ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.translatable(
                        "debug.create_pocket_factory.menu.factory.summary",
                        factory.slotX(),
                        factory.slotZ()
                ).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "));

        if (factory.hasEntrance()) {
            line.append(Component.translatable(
                    "debug.create_pocket_factory.menu.factory.entrance",
                    factory.entranceDimension().toString(),
                    factory.entrancePos().getX(),
                    factory.entrancePos().getY(),
                    factory.entrancePos().getZ()
            ).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            line.append(Component.translatable("debug.create_pocket_factory.menu.factory.no_entrance").withStyle(ChatFormatting.DARK_GRAY));
        }

        line.append(Component.literal(" "));
        line.append(clickable(Component.translatable("debug.create_pocket_factory.menu.action.teleport"), commandRoot + " tp " + factory.id(), ChatFormatting.GREEN));
        if (allowRecovery && !factory.hasEntrance()) {
            line.append(Component.literal(" "));
            line.append(clickable(Component.translatable("debug.create_pocket_factory.menu.action.recover"), commandRoot + " recover " + factory.id(), ChatFormatting.YELLOW));
        }
        return line;
    }

    private static MutableComponent clickable(Component label, String command, ChatFormatting color) {
        return Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(label.copy().withStyle(style -> style.withColor(color).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))))
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }
}