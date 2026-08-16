package com.esplus.panel;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/audit/export")
    public ResponseEntity<String> auditExport(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) Boolean success
    ) {
        String csv = queries.exportAuditCsv(action, uuid, success, 5000);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=audit-export.csv")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv);
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

    // ── Automation API ─────────────────────────────────────────

    @GetMapping("/automation/tasks")
    public List<Map<String, Object>> automationTasks() {
        return queries.listAutomationTasks();
    }

    @GetMapping("/automation/tasks/{id}")
    public Map<String, Object> automationTask(@PathVariable long id) {
        return queries.getAutomationTask(id);
    }

    @PostMapping("/automation/tasks")
    public Map<String, Object> createAutomationTask(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "manual") String triggerType,
            @RequestParam(defaultValue = "0") int intervalSecs,
            @RequestParam(required = false) String cron) {
        long id = queries.createAutomationTask(name, description, triggerType, intervalSecs, cron);
        return Map.of("ok", id > 0, "id", id);
    }

    @PostMapping("/automation/tasks/{id}")
    public Map<String, Object> updateAutomationTask(
            @PathVariable long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "manual") String triggerType,
            @RequestParam(defaultValue = "0") int intervalSecs,
            @RequestParam(required = false) String cron,
            @RequestParam(defaultValue = "true") boolean enabled) {
        return Map.of("ok", queries.updateAutomationTask(id, name, description, triggerType, intervalSecs, cron, enabled));
    }

    @PostMapping("/automation/tasks/{id}/delete")
    public Map<String, Object> deleteAutomationTask(@PathVariable long id) {
        return Map.of("ok", queries.deleteAutomationTask(id));
    }

    @PostMapping("/automation/tasks/{id}/toggle")
    public Map<String, Object> toggleAutomationTask(@PathVariable long id, @RequestParam boolean enabled) {
        return Map.of("ok", queries.toggleAutomationTaskEnabled(id, enabled));
    }

    @PostMapping("/automation/tasks/{id}/trigger")
    public Map<String, Object> triggerAutomationTask(@PathVariable long id) {
        return Map.of("ok", queries.triggerAutomationTask(id));
    }

    @PostMapping("/automation/tasks/{taskId}/nodes")
    public Map<String, Object> addAutomationNode(@PathVariable long taskId, @RequestParam(required = false) String name) {
        long id = queries.addAutomationNode(taskId, name);
        return Map.of("ok", id > 0, "id", id);
    }

    @PostMapping("/automation/nodes/{nodeId}/delete")
    public Map<String, Object> deleteAutomationNode(@PathVariable long nodeId) {
        return Map.of("ok", queries.deleteAutomationNode(nodeId));
    }

    @PostMapping("/automation/tasks/{taskId}/operations")
    public Map<String, Object> addAutomationOperation(
            @PathVariable long taskId,
            @RequestParam long nodeId,
            @RequestParam String actionType,
            @RequestParam(required = false) String params) {
        long id = queries.addAutomationOperation(taskId, nodeId, actionType, params);
        return Map.of("ok", id > 0, "id", id);
    }

    @PostMapping("/automation/operations/{opId}")
    public Map<String, Object> updateAutomationOperation(
            @PathVariable long opId,
            @RequestParam String actionType,
            @RequestParam(required = false) String params,
            @RequestParam(defaultValue = "true") boolean enabled) {
        return Map.of("ok", queries.updateOperationParams(opId, actionType, params, enabled));
    }

    @PostMapping("/automation/nodes/{nodeId}/move")
    public Map<String, Object> moveAutomationNode(
            @PathVariable long nodeId,
            @RequestParam long taskId,
            @RequestParam(defaultValue = "1") int direction) {
        return Map.of("ok", queries.moveAutomationNode(taskId, nodeId, direction));
    }

    @PostMapping("/automation/operations/{opId}/move")
    public Map<String, Object> moveAutomationOperation(
            @PathVariable long opId,
            @RequestParam long nodeId,
            @RequestParam(defaultValue = "1") int direction) {
        return Map.of("ok", queries.moveAutomationOperation(nodeId, opId, direction));
    }

    @GetMapping("/automation/tasks/{taskId}/logs")
    public List<Map<String, Object>> automationLogs(
            @PathVariable long taskId,
            @RequestParam(defaultValue = "20") int limit) {
        return queries.automationLogs(taskId, limit);
    }

    @PostMapping("/automation/tasks/{id}/clone")
    public Map<String, Object> cloneAutomationTask(@PathVariable long id) {
        long newId = queries.cloneAutomationTask(id);
        return Map.of("ok", newId > 0, "id", newId);
    }

    @PostMapping("/automation/tasks/import")
    public Map<String, Object> importAutomationTask(@RequestBody Map<String, Object> json) {
        String name = json.getOrDefault("name", "Imported").toString();
        String desc = json.getOrDefault("description", "").toString();
        String trigger = json.getOrDefault("trigger_type", "manual").toString();
        int interval = json.get("trigger_interval_secs") instanceof Number n ? n.intValue() : 0;
        String cron = json.getOrDefault("trigger_cron", "").toString();
        long id = queries.createAutomationTask(name, desc, trigger, interval, cron);
        @SuppressWarnings("unchecked")
        var nodes = (List<Map<String, Object>>) json.get("nodes");
        @SuppressWarnings("unchecked")
        var ops = (List<Map<String, Object>>) json.get("operations");
        if (nodes != null && id > 0) {
            var nodeIdMap = new java.util.HashMap<Long, Long>();
            for (var node : nodes) {
                long oldNid = node.get("id") instanceof Number n ? n.longValue() : 0;
                long newNid = queries.addAutomationNode(id, String.valueOf(node.getOrDefault("name", "步骤")));
                nodeIdMap.put(oldNid, newNid);
            }
            if (ops != null) {
                for (var op : ops) {
                    long oldNid = op.get("node_id") instanceof Number n ? n.longValue() : 0;
                    Long newNid = nodeIdMap.get(oldNid);
                    if (newNid != null) {
                        queries.addAutomationOperation(id, newNid,
                                String.valueOf(op.getOrDefault("action_type", "console_cmd")),
                                String.valueOf(op.getOrDefault("params", "")));
                    }
                }
            }
        }
        return Map.of("ok", id > 0, "id", id);
    }

    @GetMapping("/automation/stats")
    public Map<String, Object> automationStats() {
        return queries.automationStats();
    }

    @PostMapping("/automation/operations/{opId}/delete")
    public Map<String, Object> deleteAutomationOperation(@PathVariable long opId) {
        return Map.of("ok", queries.deleteAutomationOperation(opId));
    }
}
