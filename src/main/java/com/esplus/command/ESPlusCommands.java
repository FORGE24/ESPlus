package com.esplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.esplus.Config;
import com.esplus.security.SecurityService;
import com.esplus.security.SecurityService.PasswordResult;
import com.esplus.ui.PasswordPromptBridge;
import com.esplus.ui.PasswordPromptBridge.Purpose;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ESPlusCommands {
    private ESPlusCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            SecurityService security,
            PasswordPromptBridge bridge
    ) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("esplus");

        root.then(Commands.literal("password")
                .then(Commands.literal("set")
                        .executes(ctx -> beginSetPassword(ctx.getSource(), security, bridge)))
                .then(Commands.literal("change")
                        .executes(ctx -> beginChangePassword(ctx.getSource(), security, bridge))));

        root.then(Commands.literal("resetpassword")
                .requires(source -> source.hasPermission(4) || !source.isPlayer())
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> resetPassword(ctx.getSource(), security, EntityArgument.getPlayer(ctx, "player")))));

        dispatcher.register(root);

        // Do not hide behind .requires(2): non-OP would see "unknown command".
        dispatcher.register(Commands.literal("setoppw")
                .executes(ctx -> beginSetPassword(ctx.getSource(), security, bridge)));

        dispatcher.register(Commands.literal("changepw")
                .executes(ctx -> beginChangePassword(ctx.getSource(), security, bridge)));
    }

    private static boolean requireOp(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null ? SecurityService.isMinecraftOperator(player) : source.hasPermission(2)) {
            return true;
        }
        source.sendFailure(Component.translatable("esplus.error.op_required"));
        return false;
    }

    private static int beginSetPassword(CommandSourceStack source, SecurityService security, PasswordPromptBridge bridge) {
        if (!requireOp(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("esplus.error.player_only"));
            return 0;
        }
        if (!security.isReady()) {
            source.sendFailure(Component.translatable("esplus.error.not_ready"));
            return 0;
        }
        bridge.request(
                player,
                Purpose.SET_PASSWORD,
                "ESPlus",
                "设置 sudo 密码 / Set sudo password",
                true,
                (p, password) -> applySetPassword(p, security, password),
                () -> player.sendSystemMessage(Component.translatable("esplus.password.ui_canceled"))
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void applySetPassword(ServerPlayer player, SecurityService security, String password) {
        PasswordResult result = security.setPassword(player, password);
        switch (result) {
            case SET -> player.sendSystemMessage(Component.translatable("esplus.password.set"));
            case ALREADY_SET -> player.sendSystemMessage(Component.translatable("esplus.password.already_set"));
            case TOO_SHORT -> player.sendSystemMessage(Component.translatable(
                    "esplus.password.too_short", Config.MIN_PASSWORD_LENGTH.getAsInt()));
            case NOT_OP -> player.sendSystemMessage(Component.translatable("esplus.error.op_required"));
            case NOT_READY -> player.sendSystemMessage(Component.translatable("esplus.error.not_ready"));
            default -> player.sendSystemMessage(Component.literal("Failed to set password."));
        }
    }

    private static int beginChangePassword(CommandSourceStack source, SecurityService security, PasswordPromptBridge bridge) {
        if (!requireOp(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("esplus.error.player_only"));
            return 0;
        }
        if (!security.isReady()) {
            source.sendFailure(Component.translatable("esplus.error.not_ready"));
            return 0;
        }
        bridge.request(
                player,
                Purpose.CHANGE_OLD,
                "ESPlus",
                "输入旧密码 / Enter current password",
                false,
                (p, oldPassword) -> bridge.request(
                        p,
                        Purpose.CHANGE_NEW,
                        "ESPlus",
                        "输入新密码 / Enter new password",
                        true,
                        (p2, newPassword) -> applyChangePassword(p2, security, oldPassword, newPassword),
                        () -> p.sendSystemMessage(Component.translatable("esplus.password.ui_canceled"))
                ),
                () -> player.sendSystemMessage(Component.translatable("esplus.password.ui_canceled"))
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void applyChangePassword(ServerPlayer player, SecurityService security, String oldPassword, String newPassword) {
        PasswordResult result = security.changePassword(player, oldPassword, newPassword);
        switch (result) {
            case CHANGED -> player.sendSystemMessage(Component.translatable("esplus.password.changed"));
            case NOT_SET -> player.sendSystemMessage(Component.translatable("esplus.password.not_set"));
            case WRONG_OLD -> player.sendSystemMessage(Component.translatable("esplus.password.wrong_old"));
            case LOCKED -> {
                long until = security.lockedUntil(player.getUUID());
                player.sendSystemMessage(Component.translatable("esplus.password.locked", SecurityService.formatInstant(until)));
            }
            case TOO_SHORT -> player.sendSystemMessage(Component.translatable(
                    "esplus.password.too_short", Config.MIN_PASSWORD_LENGTH.getAsInt()));
            case NOT_OP -> player.sendSystemMessage(Component.translatable("esplus.error.op_required"));
            case NOT_READY -> player.sendSystemMessage(Component.translatable("esplus.error.not_ready"));
            default -> player.sendSystemMessage(Component.literal("Failed to change password."));
        }
    }

    private static int resetPassword(CommandSourceStack source, SecurityService security, ServerPlayer target) {
        if (!security.isReady()) {
            source.sendFailure(Component.translatable("esplus.error.not_ready"));
            return 0;
        }
        boolean ok = security.resetPassword(target.getUUID());
        if (ok) {
            source.sendSuccess(() -> Component.translatable("esplus.reset.success", target.getGameProfile().getName()), true);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(Component.translatable("esplus.reset.missing", target.getGameProfile().getName()));
        return 0;
    }
}
