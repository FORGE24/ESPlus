package com.esplus.security.perm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.esplus.Config;

/**
 * Catalog of SEM fine-grained permissions.
 * OP / sudo session alone is not enough — each action needs an explicit grant.
 */
public final class SemPermissions {
    public static final String SUDO_GIVE = "sudo.give";
    public static final String SUDO_SESSION = "sudo.session";

    public static final List<String> ROLE_ORDER = List.of(
            "owner", "admin", "moderator", "builder", "helper", "op", "viewer"
    );

    private SemPermissions() {
    }

    public static String commandPerm(String rootCommand) {
        return "cmd." + rootCommand.toLowerCase(Locale.ROOT);
    }

    public static List<String> allKnown() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(SUDO_SESSION);
        set.add(SUDO_GIVE);
        for (String cmd : Config.PROTECTED_COMMANDS.get()) {
            if (cmd != null && !cmd.isBlank()) {
                set.add(commandPerm(cmd.trim()));
            }
        }
        return new ArrayList<>(set);
    }

    public static boolean isKnownRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String r = role.trim().toLowerCase(Locale.ROOT);
        return ROLE_ORDER.contains(r);
    }

    /** Role templates — plain Minecraft OP only gets sudo.session; cmd.* via panel role upgrade. */
    public static Set<String> defaultsForRole(String role) {
        String r = role == null ? "op" : role.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        List<String> all = allKnown();
        return switch (r) {
            case "owner", "admin" -> {
                set.addAll(all);
                yield set;
            }
            case "moderator" -> {
                set.add(SUDO_SESSION);
                addIfKnown(set, all, "cmd.kick", "cmd.ban", "cmd.ban-ip", "cmd.pardon", "cmd.pardon-ip",
                        "cmd.tp", "cmd.teleport", "cmd.gamemode", "cmd.clear", "cmd.effect");
                yield set;
            }
            case "builder" -> {
                set.add(SUDO_SESSION);
                addIfKnown(set, all, "cmd.gamemode", "cmd.tp", "cmd.teleport", "cmd.give", "cmd.setblock",
                        "cmd.fill", "cmd.clone", "cmd.summon", "cmd.time", "cmd.weather", "cmd.gamerule");
                yield set;
            }
            case "helper" -> {
                set.add(SUDO_SESSION);
                addIfKnown(set, all, "cmd.kick", "cmd.tp", "cmd.teleport", "cmd.gamemode");
                yield set;
            }
            case "viewer" -> set; // no sudo even
            default -> {
                set.add(SUDO_SESSION); // op
                yield set;
            }
        };
    }

    private static void addIfKnown(Set<String> target, List<String> all, String... perms) {
        for (String p : perms) {
            if (all.contains(p)) {
                target.add(p);
            } else {
                target.add(p); // still grant node so future protected list picks it up
            }
        }
    }
}
