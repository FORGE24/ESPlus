package com.esplus.panel;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PanelApiController {
    private final PanelQueryService queries;

    public PanelApiController(PanelQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return queries.dashboard();
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String traceId
    ) {
        return queries.search(q, category, actor, traceId, 200);
    }

    @GetMapping("/trace/{traceId}")
    public Map<String, Object> trace(@PathVariable String traceId) {
        return queries.itemTrace(traceId);
    }

    @GetMapping("/incident/{eventId}")
    public Map<String, Object> incident(@PathVariable String eventId) {
        return queries.incident(eventId, 15 * 60_000L);
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts(@RequestParam(defaultValue = "true") boolean open) {
        return queries.alerts(open);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) Boolean success
    ) {
        return queries.auditLogs(action, uuid, success, 200);
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return queries.usersSummary();
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long afterId
    ) {
        return queries.serverLogs(level, q, afterId, 300);
    }

    @PostMapping("/console")
    public Map<String, Object> console(@RequestParam String command) {
        return Map.of("ok", queries.enqueueConsoleCommand(command));
    }

    @PostMapping("/players/kick")
    public Map<String, Object> kick(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String reason
    ) {
        return Map.of("ok", queries.enqueueKick(player, uuid, reason));
    }

    @PostMapping("/players/ban")
    public Map<String, Object> ban(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String reason
    ) {
        return Map.of("ok", queries.enqueueBan(player, uuid, reason));
    }

    @PostMapping("/players/unban")
    public Map<String, Object> unban(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueueUnban(player, uuid));
    }

    @PostMapping("/admins/{uuid}/unlock")
    public Map<String, Object> unlock(@PathVariable String uuid) {
        return Map.of("ok", queries.unlockUser(uuid));
    }

    @PostMapping("/admins/{uuid}/reset")
    public Map<String, Object> reset(@PathVariable String uuid) {
        return Map.of("ok", queries.resetUserPassword(uuid));
    }

    @PostMapping("/admins/{uuid}/role")
    public Map<String, Object> role(@PathVariable String uuid, @RequestParam String role) {
        return Map.of("ok", queries.updateUserRole(uuid, role));
    }

    @PostMapping("/alerts/{alertId}/ack")
    public Map<String, Object> ack(@PathVariable String alertId) {
        return Map.of("ok", queries.acknowledge(alertId));
    }

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        return queries.runtimeSnapshot();
    }

    @GetMapping("/perf")
    public List<Map<String, Object>> perf(@RequestParam(defaultValue = "120") int limit) {
        return queries.perfSamples(limit);
    }

    @GetMapping("/players/inventory")
    public List<Map<String, Object>> inventory(
            @RequestParam String q,
            @RequestParam(required = false) String section
    ) {
        return queries.playerInventory(q, section);
    }

    @GetMapping("/schedules")
    public List<Map<String, Object>> schedules() {
        return queries.listSchedules(100);
    }

    @GetMapping("/actions")
    public List<Map<String, Object>> actions() {
        return queries.recentPanelActions(50);
    }

    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(
            @RequestParam String message,
            @RequestParam(defaultValue = "[公告]") String prefix,
            @RequestParam(defaultValue = "1") int times
    ) {
        return Map.of("ok", queries.enqueueBroadcast(message, prefix, times));
    }

    @PostMapping("/maintenance/kick")
    public Map<String, Object> maintenanceKick(
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "false") boolean whitelist
    ) {
        String why = reason == null || reason.isBlank() ? "服务器维护中，请稍后再试" : reason.trim();
        return Map.of("ok", queries.enqueuePayload("maintenance_kick", whitelist ? "whitelist" : null, null, why));
    }

    @PostMapping("/maintenance/clear")
    public Map<String, Object> maintenanceClear(@RequestParam(defaultValue = "false") boolean whitelist) {
        return Map.of("ok", queries.enqueuePayload("clear_maintenance", whitelist ? "whitelist" : null, null, null));
    }
}
