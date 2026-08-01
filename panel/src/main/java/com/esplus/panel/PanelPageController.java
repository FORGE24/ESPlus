package com.esplus.panel;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PanelPageController {
    private final PanelQueryService queries;
    private final PanelGovernanceService governance;
    private final PanelMfaService mfa;

    @Value("${server.port:8088}")
    private int panelPort;

    @Value("${server.address:127.0.0.1}")
    private String panelBind;

    @Value("${esplus.sshHint:}")
    private String sshHint;

    @Value("${esplus.securityReady:false}")
    private boolean securityReady;

    public PanelPageController(PanelQueryService queries, PanelGovernanceService governance, PanelMfaService mfa) {
        this.queries = queries;
        this.governance = governance;
        this.mfa = mfa;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("error", error);
        model.addAttribute("locked", "locked".equals(error));
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAllAttributes(queries.dashboard());
        return "dashboard";
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String traceId,
            Model model
    ) {
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("actor", actor);
        model.addAttribute("traceId", traceId);
        model.addAttribute("results", queries.search(q, category, actor, traceId, 200));
        return "search";
    }

    @GetMapping("/trace/{traceId}")
    public String trace(@PathVariable String traceId, Model model) {
        model.addAttribute("traceId", traceId);
        model.addAllAttributes(queries.itemTrace(traceId));
        model.addAllAttributes(governance.itemGraph(traceId));
        return "trace";
    }

    @GetMapping("/incident/{eventId}")
    public String incident(@PathVariable String eventId, Model model) {
        model.addAttribute("eventId", eventId);
        model.addAllAttributes(queries.incident(eventId, 15 * 60_000L));
        return "incident";
    }

    @GetMapping("/alerts")
    public String alerts(@RequestParam(defaultValue = "true") boolean open, Model model) {
        model.addAttribute("openOnly", open);
        model.addAttribute("alerts", queries.alerts(open));
        return "alerts";
    }

    @GetMapping("/audit")
    public String audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("action", action);
        model.addAttribute("uuid", uuid);
        model.addAttribute("success", success);
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        model.addAttribute("logs", queries.auditLogs(action, uuid, success, 200));
        return "audit";
    }

    @GetMapping("/admins")
    public String admins(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAllAttributes(queries.adminsPage());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "admins";
    }

    @GetMapping("/console")
    public String console(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String msg,
            Model model
    ) {
        model.addAllAttributes(queries.consolePage(level, q));
        model.addAttribute("msg", msg);
        return "console";
    }

    @PostMapping("/console/cmd")
    public String consoleCmd(@RequestParam String command) {
        boolean ok = queries.enqueueConsoleCommand(command);
        return ok ? "redirect:/console?msg=queued" : "redirect:/console?msg=failed";
    }

    @PostMapping("/admins/{uuid}/unlock")
    public String unlockAdmin(@PathVariable String uuid) {
        boolean ok = queries.unlockUser(uuid);
        return ok ? "redirect:/admins?msg=unlocked" : "redirect:/admins?err=unlock_failed";
    }

    @PostMapping("/admins/{uuid}/reset")
    public String resetAdminPassword(@PathVariable String uuid) {
        boolean ok = queries.resetUserPassword(uuid);
        return ok ? "redirect:/admins?msg=reset" : "redirect:/admins?err=reset_failed";
    }

    @PostMapping("/admins/{uuid}/role")
    public String updateAdminRole(@PathVariable String uuid, @RequestParam String role) {
        boolean ok = queries.updateUserRole(uuid, role);
        return ok ? "redirect:/admins?msg=role" : "redirect:/admins?err=role_failed";
    }

    @PostMapping("/admins/{uuid}/op-bound")
    public String updateOpBound(@PathVariable String uuid, @RequestParam boolean opBound) {
        boolean ok = queries.updateOpBound(uuid, opBound);
        return ok ? "redirect:/admins?msg=op_bound" : "redirect:/admins?err=op_bound_failed";
    }

    @PostMapping("/admins/op/grant")
    public String grantOp(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueueGrantOp(player, uuid);
        return ok ? "redirect:/admins?msg=op_queued" : "redirect:/admins?err=op_queue_failed";
    }

    @PostMapping("/admins/op/revoke")
    public String revokeOp(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueueRevokeOp(player, uuid);
        return ok ? "redirect:/admins?msg=deop_queued" : "redirect:/admins?err=op_queue_failed";
    }

    @GetMapping("/players")
    public String players(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAllAttributes(queries.playersPage());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "players";
    }

    @PostMapping("/players/kick")
    public String kickPlayer(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String reason
    ) {
        boolean ok = queries.enqueueKick(player, uuid, reason);
        return ok ? "redirect:/players?msg=kick_queued" : "redirect:/players?err=kick_failed";
    }

    @PostMapping("/players/ban")
    public String banPlayer(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String reason
    ) {
        boolean ok = queries.enqueueBan(player, uuid, reason);
        return ok ? "redirect:/players?msg=ban_queued" : "redirect:/players?err=ban_failed";
    }

    @PostMapping("/players/temp-ban")
    public String tempBanPlayer(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(required = false) String reason
    ) {
        boolean ok = queries.enqueueTempBan(player, uuid, minutes, reason);
        return ok ? "redirect:/players?msg=temp_ban_queued" : "redirect:/players?err=ban_failed";
    }

    @PostMapping("/players/unban")
    public String unbanPlayer(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueueUnban(player, uuid);
        return ok ? "redirect:/players?msg=unban_queued" : "redirect:/players?err=unban_failed";
    }

    @GetMapping("/bans")
    public String bans(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAllAttributes(queries.bansPage());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "bans";
    }

    @PostMapping("/bans/unban")
    public String bansUnban(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueueUnban(player, uuid);
        return ok ? "redirect:/bans?msg=unban_queued" : "redirect:/bans?err=failed";
    }

    @PostMapping("/bans/ip/ban")
    public String banIp(@RequestParam String ip, @RequestParam(required = false) String reason) {
        boolean ok = queries.enqueuePayload("ban_ip", reason, null, ip);
        return ok ? "redirect:/bans?msg=ip_ban_queued" : "redirect:/bans?err=failed";
    }

    @PostMapping("/bans/ip/pardon")
    public String pardonIp(@RequestParam String ip) {
        boolean ok = queries.enqueuePayload("pardon_ip", null, null, ip);
        return ok ? "redirect:/bans?msg=ip_pardon_queued" : "redirect:/bans?err=failed";
    }

    @GetMapping("/whitelist")
    public String whitelist(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAllAttributes(queries.whitelistPage());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "whitelist";
    }

    @PostMapping("/whitelist/add")
    public String whitelistAdd(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueueWhitelistAdd(player, uuid);
        return ok ? "redirect:/whitelist?msg=add_queued" : "redirect:/whitelist?err=failed";
    }

    @PostMapping("/whitelist/remove")
    public String whitelistRemove(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueueWhitelistRemove(player, uuid);
        return ok ? "redirect:/whitelist?msg=remove_queued" : "redirect:/whitelist?err=failed";
    }

    @PostMapping("/whitelist/toggle")
    public String whitelistToggle(@RequestParam boolean enabled) {
        boolean ok = queries.enqueueWhitelistSet(enabled);
        return ok ? "redirect:/whitelist?msg=toggle_queued" : "redirect:/whitelist?err=failed";
    }

    @GetMapping("/messages")
    public String messages(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "messages";
    }

    @PostMapping("/messages/broadcast")
    public String broadcast(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "[公告]") String prefix,
            @RequestParam(defaultValue = "1") int times
    ) {
        boolean ok = queries.enqueueBroadcast(message, prefix, times);
        return ok ? "redirect:/messages?msg=broadcast_queued" : "redirect:/messages?err=failed";
    }

    @GetMapping("/messages/schedule")
    public String messageSchedule(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("schedules", queries.listSchedules(100));
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "messages-schedule";
    }

    @PostMapping("/messages/schedule")
    public String createSchedule(
            @RequestParam String message,
            @RequestParam(defaultValue = "[公告]") String prefix,
            @RequestParam(defaultValue = "1") int times,
            @RequestParam(defaultValue = "0") int delaySeconds,
            @RequestParam(defaultValue = "0") int intervalSeconds,
            @RequestParam(required = false) String note
    ) {
        boolean ok = queries.createBroadcastSchedule(message, prefix, times, delaySeconds, intervalSeconds, note);
        return ok ? "redirect:/messages/schedule?msg=created" : "redirect:/messages/schedule?err=failed";
    }

    @PostMapping("/messages/schedule/{id}/toggle")
    public String toggleSchedule(@PathVariable long id, @RequestParam boolean enabled) {
        boolean ok = queries.setScheduleEnabled(id, enabled);
        return ok ? "redirect:/messages/schedule?msg=toggled" : "redirect:/messages/schedule?err=failed";
    }

    @PostMapping("/messages/schedule/{id}/delete")
    public String deleteSchedule(@PathVariable long id) {
        boolean ok = queries.deleteSchedule(id);
        return ok ? "redirect:/messages/schedule?msg=deleted" : "redirect:/messages/schedule?err=failed";
    }

    @PostMapping("/messages/tell")
    public String tell(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String message
    ) {
        boolean ok = queries.enqueueTell(player, uuid, message);
        return ok ? "redirect:/messages?msg=tell_queued" : "redirect:/messages?err=failed";
    }

    @GetMapping("/remote")
    public String remote(Model model) {
        model.addAttribute("sshHint", sshHint);
        return "remote";
    }

    @GetMapping("/audit/export")
    public org.springframework.http.ResponseEntity<String> auditExport(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) Boolean success
    ) {
        String csv = queries.exportAuditCsv(action, uuid, success, 5000);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=audit-export.csv")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv);
    }

    @PostMapping("/audit/cleanup")
    public String auditCleanup(@RequestParam(required = false) Integer days) {
        boolean ok = queries.enqueueRetentionCleanup(days);
        return ok ? "redirect:/audit?msg=cleanup_queued" : "redirect:/audit?err=cleanup_failed";
    }

    @GetMapping("/admins/{uuid}/perms")
    public String adminPerms(@PathVariable String uuid, Model model) {
        Map<String, Object> page = queries.userPermissionsPage(uuid);
        model.addAllAttributes(page);
        model.addAttribute("uuid", uuid);
        if (!Boolean.TRUE.equals(page.get("found"))) {
            return "redirect:/admins?err=not_found";
        }
        return "admin-perms";
    }

    @PostMapping("/admins/{uuid}/perms")
    public String saveAdminPerms(
            @PathVariable String uuid,
            @RequestParam(value = "perm", required = false) List<String> perms
    ) {
        boolean ok = queries.saveUserPermissions(uuid, perms);
        return ok ? "redirect:/admins/" + uuid + "/perms?saved=1" : "redirect:/admins?err=perm_failed";
    }

    @PostMapping("/admins/{uuid}/perms/reset-role")
    public String resetPermsToRole(@PathVariable String uuid, @RequestParam String role) {
        boolean ok = queries.applyRolePermissionDefaults(uuid, role);
        return ok ? "redirect:/admins/" + uuid + "/perms?saved=1" : "redirect:/admins?err=perm_failed";
    }

    @PostMapping("/alerts/{alertId}/ack")
    public String ack(@PathVariable String alertId) {
        queries.acknowledge(alertId);
        return "redirect:/alerts";
    }

    @GetMapping("/movements")
    public String movements(
            @RequestParam String player,
            @RequestParam(defaultValue = "0") long from,
            @RequestParam(defaultValue = "0") long to,
            Model model
    ) {
        model.addAttribute("player", player);
        model.addAttribute("movements", queries.movements(player, from, to));
        return "movements";
    }

    @GetMapping("/status")
    public String status(Model model) {
        long alerts = ((Number) queries.dashboard().getOrDefault("alertsOpen", 0L)).longValue();
        model.addAllAttributes(queries.statusOverview(alerts));
        model.addAttribute("panelBind", panelBind);
        model.addAttribute("panelPort", panelPort);
        model.addAttribute("tpsSamples", queries.perfSamplesAsc(60));
        return "status";
    }

    @GetMapping("/status/performance")
    public String performance(Model model) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("dimensions", queries.dimensions());
        model.addAttribute("samples", queries.perfSamplesAsc(120));
        model.addAttribute("recentSamples", queries.perfSamples(30));
        return "status-performance";
    }

    @GetMapping("/status/connection")
    public String connection(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("panelBind", panelBind);
        model.addAttribute("panelPort", panelPort);
        model.addAttribute("sshHint", sshHint);
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "status-connection";
    }

    @GetMapping("/status/versions")
    public String versions(Model model) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("audit24h", queries.dashboard().get("audit24h"));
        model.addAttribute("usersCount", queries.dashboard().get("usersCount"));
        return "status-versions";
    }

    @GetMapping("/gamerules")
    public String gamerules(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("rules", queries.gamerules());
        model.addAttribute("categories", List.of("PLAYER", "MOBS", "SPAWNING", "DROPS", "UPDATES", "CHAT", "MISC"));
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "gamerules";
    }

    @PostMapping("/gamerules/set")
    public String setGamerule(@RequestParam String ruleId, @RequestParam String value) {
        boolean ok = queries.enqueueSetGamerule(ruleId, value);
        return ok ? "redirect:/gamerules?msg=queued" : "redirect:/gamerules?err=failed";
    }

    @GetMapping("/world/time")
    public String worldTime(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "world-time";
    }

    @PostMapping("/world/time")
    public String setTime(@RequestParam String value) {
        boolean ok = queries.enqueuePayload("set_time", null, null, value);
        return ok ? "redirect:/world/time?msg=queued" : "redirect:/world/time?err=failed";
    }

    @PostMapping("/world/weather")
    public String setWeather(@RequestParam String value) {
        boolean ok = queries.enqueuePayload("set_weather", null, null, value);
        return ok ? "redirect:/world/time?msg=queued" : "redirect:/world/time?err=failed";
    }

    @PostMapping("/world/cycle-lock")
    public String cycleLock(@RequestParam String ruleId, @RequestParam String value) {
        if (!("doWeatherCycle".equals(ruleId) || "doDaylightCycle".equals(ruleId))) {
            return "redirect:/world/time?err=failed";
        }
        boolean ok = queries.enqueueSetGamerule(ruleId, value);
        return ok ? "redirect:/world/time?msg=queued" : "redirect:/world/time?err=failed";
    }

    @GetMapping("/world/difficulty")
    public String worldDifficulty(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "world-difficulty";
    }

    @PostMapping("/world/difficulty")
    public String setDifficulty(@RequestParam String value) {
        boolean ok = queries.enqueuePayload("set_difficulty", null, null, value);
        return ok ? "redirect:/world/difficulty?msg=queued" : "redirect:/world/difficulty?err=failed";
    }

    @PostMapping("/world/default-gamemode")
    public String setDefaultGamemode(@RequestParam String value) {
        boolean ok = queries.enqueuePayload("set_default_gamemode", null, null, value);
        return ok ? "redirect:/world/difficulty?msg=queued" : "redirect:/world/difficulty?err=failed";
    }

    @GetMapping("/world/border")
    public String worldBorder(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "world-border";
    }

    @PostMapping("/world/border")
    public String setBorder(
            @RequestParam double size,
            @RequestParam(required = false) Double centerX,
            @RequestParam(required = false) Double centerZ,
            @RequestParam(required = false) Integer warning,
            @RequestParam(required = false) Double damage
    ) {
        StringBuilder sb = new StringBuilder(String.valueOf(size));
        if (centerX != null && centerZ != null) {
            sb.append('|').append(centerX).append('|').append(centerZ);
            if (warning != null) {
                sb.append('|').append(warning);
                if (damage != null) {
                    sb.append('|').append(damage);
                }
            }
        }
        boolean ok = queries.enqueuePayload("set_worldborder", null, null, sb.toString());
        return ok ? "redirect:/world/border?msg=queued" : "redirect:/world/border?err=failed";
    }

    @GetMapping("/world/spawn")
    public String worldSpawn(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "world-spawn";
    }

    @PostMapping("/world/spawn")
    public String setWorldSpawn(
            @RequestParam double x,
            @RequestParam double y,
            @RequestParam double z,
            @RequestParam(defaultValue = "0") float angle
    ) {
        boolean ok = queries.enqueuePayload("set_worldspawn", null, null, x + "|" + y + "|" + z + "|" + angle);
        return ok ? "redirect:/world/spawn?msg=queued" : "redirect:/world/spawn?err=failed";
    }

    @PostMapping("/world/spawn/player")
    public String forcePlayerSpawn(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(defaultValue = "world") String mode
    ) {
        boolean ok = queries.enqueuePayload("set_spawnpoint", player, uuid, mode);
        return ok ? "redirect:/world/spawn?msg=player_queued" : "redirect:/world/spawn?err=failed";
    }

    @GetMapping("/world/dimensions")
    public String worldDimensions(Model model) {
        model.addAttribute("dimensions", queries.dimensions());
        return "world-dimensions";
    }

    @PostMapping("/players/gamemode")
    public String playerGamemode(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String mode
    ) {
        boolean ok = queries.enqueuePayload("set_player_gamemode", player, uuid, mode);
        return ok ? "redirect:/players?msg=gm_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/clear")
    public String playerClear(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueuePayload("clear_inventory", player, uuid, null);
        return ok ? "redirect:/players?msg=clear_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/heal")
    public String playerHeal(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueuePayload("heal_player", player, uuid, null);
        return ok ? "redirect:/players?msg=heal_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/feed")
    public String playerFeed(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueuePayload("feed_player", player, uuid, null);
        return ok ? "redirect:/players?msg=feed_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/extinguish")
    public String playerExtinguish(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueuePayload("extinguish_player", player, uuid, null);
        return ok ? "redirect:/players?msg=extinguish_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/teleport")
    public String playerTeleport(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String mode,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) Double x,
            @RequestParam(required = false) Double y,
            @RequestParam(required = false) Double z,
            @RequestParam(required = false) String dimension
    ) {
        String payload;
        if ("spawn".equals(mode)) {
            payload = "spawn";
        } else if ("player".equals(mode)) {
            payload = "player|" + (target == null ? "" : target);
        } else if ("coords".equals(mode) && x != null && y != null && z != null) {
            payload = "coords|" + x + "|" + y + "|" + z + "|" + (dimension == null ? "" : dimension);
        } else {
            return "redirect:/players?err=failed";
        }
        boolean ok = queries.enqueuePayload("teleport_player", player, uuid, payload);
        return ok ? "redirect:/players?msg=tp_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/clear-ender")
    public String playerClearEnder(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueuePayload("clear_enderchest", player, uuid, null);
        return ok ? "redirect:/players?msg=ender_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/spawnpoint")
    public String playerSpawnpoint(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(defaultValue = "here") String mode,
            @RequestParam(required = false) Double x,
            @RequestParam(required = false) Double y,
            @RequestParam(required = false) Double z
    ) {
        String payload;
        if ("world".equals(mode)) {
            payload = "world";
        } else if ("coords".equals(mode) && x != null && y != null && z != null) {
            payload = "coords|" + x + "|" + y + "|" + z;
        } else {
            payload = "here";
        }
        boolean ok = queries.enqueuePayload("set_spawnpoint", player, uuid, payload);
        return ok ? "redirect:/players?msg=spawnpoint_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/clear-effects")
    public String playerClearEffects(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueuePayload("clear_effects", player, uuid, null);
        return ok ? "redirect:/players?msg=effects_queued" : "redirect:/players?err=failed";
    }

    @PostMapping("/players/effect")
    public String playerEffect(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String effect,
            @RequestParam(defaultValue = "30") int seconds,
            @RequestParam(defaultValue = "0") int amplifier
    ) {
        boolean ok = queries.enqueuePayload("give_effect", player, uuid, effect + "|" + seconds + "|" + amplifier);
        return ok ? "redirect:/players?msg=effect_queued" : "redirect:/players?err=failed";
    }

    @GetMapping("/messages/mute")
    public String mutes(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("mutes", queries.listMutes());
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "messages-mute";
    }

    @PostMapping("/messages/mute")
    public String mutePlayer(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(required = false) String reason
    ) {
        boolean ok = queries.mutePlayer(player, uuid, minutes, reason);
        return ok ? "redirect:/messages/mute?msg=muted" : "redirect:/messages/mute?err=failed";
    }

    @PostMapping("/messages/mute/unmute")
    public String unmute(@RequestParam String key) {
        boolean ok = queries.unmutePlayer(key);
        return ok ? "redirect:/messages/mute?msg=unmuted" : "redirect:/messages/mute?err=failed";
    }

    @GetMapping("/players/profile")
    public String playerProfile(@RequestParam(required = false) String q, Model model) {
        model.addAllAttributes(queries.playerProfile(q));
        return "player-profile";
    }

    @GetMapping("/players/actions")
    public String playerActions(Model model) {
        model.addAttribute("actions", queries.recentPanelActions(50,
                "kick_player", "ban_player", "temp_ban_player", "unban_player", "broadcast",
                "tell_player", "set_player_gamemode", "clear_inventory", "heal_player", "feed_player"));
        return "player-actions";
    }

    @GetMapping("/messages/title")
    public String messageTitle(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "messages-title";
    }

    @PostMapping("/messages/title")
    public String postTitle(
            @RequestParam String kind,
            @RequestParam String text,
            @RequestParam(required = false) String subtitle
    ) {
        String payload = "subtitle".equals(kind)
                ? kind + "|" + text + "|" + (subtitle == null ? "" : subtitle)
                : kind + "|" + text;
        boolean ok = queries.enqueuePayload("title_broadcast", null, null, payload);
        return ok ? "redirect:/messages/title?msg=queued" : "redirect:/messages/title?err=failed";
    }

    @GetMapping("/messages/bossbar")
    public String bossbar(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("bars", queries.bossbars());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "messages-bossbar";
    }

    @PostMapping("/messages/bossbar/create")
    public String bossbarCreate(
            @RequestParam String id,
            @RequestParam String name,
            @RequestParam(defaultValue = "white") String color,
            @RequestParam(defaultValue = "100") int max
    ) {
        boolean ok = queries.enqueuePayload("bossbar_create", null, null, id + "|" + name + "|" + color + "|" + max);
        return ok ? "redirect:/messages/bossbar?msg=queued" : "redirect:/messages/bossbar?err=failed";
    }

    @PostMapping("/messages/bossbar/update")
    public String bossbarUpdate(
            @RequestParam String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) Integer value,
            @RequestParam(required = false) Integer max,
            @RequestParam(required = false) String visible
    ) {
        String payload = id + "|"
                + (name == null ? "" : name) + "|"
                + (color == null ? "" : color) + "|"
                + (value == null ? "" : value) + "|"
                + (max == null ? "" : max) + "|"
                + (visible == null ? "" : visible);
        boolean ok = queries.enqueuePayload("bossbar_update", null, null, payload);
        return ok ? "redirect:/messages/bossbar?msg=queued" : "redirect:/messages/bossbar?err=failed";
    }

    @PostMapping("/messages/bossbar/remove")
    public String bossbarRemove(@RequestParam String id) {
        boolean ok = queries.enqueuePayload("bossbar_remove", null, null, id);
        return ok ? "redirect:/messages/bossbar?msg=queued" : "redirect:/messages/bossbar?err=failed";
    }

    @GetMapping("/messages/filter")
    public String chatFilter(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("words", queries.chatFilterWords());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "messages-filter";
    }

    @PostMapping("/messages/filter/add")
    public String chatFilterAdd(@RequestParam String word) {
        boolean ok = queries.addChatFilterWord(word);
        return ok ? "redirect:/messages/filter?msg=added" : "redirect:/messages/filter?err=failed";
    }

    @PostMapping("/messages/filter/delete")
    public String chatFilterDelete(@RequestParam long id) {
        boolean ok = queries.deleteChatFilterWord(id);
        return ok ? "redirect:/messages/filter?msg=deleted" : "redirect:/messages/filter?err=failed";
    }

    @PostMapping("/messages/filter/toggle")
    public String chatFilterToggle(@RequestParam long id, @RequestParam boolean enabled) {
        boolean ok = queries.toggleChatFilterWord(id, enabled);
        return ok ? "redirect:/messages/filter?msg=toggled" : "redirect:/messages/filter?err=failed";
    }

    @GetMapping("/entities")
    public String entities(Model model) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("dimensions", queries.dimensions());
        model.addAttribute("entityTypes", queries.entityTypes());
        return "entities";
    }

    @GetMapping("/entities/cleanup")
    public String entityCleanup(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        model.addAttribute("entityTypes", queries.entityTypes());
        return "entities-cleanup";
    }

    @PostMapping("/entities/cleanup")
    public String postEntityCleanup(@RequestParam String kind) {
        boolean ok = queries.enqueuePayload("kill_entities", null, null, kind);
        return ok ? "redirect:/entities/cleanup?msg=queued" : "redirect:/entities/cleanup?err=failed";
    }

    @PostMapping("/entities/kill-type")
    public String killEntityType(@RequestParam String entityType) {
        boolean ok = queries.enqueuePayload("kill_entities", null, null, "type:" + entityType.trim());
        return ok ? "redirect:/entities/cleanup?msg=queued" : "redirect:/entities/cleanup?err=failed";
    }

    @GetMapping("/items/give")
    public String itemsGive(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "items-give";
    }

    @PostMapping("/items/give")
    public String postGive(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String item,
            @RequestParam(defaultValue = "1") int count,
            @RequestParam(required = false) String reason
    ) {
        String payload = item.trim() + "|" + count;
        boolean needs = governance.needsApproval("give_item", payload);
        boolean ok = queries.enqueuePayload("give_item", player, uuid, payload, "panel", reason);
        if (!ok) {
            return "redirect:/items/give?err=failed";
        }
        return needs ? "redirect:/items/give?msg=approval" : "redirect:/items/give?msg=queued";
    }

    @GetMapping("/items/clear")
    public String itemsClear(Model model) {
        model.addAttribute("online", queries.onlinePlayers());
        return "items-clear";
    }

    @GetMapping("/items/inventory")
    public String itemsInventory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String section,
            Model model
    ) {
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("q", q);
        model.addAttribute("section", section);
        model.addAttribute("slots", queries.playerInventory(q, section));
        return "items-inventory";
    }

    @PostMapping("/items/clear")
    public String postItemsClear(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        boolean ok = queries.enqueuePayload("clear_inventory", player, uuid, null);
        return ok ? "redirect:/items/clear?msg=queued" : "redirect:/items/clear?err=failed";
    }

    @GetMapping("/security/sudo")
    public String sudoPolicy(
            Model model,
            @Value("${esplus.sudoSessionMinutes:5}") int sudoMinutes,
            @Value("${esplus.maxFailedAttempts:5}") int maxFailed,
            @Value("${esplus.lockMinutes:15}") int lockMinutes,
            @Value("${esplus.protectedCommands:}") String protectedCommands,
            @Value("${esplus.auditRetentionDays:30}") int retentionDays
    ) {
        Map<String, Object> runtime = queries.runtimeSnapshot();
        model.addAttribute("runtime", runtime);
        model.addAttribute("sudoMinutes", runtime.getOrDefault("sudo_session_minutes", sudoMinutes));
        model.addAttribute("maxFailed", runtime.getOrDefault("max_failed_attempts", maxFailed));
        model.addAttribute("lockMinutes", runtime.getOrDefault("lock_minutes", lockMinutes));
        model.addAttribute("protectedCommands", runtime.getOrDefault("protected_commands", protectedCommands));
        model.addAttribute("retentionDays", runtime.getOrDefault("audit_retention_days", retentionDays));
        return "security-sudo";
    }

    @GetMapping("/security/accounts")
    public String panelAccounts(Model model) {
        return "panel-accounts";
    }

    @GetMapping("/access/ops")
    public String opsList(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("users", queries.usersSummary());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "access-ops";
    }

    @PostMapping("/access/ops/deop")
    public String deop(@RequestParam(required = false) String player, @RequestParam(required = false) String uuid) {
        boolean ok = queries.enqueueRevokeOp(player, uuid);
        return ok ? "redirect:/access/ops?msg=deop_queued" : "redirect:/access/ops?err=failed";
    }

    @GetMapping("/access/spectator")
    public String spectator(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("rules", queries.gamerules().stream()
                .filter(r -> "spectatorsGenerateChunks".equals(String.valueOf(r.get("rule_id"))))
                .toList());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "access-spectator";
    }

    @GetMapping("/scoreboard")
    public String scoreboard(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("objectives", queries.scoreboardObjectives());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "scoreboard";
    }

    @PostMapping("/scoreboard/add")
    public String scoreboardAdd(
            @RequestParam String name,
            @RequestParam(defaultValue = "dummy") String criteria,
            @RequestParam(required = false) String displayName
    ) {
        String payload = name + "|" + criteria + "|" + (displayName == null ? name : displayName);
        boolean ok = queries.enqueuePayload("scoreboard_add", null, null, payload);
        return ok ? "redirect:/scoreboard?msg=queued" : "redirect:/scoreboard?err=failed";
    }

    @PostMapping("/scoreboard/remove")
    public String scoreboardRemove(@RequestParam String name) {
        boolean ok = queries.enqueuePayload("scoreboard_remove", null, null, name);
        return ok ? "redirect:/scoreboard?msg=queued" : "redirect:/scoreboard?err=failed";
    }

    @PostMapping("/scoreboard/display")
    public String scoreboardDisplay(
            @RequestParam String slot,
            @RequestParam(required = false) String objective
    ) {
        boolean ok = queries.enqueuePayload("scoreboard_display", null, null, slot + "|" + (objective == null ? "" : objective));
        return ok ? "redirect:/scoreboard?msg=queued" : "redirect:/scoreboard?err=failed";
    }

    @PostMapping("/scoreboard/set")
    public String scoreboardSet(
            @RequestParam String player,
            @RequestParam String objective,
            @RequestParam int score
    ) {
        boolean ok = queries.enqueuePayload("scoreboard_set", null, null, player + "|" + objective + "|" + score);
        return ok ? "redirect:/scoreboard?msg=queued" : "redirect:/scoreboard?err=failed";
    }

    @GetMapping("/scoreboard/teams")
    public String teams(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("teams", queries.teams());
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "scoreboard-teams";
    }

    @PostMapping("/scoreboard/teams/create")
    public String teamCreate(
            @RequestParam String name,
            @RequestParam(required = false) String displayName,
            @RequestParam(defaultValue = "white") String color,
            @RequestParam(defaultValue = "true") String friendlyFire
    ) {
        String payload = name + "|" + (displayName == null ? name : displayName) + "|" + color + "|" + friendlyFire;
        boolean ok = queries.enqueuePayload("team_create", null, null, payload);
        return ok ? "redirect:/scoreboard/teams?msg=queued" : "redirect:/scoreboard/teams?err=failed";
    }

    @PostMapping("/scoreboard/teams/remove")
    public String teamRemove(@RequestParam String name) {
        boolean ok = queries.enqueuePayload("team_remove", null, null, name);
        return ok ? "redirect:/scoreboard/teams?msg=queued" : "redirect:/scoreboard/teams?err=failed";
    }

    @PostMapping("/scoreboard/teams/join")
    public String teamJoin(@RequestParam String team, @RequestParam String player) {
        boolean ok = queries.enqueuePayload("team_join", null, null, team + "|" + player);
        return ok ? "redirect:/scoreboard/teams?msg=queued" : "redirect:/scoreboard/teams?err=failed";
    }

    @PostMapping("/scoreboard/teams/leave")
    public String teamLeave(@RequestParam String player) {
        boolean ok = queries.enqueuePayload("team_leave", null, null, player);
        return ok ? "redirect:/scoreboard/teams?msg=queued" : "redirect:/scoreboard/teams?err=failed";
    }

    @PostMapping("/scoreboard/teams/modify")
    public String teamModify(
            @RequestParam String team,
            @RequestParam String option,
            @RequestParam String value
    ) {
        boolean ok = queries.enqueuePayload("team_modify", null, null, team + "|" + option + "|" + value);
        return ok ? "redirect:/scoreboard/teams?msg=queued" : "redirect:/scoreboard/teams?err=failed";
    }

    @GetMapping("/system/save")
    public String systemSave(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "system-save";
    }

    @PostMapping("/system/save")
    public String postSave() {
        boolean ok = queries.enqueuePayload("save_all", null, null, null);
        return ok ? "redirect:/system/save?msg=queued" : "redirect:/system/save?err=failed";
    }

    @PostMapping("/system/save-on")
    public String postSaveOn() {
        boolean ok = queries.enqueuePayload("save_on", null, null, null);
        return ok ? "redirect:/system/save?msg=on" : "redirect:/system/save?err=failed";
    }

    @PostMapping("/system/save-off")
    public String postSaveOff() {
        boolean ok = queries.enqueuePayload("save_off", null, null, null);
        return ok ? "redirect:/system/save?msg=off" : "redirect:/system/save?err=failed";
    }

    @GetMapping("/system/retention")
    public String systemRetention(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "system-retention";
    }

    @PostMapping("/system/retention")
    public String postRetention(@RequestParam(required = false) Integer days) {
        boolean ok = queries.enqueueRetentionCleanup(days);
        return ok ? "redirect:/system/retention?msg=queued" : "redirect:/system/retention?err=failed";
    }

    @GetMapping("/system/reload")
    public String systemReload(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "system-reload";
    }

    @PostMapping("/system/reload")
    public String postReload(@RequestParam(required = false) String confirm) {
        if (!"RELOAD".equals(confirm)) {
            return "redirect:/system/reload?err=confirm";
        }
        boolean ok = queries.enqueuePayload("reload_server", null, null, null);
        return ok ? "redirect:/system/reload?msg=queued" : "redirect:/system/reload?err=failed";
    }

    @GetMapping("/system/stop")
    public String systemStop(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "system-stop";
    }

    @PostMapping("/system/stop")
    public String postStop(@RequestParam(required = false) String confirm1, @RequestParam(required = false) String confirm2) {
        if (!"STOP".equals(confirm1) || !"STOP".equals(confirm2)) {
            return "redirect:/system/stop?err=confirm";
        }
        boolean ok = queries.enqueuePayload("stop_server", null, null, null);
        return ok ? "redirect:/system/stop?msg=queued" : "redirect:/system/stop?err=failed";
    }

    @GetMapping("/system/runtime")
    public String systemRuntime(Model model) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("panelBind", panelBind);
        model.addAttribute("panelPort", panelPort);
        model.addAttribute("sshHint", sshHint);
        model.addAttribute("securityReady", securityReady);
        return "system-runtime";
    }

    @GetMapping("/system/maintenance")
    public String maintenance(
            @RequestParam(required = false) String msg,
            @RequestParam(required = false) String err,
            Model model
    ) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        model.addAttribute("online", queries.onlinePlayers());
        model.addAttribute("msg", msg);
        model.addAttribute("err", err);
        return "system-maintenance";
    }

    @PostMapping("/system/maintenance/kick")
    public String maintenanceKick(
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "false") boolean whitelist
    ) {
        String why = reason == null || reason.isBlank() ? "服务器维护中，请稍后再试" : reason.trim();
        boolean ok = queries.enqueuePayload("maintenance_kick", whitelist ? "whitelist" : null, null, why);
        return ok ? "redirect:/system/maintenance?msg=kick_queued" : "redirect:/system/maintenance?err=failed";
    }

    @PostMapping("/system/maintenance/clear")
    public String maintenanceClear(@RequestParam(defaultValue = "false") boolean whitelist) {
        boolean ok = queries.enqueuePayload("clear_maintenance", whitelist ? "whitelist" : null, null, null);
        return ok ? "redirect:/system/maintenance?msg=clear_queued" : "redirect:/system/maintenance?err=failed";
    }

    @PostMapping("/system/motd")
    public String setMotd(@RequestParam String motd) {
        boolean ok = queries.enqueuePayload("set_motd", null, null, motd);
        return ok ? "redirect:/status/connection?msg=motd_queued" : "redirect:/status/connection?err=failed";
    }

    @PostMapping("/system/idle")
    public String setIdle(@RequestParam int minutes) {
        boolean ok = queries.enqueuePayload("set_idle_timeout", null, null, String.valueOf(minutes));
        return ok ? "redirect:/system/maintenance?msg=idle_queued" : "redirect:/system/maintenance?err=failed";
    }

    @GetMapping("/diag/logs")
    public String diagLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String q,
            Model model
    ) {
        model.addAllAttributes(queries.consolePage(level, q));
        return "diag-logs";
    }

    @GetMapping("/diag/actions")
    public String diagActions(Model model) {
        model.addAttribute("actions", queries.recentPanelActions(100));
        model.addAttribute("pending", queries.recentPanelActions(50).stream()
                .filter(a -> "pending".equals(String.valueOf(a.get("status")))).count());
        return "diag-actions";
    }

    @GetMapping("/diag/movements")
    public String diagMovements(@RequestParam(required = false) String player, Model model) {
        model.addAttribute("player", player);
        if (player != null && !player.isBlank()) {
            model.addAttribute("movements", queries.movements(player, 0, 0));
        }
        return "diag-movements";
    }

    @GetMapping("/security/risk")
    public String securityRisk(Model model) {
        model.addAllAttributes(governance.riskPage());
        return "security-risk";
    }

    @PostMapping("/security/risk/recompute")
    public String securityRiskRecompute() {
        governance.recomputeRisk(7);
        return "redirect:/security/risk?msg=recomputed";
    }

    @GetMapping("/security/approvals")
    public String securityApprovals(@RequestParam(required = false) String status, Model model) {
        model.addAttribute("status", status);
        model.addAttribute("approvals", governance.listApprovals(status, 100));
        model.addAttribute("approvalEnabled", governance.approvalEnabled());
        return "security-approvals";
    }

    @PostMapping("/security/approvals/{id}/approve")
    public String approveRequest(@PathVariable long id) {
        boolean ok = governance.decideApproval(id, true, "panel-admin");
        return ok ? "redirect:/security/approvals?msg=approved" : "redirect:/security/approvals?err=failed";
    }

    @PostMapping("/security/approvals/{id}/reject")
    public String rejectRequest(@PathVariable long id) {
        boolean ok = governance.decideApproval(id, false, "panel-admin");
        return ok ? "redirect:/security/approvals?msg=rejected" : "redirect:/security/approvals?err=failed";
    }

    @GetMapping("/security/integrity")
    public String securityIntegrity(Model model) {
        model.addAllAttributes(governance.integrityPage());
        return "security-integrity";
    }

    @PostMapping("/security/integrity/verify")
    public String securityIntegrityVerify(Model model) {
        return "redirect:/security/integrity";
    }

    @GetMapping("/security/webhooks")
    public String securityWebhooks(Model model) {
        model.addAllAttributes(governance.webhookPage());
        return "security-webhooks";
    }

    @GetMapping("/security/config-history")
    public String configHistory(Model model) {
        model.addAttribute("revisions", governance.configHistory(100));
        return "security-config-history";
    }

    @PostMapping("/security/config-history/{id}/rollback")
    public String rollbackConfig(@PathVariable long id) {
        boolean ok = governance.rollbackGamerule(id);
        return ok ? "redirect:/security/config-history?msg=rollback_queued" : "redirect:/security/config-history?err=failed";
    }

    @GetMapping("/security/snapshots")
    public String snapshots(Model model) {
        model.addAttribute("snapshots", governance.snapshots(50));
        return "security-snapshots";
    }

    @PostMapping("/security/snapshots/create")
    public String createSnapshot(@RequestParam(required = false) String label) {
        boolean ok = governance.createSnapshot(label, "panel");
        return ok ? "redirect:/security/snapshots?msg=queued" : "redirect:/security/snapshots?err=failed";
    }

    @PostMapping("/security/snapshots/{id}/restore")
    public String restoreSnapshot(@PathVariable long id) {
        boolean ok = governance.restoreSnapshot(id);
        return ok ? "redirect:/security/snapshots?msg=restore_queued" : "redirect:/security/snapshots?err=failed";
    }

    @GetMapping("/center")
    public String center(Model model) {
        model.addAllAttributes(governance.centerPage());
        return "center";
    }

    @GetMapping("/security/economy")
    public String economy(Model model) {
        model.addAttribute("events", governance.economyEvents(100));
        return "security-economy";
    }

    @GetMapping("/security/mod-sources")
    public String modSources(Model model) {
        model.addAttribute("sources", governance.modSources(100));
        return "security-mod-sources";
    }

    @GetMapping("/login/mfa")
    public String loginMfa(@RequestParam(required = false) String err, Model model) {
        model.addAttribute("err", err);
        return "login-mfa";
    }

    @PostMapping("/login/mfa")
    public String postLoginMfa(@RequestParam String code, jakarta.servlet.http.HttpSession session) {
        Object pending = session.getAttribute(PanelMfaFilter.MFA_PENDING);
        String user = pending == null ? null : String.valueOf(pending);
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (user == null && auth != null) {
            user = auth.getName();
        }
        if (user == null || !mfa.verify(user, code)) {
            return "redirect:/login/mfa?err=bad";
        }
        session.setAttribute(PanelMfaFilter.MFA_OK, user);
        session.removeAttribute(PanelMfaFilter.MFA_PENDING);
        return "redirect:/";
    }

    @GetMapping("/setup")
    public String setup(Model model) {
        model.addAttribute("policies", List.of("宽松", "推荐", "严格"));
        return "setup-wizard";
    }

    @PostMapping("/setup/complete")
    public String setupComplete(
            @RequestParam(defaultValue = "推荐") String policy,
            @RequestParam(required = false) String enableTotp
    ) {
        queries.completeSetup(policy);
        return "redirect:/?msg=setup_done";
    }

    @GetMapping("/security/mfa")
    public String securityMfa(Model model, org.springframework.security.core.Authentication auth) {
        model.addAttribute("enabled", mfa.isEnabled(auth.getName()));
        return "security-mfa";
    }

    @PostMapping("/security/mfa/enroll")
    public String mfaEnroll(Model model, org.springframework.security.core.Authentication auth) {
        model.addAllAttributes(mfa.beginEnroll(auth.getName()));
        model.addAttribute("enabled", false);
        model.addAttribute("enrolling", true);
        return "security-mfa";
    }

    @PostMapping("/security/mfa/confirm")
    public String mfaConfirm(@RequestParam String code, org.springframework.security.core.Authentication auth) {
        boolean ok = mfa.confirmEnroll(auth.getName(), code);
        return ok ? "redirect:/security/mfa?msg=enabled" : "redirect:/security/mfa?err=bad";
    }

    @PostMapping("/security/mfa/disable")
    public String mfaDisable(org.springframework.security.core.Authentication auth) {
        mfa.disable(auth.getName());
        return "redirect:/security/mfa?msg=disabled";
    }

    @PostMapping("/security/mfa/player/enroll")
    public String playerTotpEnroll(@RequestParam String uuid, Model model) {
        model.addAllAttributes(governance.enrollPlayerTotp(uuid));
        model.addAttribute("playerUuid", uuid);
        model.addAttribute("enabled", mfa.isEnabled("x"));
        return "security-mfa";
    }

    @PostMapping("/security/mfa/player/confirm")
    public String playerTotpConfirm(@RequestParam String uuid, @RequestParam String code) {
        boolean ok = governance.enablePlayerTotp(uuid, code);
        return ok ? "redirect:/security/mfa?msg=player_enabled" : "redirect:/security/mfa?err=bad";
    }

    @GetMapping("/security/lockdown")
    public String lockdownPage(Model model) {
        model.addAttribute("runtime", queries.runtimeSnapshot());
        return "security-lockdown";
    }

    @PostMapping("/security/lockdown/on")
    public String lockdownOn() {
        boolean ok = queries.enqueuePayload("lockdown_on", null, null, null);
        return ok ? "redirect:/security/lockdown?msg=on" : "redirect:/security/lockdown?err=failed";
    }

    @PostMapping("/security/lockdown/off")
    public String lockdownOff() {
        boolean ok = queries.enqueuePayload("lockdown_off", null, null, null);
        return ok ? "redirect:/security/lockdown?msg=off" : "redirect:/security/lockdown?err=failed";
    }

    @GetMapping("/system/schedules")
    public String systemSchedules(Model model) {
        model.addAttribute("schedules", queries.listSchedules(100));
        return "system-schedules";
    }

    @PostMapping("/system/schedules/create")
    public String createSchedule(
            @RequestParam String kind,
            @RequestParam(required = false) String payload,
            @RequestParam(defaultValue = "0") int delaySeconds,
            @RequestParam(defaultValue = "0") int intervalSeconds,
            @RequestParam(required = false) String note
    ) {
        boolean ok = queries.createOpsSchedule(kind, payload, delaySeconds, intervalSeconds, note);
        return ok ? "redirect:/system/schedules?msg=created" : "redirect:/system/schedules?err=failed";
    }

    @GetMapping("/status/trends")
    public String statusTrends(Model model) {
        model.addAttribute("hours24", queries.perfSamplesSince(24));
        model.addAttribute("hours168", queries.perfSamplesSince(168));
        model.addAttribute("forecast", queries.simpleOnlineForecast());
        return "status-trends";
    }
}
