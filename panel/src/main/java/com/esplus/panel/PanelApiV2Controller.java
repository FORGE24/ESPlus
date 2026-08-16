package com.esplus.panel;

import java.util.HashMap;
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

/**
 * V2 REST API — JSON endpoints for the React SPA.
 * Delegates to PanelQueryService and PanelGovernanceService.
 * Covers endpoints not already present in PanelApiController.
 */
@RestController
@RequestMapping("/api")
public class PanelApiV2Controller {

    private final PanelQueryService queries;
    private final PanelGovernanceService governance;
    private final PanelMfaService mfa;

    public PanelApiV2Controller(PanelQueryService queries, PanelGovernanceService governance, PanelMfaService mfa) {
        this.queries = queries;
        this.governance = governance;
        this.mfa = mfa;
    }

    // ── Players ──────────────────────────────────────────────

    @GetMapping("/players/online")
    public List<Map<String, Object>> onlinePlayers() {
        return queries.onlinePlayers();
    }

    @GetMapping("/players/page")
    public Map<String, Object> playersPage() {
        return queries.playersPage();
    }

    @GetMapping("/players/profile")
    public Map<String, Object> playerProfile(@RequestParam(required = false) String q) {
        return queries.playerProfile(q);
    }

    @PostMapping("/players/temp-ban")
    public Map<String, Object> tempBan(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(required = false) String reason
    ) {
        return Map.of("ok", queries.enqueuePayload("temp_ban_player", player, uuid,
                (reason == null ? "" : reason) + "|" + minutes));
    }

    @PostMapping("/players/gamemode")
    public Map<String, Object> gamemode(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String mode
    ) {
        return Map.of("ok", queries.enqueuePayload("set_player_gamemode", player, uuid, mode));
    }

    @PostMapping("/players/clear")
    public Map<String, Object> clear(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueuePayload("clear_inventory", player, uuid, null));
    }

    @PostMapping("/players/heal")
    public Map<String, Object> heal(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueuePayload("heal_player", player, uuid, null));
    }

    @PostMapping("/players/feed")
    public Map<String, Object> feed(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueuePayload("feed_player", player, uuid, null));
    }

    @PostMapping("/players/extinguish")
    public Map<String, Object> extinguish(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueuePayload("extinguish_player", player, uuid, null));
    }

    @PostMapping("/players/teleport")
    public Map<String, Object> teleport(@RequestBody Map<String, Object> body) {
        String player = str(body, "player");
        String uuid = str(body, "uuid");
        String mode = str(body, "mode");
        String target = str(body, "target");
        String payload;
        if ("spawn".equals(mode)) {
            payload = "spawn";
        } else if ("player".equals(mode)) {
            payload = "player|" + (target == null ? "" : target);
        } else {
            payload = "coords|" + body.get("x") + "|" + body.get("y") + "|" + body.get("z") + "|" + (body.get("dimension") == null ? "" : body.get("dimension"));
        }
        return Map.of("ok", queries.enqueuePayload("teleport_player", player, uuid, payload));
    }

    @PostMapping("/players/clear-ender")
    public Map<String, Object> clearEnder(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueuePayload("clear_enderchest", player, uuid, null));
    }

    @PostMapping("/players/spawnpoint")
    public Map<String, Object> spawnpoint(@RequestBody Map<String, Object> body) {
        String player = str(body, "player");
        String uuid = str(body, "uuid");
        String mode = str(body, "mode");
        String payload;
        if ("world".equals(mode)) {
            payload = "world";
        } else if ("coords".equals(mode)) {
            payload = "coords|" + body.get("x") + "|" + body.get("y") + "|" + body.get("z");
        } else {
            payload = "here";
        }
        return Map.of("ok", queries.enqueuePayload("set_spawnpoint", player, uuid, payload));
    }

    @PostMapping("/players/clear-effects")
    public Map<String, Object> clearEffects(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueuePayload("clear_effects", player, uuid, null));
    }

    @PostMapping("/players/effect")
    public Map<String, Object> effect(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String effect,
            @RequestParam(defaultValue = "30") int seconds,
            @RequestParam(defaultValue = "0") int amplifier
    ) {
        return Map.of("ok", queries.enqueuePayload("give_effect", player, uuid, effect + "|" + seconds + "|" + amplifier));
    }

    // ── Admins ───────────────────────────────────────────────

    @GetMapping("/admins/page")
    public Map<String, Object> adminsPage() {
        return queries.adminsPage();
    }

    @PostMapping("/admins/{uuid}/op-bound")
    public Map<String, Object> opBound(@PathVariable String uuid, @RequestParam boolean opBound) {
        return Map.of("ok", queries.updateOpBound(uuid, opBound));
    }

    @PostMapping("/admins/op/grant")
    public Map<String, Object> grantOp(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueueGrantOp(player, uuid));
    }

    @PostMapping("/admins/op/revoke")
    public Map<String, Object> revokeOp(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid
    ) {
        return Map.of("ok", queries.enqueueRevokeOp(player, uuid));
    }

    @GetMapping("/admins/{uuid}/perms")
    public Map<String, Object> userPerms(@PathVariable String uuid) {
        return queries.userPermissionsPage(uuid);
    }

    @PostMapping("/admins/{uuid}/perms")
    public Map<String, Object> savePerms(
            @PathVariable String uuid,
            @RequestParam(value = "perm", required = false) List<String> perms
    ) {
        return Map.of("ok", queries.saveUserPermissions(uuid, perms));
    }

    @PostMapping("/admins/{uuid}/perms/reset-role")
    public Map<String, Object> resetPerms(@PathVariable String uuid, @RequestParam String role) {
        return Map.of("ok", queries.applyRolePermissionDefaults(uuid, role));
    }

    // ── Generic payload ──────────────────────────────────────

    @PostMapping("/payload/{action}")
    public Map<String, Object> payload(
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        body = body == null ? Map.of() : body;
        String player = str(body, "player");
        String uuid = str(body, "uuid");
        String payload = str(body, "payload");
        boolean ok = queries.enqueuePayload(action, player, uuid, payload);
        return Map.of("ok", ok);
    }

    // ── Messages ─────────────────────────────────────────────

    @PostMapping("/messages/tell")
    public Map<String, Object> tell(
            @RequestParam(required = false) String player,
            @RequestParam(required = false) String uuid,
            @RequestParam String message
    ) {
        return Map.of("ok", queries.enqueueTell(player, uuid, message));
    }

    @PostMapping("/messages/schedule")
    public Map<String, Object> createSchedule(
            @RequestParam String message,
            @RequestParam(defaultValue = "[公告]") String prefix,
            @RequestParam(defaultValue = "1") int times,
            @RequestParam(defaultValue = "0") int delaySeconds,
            @RequestParam(defaultValue = "0") int intervalSeconds,
            @RequestParam(required = false) String note
    ) {
        return Map.of("ok", queries.createBroadcastSchedule(message, prefix, times, delaySeconds, intervalSeconds, note));
    }

    @PostMapping("/messages/schedule/{id}/toggle")
    public Map<String, Object> toggleSchedule(@PathVariable long id, @RequestParam boolean enabled) {
        return Map.of("ok", queries.setScheduleEnabled(id, enabled));
    }

    @PostMapping("/messages/schedule/{id}/delete")
    public Map<String, Object> deleteSchedule(@PathVariable long id) {
        return Map.of("ok", queries.deleteSchedule(id));
    }

    // ── Governance ───────────────────────────────────────────

    @GetMapping("/governance/risk")
    public Map<String, Object> riskPage() {
        return governance.riskPage();
    }

    @PostMapping("/governance/risk/recompute")
    public Map<String, Object> recomputeRisk() {
        governance.recomputeRisk(7);
        return Map.of("ok", true);
    }

    @GetMapping("/governance/approvals")
    public List<Map<String, Object>> approvals(@RequestParam(required = false) String status) {
        return governance.listApprovals(status, 100);
    }

    @GetMapping("/governance/approval-enabled")
    public boolean approvalEnabled() {
        return governance.approvalEnabled();
    }

    @PostMapping("/governance/approvals/{id}/approve")
    public Map<String, Object> approve(@PathVariable long id) {
        return Map.of("ok", governance.decideApproval(id, true, "panel-admin"));
    }

    @PostMapping("/governance/approvals/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id) {
        return Map.of("ok", governance.decideApproval(id, false, "panel-admin"));
    }

    @GetMapping("/governance/integrity")
    public Map<String, Object> integrity() {
        return governance.integrityPage();
    }

    @GetMapping("/governance/webhooks")
    public Map<String, Object> webhooks() {
        return governance.webhookPage();
    }

    @GetMapping("/governance/config-history")
    public List<Map<String, Object>> configHistory() {
        return governance.configHistory(100);
    }

    @PostMapping("/governance/config-history/{id}/rollback")
    public Map<String, Object> rollbackConfig(@PathVariable long id) {
        return Map.of("ok", governance.rollbackGamerule(id));
    }

    @GetMapping("/governance/snapshots")
    public List<Map<String, Object>> snapshots() {
        return governance.snapshots(50);
    }

    @PostMapping("/governance/snapshots/create")
    public Map<String, Object> createSnapshot(@RequestParam(required = false) String label) {
        return Map.of("ok", governance.createSnapshot(label, "panel"));
    }

    @PostMapping("/governance/snapshots/{id}/restore")
    public Map<String, Object> restoreSnapshot(@PathVariable long id) {
        return Map.of("ok", governance.restoreSnapshot(id));
    }

    @GetMapping("/governance/center")
    public Map<String, Object> center() {
        return governance.centerPage();
    }

    @GetMapping("/governance/economy")
    public List<Map<String, Object>> economy() {
        return governance.economyEvents(100);
    }

    @GetMapping("/governance/mod-sources")
    public List<Map<String, Object>> modSources() {
        return governance.modSources(100);
    }

    @GetMapping("/governance/item-graph/{traceId}")
    public Map<String, Object> itemGraph(@PathVariable String traceId) {
        return governance.itemGraph(traceId);
    }

    // ── MFA ──────────────────────────────────────────────────

    @GetMapping("/mfa/status")
    public Map<String, Object> mfaStatus(org.springframework.security.core.Authentication auth) {
        if (auth == null) return Map.of("enabled", false);
        return Map.of("enabled", mfa.isEnabled(auth.getName()));
    }

    @PostMapping("/mfa/enroll")
    public Map<String, Object> mfaEnroll(org.springframework.security.core.Authentication auth) {
        return mfa.beginEnroll(auth.getName());
    }

    @PostMapping("/mfa/confirm")
    public Map<String, Object> mfaConfirm(@RequestParam String code, org.springframework.security.core.Authentication auth) {
        return Map.of("ok", mfa.confirmEnroll(auth.getName(), code));
    }

    @PostMapping("/mfa/disable")
    public Map<String, Object> mfaDisable(org.springframework.security.core.Authentication auth) {
        mfa.disable(auth.getName());
        return Map.of("ok", true);
    }

    // ── Helper ───────────────────────────────────────────────
    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
