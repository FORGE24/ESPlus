package com.esplus.security.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.esplus.Config;
import com.esplus.security.SecurityService;
import com.esplus.security.risk.RiskDecision;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * Task interception: sudo session + per-player permission.
 * Optional: admin→admin one-shot auto elevate; admin→player requires manual /sudo.
 */
public final class CommandGate {
    private final SecurityService security;

    public CommandGate(SecurityService security) {
        this.security = security;
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!security.isReady()) {
            return;
        }

        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        String root = rootCommand(parse);
        RiskDecision risk = security.evaluateRisk(root);
        if (!risk.requiresSudo()) {
            return;
        }

        // Non-OP must never pass the gate, even with a leaked password / stale session.
        if (!SecurityService.isMinecraftOperator(player)) {
            event.setCanceled(true);
            security.sessions().close(player.getUUID());
            String detail = root + " risk=" + risk.level().name() + " reason=not_op";
            security.audit(player.getUUID(), "protected_command", detail, false);
            security.recordSecurityEvent(player, "protected_blocked", detail);
            source.sendFailure(Component.translatable("esplus.error.op_required"));
            return;
        }

        ServerPlayer target = findOnlinePlayerTarget(parse, player.server);
        boolean hasSession = security.sessions().isActive(player.getUUID());
        boolean autoAdminToAdmin = target != null
                && security.canAutoElevateAdminToAdmin(player.getUUID(), target.getUUID());

        if (!hasSession && !autoAdminToAdmin) {
            event.setCanceled(true);
            String detail = root + " risk=" + risk.level().name() + " reason=no_session";
            security.audit(player.getUUID(), "protected_command", detail, false);
            security.recordSecurityEvent(player, "protected_blocked", detail);
            if (Config.AUTO_SUDO_ADMIN_TO_ADMIN.getAsBoolean()
                    && security.isSemAdmin(player.getUUID())
                    && (target == null || !security.isSemAdmin(target.getUUID()))) {
                source.sendFailure(Component.translatable("esplus.sudo.need_manual_for_player"));
            } else {
                source.sendFailure(Component.translatable("esplus.sudo.blocked_command", root));
            }
            return;
        }

        if (!security.canUseProtectedCommand(player.getUUID(), root)) {
            event.setCanceled(true);
            String detail = root + " risk=" + risk.level().name() + " reason=no_perm";
            security.audit(player.getUUID(), "protected_command", detail, false);
            security.recordSecurityEvent(player, "protected_denied_perm", detail);
            source.sendFailure(Component.translatable("esplus.perm.denied", "cmd." + root));
            return;
        }

        if (hasSession) {
            security.refreshSudoSession(player.getUUID());
        }
        String elevate = autoAdminToAdmin && !hasSession ? " auto=admin_to_admin" : "";
        String detail = root + " risk=" + risk.level().name() + elevate;
        security.audit(player.getUUID(), "protected_command", detail, true);
        security.recordSecurityEvent(player, autoAdminToAdmin && !hasSession
                ? "protected_auto_admin_to_admin"
                : "protected_allowed", detail);
    }

    /**
     * Best-effort: first online player name token in the command that is not the actor.
     */
    private static ServerPlayer findOnlinePlayerTarget(ParseResults<CommandSourceStack> parse, MinecraftServer server) {
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        String[] parts = input.split("\\s+");
        for (int i = 1; i < parts.length; i++) {
            String token = parts[i];
            if (token.startsWith("@")) {
                continue;
            }
            ServerPlayer found = server.getPlayerList().getPlayerByName(token);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String rootCommand(ParseResults<CommandSourceStack> parse) {
        CommandContextBuilder<CommandSourceStack> context = parse.getContext();
        var nodes = context.getNodes();
        List<String> names = new ArrayList<>();
        for (var pair : nodes) {
            String n = pair.getNode().getName();
            if (n != null && !n.isBlank()) {
                names.add(n);
            }
        }

        String root = null;
        if (!names.isEmpty()) {
            root = names.getFirst();
            // Recursively resolve /execute ... run <actual_command>
            if ("execute".equals(root) || "minecraft:execute".equals(root)) {
                // Find the last "run" keyword, then take the following literal as the real command
                int lastRun = -1;
                for (int i = 0; i < names.size(); i++) {
                    String n = names.get(i);
                    if ("run".equals(n) || "minecraft:run".equals(n)) {
                        lastRun = i;
                    }
                }
                if (lastRun >= 0 && lastRun + 1 < names.size()) {
                    root = names.get(lastRun + 1);
                } else {
                    // /execute without run (shouldn't happen for executable commands)
                    root = "execute";
                }
            }
        }

        if (root == null) {
            String input = parse.getReader().getString().trim();
            if (input.startsWith("/")) input = input.substring(1);
            int space = input.indexOf(' ');
            root = space < 0 ? input : input.substring(0, space);
        }
        if (root == null || root.isBlank()) return null;
        // Normalize namespace aliases
        if (root.startsWith("minecraft:")) root = root.substring("minecraft:".length());
        return root.toLowerCase(Locale.ROOT);
    }
}
