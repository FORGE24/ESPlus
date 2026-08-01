package com.esplus.panel;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Token-based ops endpoints for Discord/QQ bots and mobile ack links.
 */
@RestController
@RequestMapping("/api/ops")
public class PanelOpsApiController {
    private final PanelQueryService queries;
    private final PanelGovernanceService governance;
    private final String opsToken;

    public PanelOpsApiController(
            PanelQueryService queries,
            PanelGovernanceService governance,
            @Value("${esplus.opsApiToken:}") String opsToken
    ) {
        this.queries = queries;
        this.governance = governance;
        this.opsToken = opsToken == null ? "" : opsToken.trim();
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts(@RequestHeader(value = "X-SEM-Token", required = false) String token) {
        requireToken(token);
        return queries.alerts(true);
    }

    @PostMapping("/ack")
    public Map<String, Object> ack(
            @RequestHeader(value = "X-SEM-Token", required = false) String token,
            @RequestParam String alertId
    ) {
        requireToken(token);
        return Map.of("ok", queries.acknowledge(alertId));
    }

    @PostMapping("/kick")
    public Map<String, Object> kick(
            @RequestHeader(value = "X-SEM-Token", required = false) String token,
            @RequestParam String player,
            @RequestParam(required = false) String reason
    ) {
        requireToken(token);
        return Map.of("ok", queries.enqueueKick(player, null, reason == null ? "ops-api" : reason));
    }

    @PostMapping("/ban")
    public Map<String, Object> ban(
            @RequestHeader(value = "X-SEM-Token", required = false) String token,
            @RequestParam String player,
            @RequestParam(required = false) String reason
    ) {
        requireToken(token);
        return Map.of("ok", queries.enqueueBan(player, null, reason == null ? "ops-api" : reason));
    }

    @PostMapping("/lockdown")
    public Map<String, Object> lockdown(
            @RequestHeader(value = "X-SEM-Token", required = false) String token,
            @RequestParam(defaultValue = "true") boolean on
    ) {
        requireToken(token);
        return Map.of("ok", queries.enqueuePayload(on ? "lockdown_on" : "lockdown_off", null, null, null));
    }

    @PostMapping("/approve")
    public Map<String, Object> approve(
            @RequestHeader(value = "X-SEM-Token", required = false) String token,
            @RequestParam long id
    ) {
        requireToken(token);
        return Map.of("ok", governance.decideApproval(id, true, "ops-api"));
    }

    private void requireToken(String token) {
        if (opsToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "opsApiToken not configured");
        }
        if (token == null || !opsToken.equals(token.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad token");
        }
    }
}
