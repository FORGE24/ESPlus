// ═══════════════════════════════════════════════════════════
// ES+ Panel — API Client
// Session-based auth with CSRF token handling.
// ═══════════════════════════════════════════════════════════

let csrfToken = ''
let csrfHeader = 'X-CSRF-TOKEN'

// Read CSRF token from meta tag (injected by Spring Security into the page)
function initCsrf() {
  const meta = document.querySelector('meta[name="_csrf"]') as HTMLMetaElement | null
  const metaHeader = document.querySelector('meta[name="_csrf_header"]') as HTMLMetaElement | null
  if (meta) csrfToken = meta.content
  if (metaHeader) csrfHeader = metaHeader.content
}

initCsrf()

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers: Record<string, string> = {
    'Accept': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  }

  // For POST/PUT/DELETE with form data, we need CSRF
  const method = (options.method || 'GET').toUpperCase()
  if (method !== 'GET' && csrfToken) {
    headers[csrfHeader] = csrfToken
  }

  // If body is a plain object, encode as form-urlencoded (Spring @RequestParam)
  if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    const params = new URLSearchParams()
    const obj = options.body as unknown as Record<string, unknown>
    for (const [k, v] of Object.entries(obj)) {
      if (v !== undefined && v !== null) params.append(k, String(v))
    }
    options.body = params.toString()
  }

  const res = await fetch(path, { ...options, headers, credentials: 'same-origin' })

  // 401/403 → redirect to login
  if (res.status === 401 || res.status === 403) {
    // Check if this is an API call (not the auth check itself)
    if (!path.startsWith('/api/auth/')) {
      window.location.href = '/login'
      throw new ApiError(res.status, '未授权，正在跳转登录…')
    }
  }

  // Handle redirects (form login responses)
  if (res.redirected) {
    window.location.href = res.url
    throw new ApiError(302, 'Redirected')
  }

  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new ApiError(res.status, text || `HTTP ${res.status}`)
  }

  const ct = res.headers.get('content-type') || ''
  if (ct.includes('application/json')) {
    return res.json() as Promise<T>
  }
  return res.text() as unknown as Promise<T>
}

export const api = {
  get: <T>(path: string) => request<T>(path),

  post: <T>(path: string, body?: Record<string, string | number | boolean | undefined>) =>
    request<T>(path, { method: 'POST', body: body as unknown as BodyInit }),

  postJson: <T>(path: string, body: unknown) =>
    request<T>(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),

  postForm: <T>(path: string, formData: FormData) =>
    request<T>(path, { method: 'POST', body: formData }),

  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

// ── High-level API wrappers ──────────────────────────────────

export const PanelAPI = {
  // Auth
  auth: {
    login: (username: string, password: string) =>
      api.post<{ ok: boolean; mfaRequired?: boolean; error?: string }>('/api/auth/login', { username, password }),
    mfa: (code: string) =>
      api.post<{ ok: boolean; error?: string }>('/api/auth/mfa', { code }),
    me: () => api.get<{ name: string; role: string }>('/api/auth/me'),
    logout: () => api.post<{ ok: boolean }>('/api/auth/logout', {}),
  },

  // Dashboard
  dashboard: () => api.get<import('./types').DashboardData>('/api/dashboard'),

  // Players
  onlinePlayers: () => api.get<import('./types').OnlinePlayer[]>('/api/players/online'),
  playersPage: () => api.get<{ online: import('./types').OnlinePlayer[]; bans: unknown[] }>('/api/players/page'),
  playerProfile: (q: string) => api.get<Record<string, unknown>>(`/api/players/profile?q=${encodeURIComponent(q)}`),
  playerInventory: (q: string, section?: string) =>
    api.get<unknown[]>(`/api/players/inventory?q=${encodeURIComponent(q)}${section ? `&section=${section}` : ''}`),

  // Player actions
  kick: (player?: string, uuid?: string, reason?: string) =>
    api.post<{ ok: boolean }>('/api/players/kick', { player, uuid, reason }),
  ban: (player?: string, uuid?: string, reason?: string) =>
    api.post<{ ok: boolean }>('/api/players/ban', { player, uuid, reason }),
  tempBan: (player?: string, uuid?: string, minutes = 60, reason?: string) =>
    api.post<{ ok: boolean }>('/api/players/temp-ban', { player, uuid, minutes, reason }),
  unban: (player?: string, uuid?: string) =>
    api.post<{ ok: boolean }>('/api/players/unban', { player, uuid }),
  gamemode: (player?: string, uuid?: string, mode?: string) =>
    api.post<{ ok: boolean }>('/api/players/gamemode', { player, uuid, mode }),
  clear: (player?: string, uuid?: string) =>
    api.post<{ ok: boolean }>('/api/players/clear', { player, uuid }),
  heal: (player?: string, uuid?: string) =>
    api.post<{ ok: boolean }>('/api/players/heal', { player, uuid }),
  feed: (player?: string, uuid?: string) =>
    api.post<{ ok: boolean }>('/api/players/feed', { player, uuid }),
  extinguish: (player?: string, uuid?: string) =>
    api.post<{ ok: boolean }>('/api/players/extinguish', { player, uuid }),
  teleport: (data: Record<string, string | number | undefined>) =>
    api.post<{ ok: boolean }>('/api/players/teleport', data),
  clearEnder: (player?: string, uuid?: string) =>
    api.post<{ ok: boolean }>('/api/players/clear-ender', { player, uuid }),
  spawnpoint: (data: Record<string, string | number | undefined>) =>
    api.post<{ ok: boolean }>('/api/players/spawnpoint', data),
  clearEffects: (player?: string, uuid?: string) =>
    api.post<{ ok: boolean }>('/api/players/clear-effects', { player, uuid }),
  effect: (player?: string, uuid?: string, effect?: string, seconds = 30, amplifier = 0) =>
    api.post<{ ok: boolean }>('/api/players/effect', { player, uuid, effect, seconds, amplifier }),

  // Audit
  audit: (action?: string, uuid?: string, success?: boolean) =>
    api.get<import('./types').AuditLog[]>(`/api/audit?action=${action || ''}&uuid=${uuid || ''}${success !== undefined ? `&success=${success}` : ''}`),
  auditExport: (action?: string, uuid?: string, success?: boolean) => {
    const params = new URLSearchParams()
    if (action) params.set('action', action)
    if (uuid) params.set('uuid', uuid)
    if (success !== undefined) params.set('success', String(success))
    window.open(`/api/audit/export?${params}`, '_blank')
  },

  // Alerts
  alerts: (open = true) => api.get<import('./types').Alert[]>(`/api/alerts?open=${open}`),
  ackAlert: (alertId: string | number) => api.post<{ ok: boolean }>(`/api/alerts/${alertId}/ack`, {}),

  // Search
  search: (q?: string, category?: string, actor?: string, traceId?: string) =>
    api.get<import('./types').GameEvent[]>(`/api/search?q=${q || ''}&category=${category || ''}&actor=${actor || ''}&traceId=${traceId || ''}`),

  // Trace & Incident
  trace: (traceId: string) => api.get<import('./types').ItemTraceData>(`/api/trace/${encodeURIComponent(traceId)}`),
  incident: (eventId: string) => api.get<import('./types').IncidentData>(`/api/incident/${encodeURIComponent(eventId)}`),

  // Admins
  users: () => api.get<import('./types').UserSummary[]>('/api/users'),
  adminsPage: () => api.get<{ users: import('./types').UserSummary[]; lockedCount: number; roles: string[]; recentOpActions: import('./types').PanelAction[] }>('/api/admins/page'),
  unlockUser: (uuid: string) => api.post<{ ok: boolean }>(`/api/admins/${uuid}/unlock`, {}),
  resetUserPassword: (uuid: string) => api.post<{ ok: boolean }>(`/api/admins/${uuid}/reset`, {}),
  updateUserRole: (uuid: string, role: string) => api.post<{ ok: boolean }>(`/api/admins/${uuid}/role`, { role }),
  updateOpBound: (uuid: string, opBound: boolean) => api.post<{ ok: boolean }>(`/api/admins/${uuid}/op-bound`, { opBound }),
  grantOp: (player?: string, uuid?: string) => api.post<{ ok: boolean }>('/api/admins/op/grant', { player, uuid }),
  revokeOp: (player?: string, uuid?: string) => api.post<{ ok: boolean }>('/api/admins/op/revoke', { player, uuid }),
  userPermissions: (uuid: string) => api.get<Record<string, unknown>>(`/api/admins/${uuid}/perms`),
  saveUserPermissions: (uuid: string, perms: string[]) =>
    api.post<{ ok: boolean }>(`/api/admins/${uuid}/perms`, { perm: perms.join(',') }),
  resetPermsToRole: (uuid: string, role: string) =>
    api.post<{ ok: boolean }>(`/api/admins/${uuid}/perms/reset-role`, { role }),

  // Console
  serverLogs: (level?: string, q?: string) =>
    api.get<import('./types').ServerLog[]>(`/api/logs?level=${level || ''}&q=${q || ''}`),
  consoleCmd: (command: string) => api.post<{ ok: boolean }>('/api/console', { command }),

  // Runtime
  runtime: () => api.get<import('./types').RuntimeSnapshot>('/api/runtime'),
  perf: (limit = 120) => api.get<import('./types').PerfSample[]>(`/api/perf?limit=${limit}`),

  // Schedules & Actions
  schedules: () => api.get<import('./types').Schedule[]>('/api/schedules'),
  actions: () => api.get<import('./types').PanelAction[]>('/api/actions'),
  recentActions: (types?: string[]) =>
    api.get<import('./types').PanelAction[]>(`/api/actions${types ? `?types=${types.join(',')}` : ''}`),

  // Broadcast & Messages
  broadcast: (message: string, prefix = '[公告]', times = 1) =>
    api.post<{ ok: boolean }>('/api/broadcast', { message, prefix, times }),
  createSchedule: (data: Record<string, string | number>) =>
    api.post<{ ok: boolean }>('/api/messages/schedule', data),
  toggleSchedule: (id: number, enabled: boolean) =>
    api.post<{ ok: boolean }>(`/api/messages/schedule/${id}/toggle`, { enabled }),
  deleteSchedule: (id: number) =>
    api.post<{ ok: boolean }>(`/api/messages/schedule/${id}/delete`, {}),
  tell: (player?: string, uuid?: string, message?: string) =>
    api.post<{ ok: boolean }>('/api/messages/tell', { player, uuid, message }),

  // Payload — generic action queue
  payload: (action: string, data: Record<string, string | number | boolean | undefined>) =>
    api.post<{ ok: boolean }>(`/api/payload/${action}`, data),

  // Governance
  governance: {
    riskPage: () => api.get<Record<string, unknown>>('/api/governance/risk'),
    recomputeRisk: () => api.post<{ ok: boolean }>('/api/governance/risk/recompute', {}),
    approvals: (status?: string) => api.get<import('./types').Approval[]>(`/api/governance/approvals?status=${status || ''}`),
    approvalEnabled: () => api.get<boolean>('/api/governance/approval-enabled'),
    approve: (id: number) => api.post<{ ok: boolean }>(`/api/governance/approvals/${id}/approve`, {}),
    reject: (id: number) => api.post<{ ok: boolean }>(`/api/governance/approvals/${id}/reject`, {}),
    integrity: () => api.get<Record<string, unknown>>('/api/governance/integrity'),
    webhooks: () => api.get<Record<string, unknown>>('/api/governance/webhooks'),
    configHistory: () => api.get<import('./types').ConfigRevision[]>('/api/governance/config-history'),
    rollbackConfig: (id: number) => api.post<{ ok: boolean }>(`/api/governance/config-history/${id}/rollback`, {}),
    snapshots: () => api.get<import('./types').Snapshot[]>('/api/governance/snapshots'),
    createSnapshot: (label?: string) => api.post<{ ok: boolean }>('/api/governance/snapshots/create', { label }),
    restoreSnapshot: (id: number) => api.post<{ ok: boolean }>(`/api/governance/snapshots/${id}/restore`, {}),
    center: () => api.get<Record<string, unknown>>('/api/governance/center'),
    economy: () => api.get<unknown[]>('/api/governance/economy'),
    modSources: () => api.get<unknown[]>('/api/governance/mod-sources'),
    itemGraph: (traceId: string) => api.get<Record<string, unknown>>(`/api/governance/item-graph/${encodeURIComponent(traceId)}`),
  },

  // MFA
  mfa: {
    status: () => api.get<{ enabled: boolean }>('/api/mfa/status'),
    enroll: () => api.post<Record<string, unknown>>('/api/mfa/enroll', {}),
    confirm: (code: string) => api.post<{ ok: boolean }>('/api/mfa/confirm', { code }),
    disable: () => api.post<{ ok: boolean }>('/api/mfa/disable', {}),
  },

  // Automation
  automation: {
    list: () => api.get<import('./types').AutomationTask[]>('/api/automation/tasks'),
    get: (id: number) => api.get<import('./types').AutomationTask>(`/api/automation/tasks/${id}`),
    create: (data: Record<string, string | number>) => api.post<{ ok: boolean; id: number }>('/api/automation/tasks', data),
    update: (id: number, data: Record<string, string | number | boolean>) => api.post<{ ok: boolean }>(`/api/automation/tasks/${id}`, data),
    delete: (id: number) => api.post<{ ok: boolean }>(`/api/automation/tasks/${id}/delete`, {}),
    toggle: (id: number, enabled: boolean) => api.post<{ ok: boolean }>(`/api/automation/tasks/${id}/toggle`, { enabled }),
    trigger: (id: number) => api.post<{ ok: boolean }>(`/api/automation/tasks/${id}/trigger`, {}),
    clone: (id: number) => api.post<{ ok: boolean; id: number }>(`/api/automation/tasks/${id}/clone`, {}),
    logs: (taskId: number, limit = 20) => api.get<unknown[]>(`/api/automation/tasks/${taskId}/logs?limit=${limit}`),
    stats: () => api.get<Record<string, unknown>>('/api/automation/stats'),
  },
}
