package com.esplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.esplus.Config;
import com.esplus.audit.AuditService;
import com.esplus.audit.GlobalEvent;
import com.esplus.audit.ItemTraceNbt;
import com.esplus.security.SecurityService;
import com.esplus.security.SecurityService.AuthResult;
import com.esplus.ui.PasswordPromptBridge;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class SudoCommands {
    private SudoCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext,
            SecurityService security,
            PasswordPromptBridge bridge
    ) {
        var root = Commands.literal("sudo");

        root.then(Commands.literal("status")
                .executes(ctx -> status(ctx.getSource(), security)));

        root.then(Commands.literal("exit")
                .executes(ctx -> exit(ctx.getSource(), security)));

        root.then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(ctx -> give(
                                        ctx.getSource(),
                                        security,
                                        EntityArgument.getPlayer(ctx, "player"),
                                        ItemArgument.getItem(ctx, "item"),
                                        1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64 * 9))
                                        .executes(ctx -> give(
                                                ctx.getSource(),
                                                security,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                ItemArgument.getItem(ctx, "item"),
                                                IntegerArgumentType.getInteger(ctx, "count")))))));

        // No password argument — Qt window only
        root.executes(ctx -> beginAuth(ctx.getSource(), security, bridge));

        dispatcher.register(root);
    }

    private static boolean requireOp(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null ? SecurityService.isMinecraftOperator(player) : source.hasPermission(2)) {
            return true;
        }
        source.sendFailure(Component.translatable("esplus.error.op_required"));
        return false;
    }

    private static int beginAuth(CommandSourceStack source, SecurityService security, PasswordPromptBridge bridge) {
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
        if (!security.hasPassword(player.getUUID())) {
            source.sendFailure(Component.translatable("esplus.sudo.need_password"));
            return 0;
        }
        bridge.request(
                player,
                PasswordPromptBridge.Purpose.SUDO_AUTH,
                "ESPlus sudo",
                "输入 sudo 密码 / Enter sudo password",
                false,
                (p, password) -> applyAuth(p, security, bridge, password),
                () -> player.sendSystemMessage(Component.translatable("esplus.password.ui_canceled"))
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void applyAuth(ServerPlayer player, SecurityService security, PasswordPromptBridge bridge, String password) {
        AuthResult result = security.authenticate(player, password);
        switch (result) {
            case SUCCESS -> player.sendSystemMessage(
                    Component.translatable("esplus.sudo.enabled", Config.SUDO_SESSION_MINUTES.getAsInt()));
            case NEED_TOTP -> bridge.request(
                    player,
                    PasswordPromptBridge.Purpose.SUDO_TOTP,
                    "ESPlus sudo TOTP",
                    "输入 Authenticator 6 位验证码 / Enter TOTP code",
                    false,
                    (p, code) -> applyTotp(p, security, code),
                    () -> player.sendSystemMessage(Component.translatable("esplus.password.ui_canceled"))
            );
            case NO_PASSWORD -> player.sendSystemMessage(Component.translatable("esplus.sudo.need_password"));
            case BAD_PASSWORD -> player.sendSystemMessage(Component.translatable("esplus.sudo.bad_password"));
            case DENIED_PERM -> player.sendSystemMessage(Component.translatable("esplus.perm.denied", "sudo.session"));
            case NOT_OP -> player.sendSystemMessage(Component.translatable("esplus.error.op_required"));
            case LOCKED -> {
                long until = security.lockedUntil(player.getUUID());
                player.sendSystemMessage(Component.translatable("esplus.sudo.locked", SecurityService.formatInstant(until)));
            }
            case NOT_READY -> player.sendSystemMessage(Component.translatable("esplus.error.not_ready"));
            default -> player.sendSystemMessage(Component.literal("Sudo authentication failed."));
        }
    }

    private static void applyTotp(ServerPlayer player, SecurityService security, String code) {
        AuthResult result = security.completeTotp(player, code);
        switch (result) {
            case SUCCESS -> player.sendSystemMessage(
                    Component.translatable("esplus.sudo.enabled", Config.SUDO_SESSION_MINUTES.getAsInt()));
            case BAD_TOTP -> player.sendSystemMessage(Component.literal("[ES+] TOTP 验证码错误"));
            default -> player.sendSystemMessage(Component.literal("Sudo TOTP failed."));
        }
    }

    private static int status(CommandSourceStack source, SecurityService security) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("esplus.error.player_only"));
            return 0;
        }
        long remaining = security.sessions().remainingMillis(player.getUUID());
        if (remaining <= 0L) {
            source.sendFailure(Component.translatable("esplus.sudo.inactive"));
            return 0;
        }
        long seconds = Math.max(1L, remaining / 1000L);
        source.sendSuccess(() -> Component.translatable("esplus.sudo.status", seconds), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int exit(CommandSourceStack source, SecurityService security) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("esplus.error.player_only"));
            return 0;
        }
        security.sessions().close(player.getUUID());
        source.sendSuccess(() -> Component.translatable("esplus.sudo.disabled"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int give(CommandSourceStack source, SecurityService security, ServerPlayer target, ItemInput item, int count) {
        ServerPlayer actor = source.getPlayer();
        if (actor == null) {
            source.sendFailure(Component.translatable("esplus.error.player_only"));
            return 0;
        }
        if (!security.isReady()) {
            source.sendFailure(Component.translatable("esplus.error.not_ready"));
            return 0;
        }
        boolean hasSession = security.sessions().isActive(actor.getUUID());
        boolean autoAdminToAdmin = security.canAutoElevateAdminToAdmin(actor.getUUID(), target.getUUID());
        if (!hasSession && !autoAdminToAdmin) {
            if (Config.AUTO_SUDO_ADMIN_TO_ADMIN.getAsBoolean() && security.isSemAdmin(actor.getUUID())) {
                source.sendFailure(Component.translatable("esplus.sudo.need_manual_for_player"));
            } else {
                source.sendFailure(Component.translatable("esplus.sudo.need_auth"));
            }
            security.audit(actor.getUUID(), "sudo_give", "denied_no_session", false);
            return 0;
        }
        if (!security.canSudoGive(actor.getUUID())) {
            source.sendFailure(Component.translatable("esplus.perm.denied", "sudo.give"));
            security.audit(actor.getUUID(), "sudo_give", "denied_no_perm", false);
            return 0;
        }
        if (hasSession) {
            security.refreshSudoSession(actor.getUUID());
        }

        try {
            ItemStack stack = item.createItemStack(count, false);
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            AuditService auditService = security.auditService();
            String traceId = null;
            if (auditService != null) {
                traceId = auditService.createItemTrace(
                        itemId,
                        "sudo_give",
                        actor.getUUID(),
                        actor.getGameProfile().getName(),
                        "sudo give to " + target.getGameProfile().getName()
                );
                ItemTraceNbt.stamp(stack, traceId, "sudo_give");
            }

            boolean added = target.getInventory().add(stack);
            if (!added && !stack.isEmpty()) {
                target.drop(stack, false);
            }
            security.audit(actor.getUUID(), "sudo_give", itemId + " x" + count + " -> " + target.getGameProfile().getName(), true);
            if (auditService != null) {
                GlobalEvent ge = new GlobalEvent(
                        java.util.UUID.randomUUID().toString(),
                        System.currentTimeMillis(),
                        "item",
                        "sudo_give",
                        actor.getUUID().toString(),
                        actor.getGameProfile().getName(),
                        target.getUUID().toString(),
                        target.getGameProfile().getName(),
                        actor.level().dimension().location().toString(),
                        actor.getX(), actor.getY(), actor.getZ(),
                        itemId,
                        traceId,
                        itemId + " x" + count,
                        "player"
                );
                auditService.recordAsync(ge);
                if (traceId != null) {
                    auditService.linkItem(traceId, null, ge.eventId(), "sudo_give", actor.getUUID(), actor.getGameProfile().getName(), ge.detail());
                }
            }
            String finalItemId = itemId;
            source.sendSuccess(
                    () -> Component.translatable("esplus.give.success", finalItemId, count, target.getGameProfile().getName()),
                    true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            security.audit(actor.getUUID(), "sudo_give", "error", false);
            source.sendFailure(Component.literal("Failed to give item: " + ex.getMessage()));
            return 0;
        }
    }
}
