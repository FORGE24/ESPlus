package com.esplus.panel;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback — forwards all non-API, non-static GET requests to index.html
 * so that React Router can handle client-side navigation on page refresh.
 *
 * Spring MVC gives priority to more specific mappings, so /api/** endpoints
 * and static resources (/assets/**, /css/**, /js/**) are served correctly.
 */
@Controller
public class PanelSpaController {

    /**
     * Catch-all for all SPA routes. Excludes paths starting with /api/ or
     * containing a file extension (e.g. .js, .css, .png) by virtue of
     * Spring's path matching prioritizing more specific controllers.
     */
    @GetMapping(value = {
            "/",
            "/login",
            "/login/mfa",
            "/players",
            "/players/profile",
            "/players/actions",
            "/bans",
            "/whitelist",
            "/messages",
            "/messages/schedule",
            "/messages/title",
            "/messages/bossbar",
            "/messages/filter",
            "/messages/mute",
            "/world/time",
            "/world/difficulty",
            "/world/border",
            "/world/spawn",
            "/world/dimensions",
            "/gamerules",
            "/entities",
            "/entities/cleanup",
            "/items/give",
            "/items/inventory",
            "/items/clear",
            "/search",
            "/admins",
            "/admins/{uuid}/perms",
            "/security/sudo",
            "/security/accounts",
            "/audit",
            "/alerts",
            "/security/risk",
            "/security/approvals",
            "/security/integrity",
            "/security/webhooks",
            "/security/config-history",
            "/security/snapshots",
            "/security/economy",
            "/security/mod-sources",
            "/security/mfa",
            "/security/lockdown",
            "/center",
            "/access/ops",
            "/access/spectator",
            "/scoreboard",
            "/scoreboard/teams",
            "/console",
            "/remote",
            "/system/save",
            "/system/retention",
            "/system/reload",
            "/system/stop",
            "/system/maintenance",
            "/system/schedules",
            "/automation",
            "/system/runtime",
            "/diag/logs",
            "/diag/actions",
            "/diag/movements",
            "/trace/{traceId}",
            "/incident/{eventId}",
            "/status",
            "/status/performance",
            "/status/trends",
            "/status/connection",
            "/status/versions",
            "/automation/{id}",
            "/setup"
    })
    public String spa() {
        return "forward:/index.html";
    }
}
