// ═══════════════════════════════════════════════════════════
// ES+ Panel — API Type Definitions
// ═══════════════════════════════════════════════════════════

export interface RuntimeSnapshot {
  tps_approx?: number
  tps?: number
  mspt_ms?: number
  uptime_seconds?: number
  max_players?: number
  motd?: string
  server_name?: string
  server_id?: string
  difficulty?: string
  gamemode?: string
  world_time?: number
  weather?: string
  whitelist_on?: boolean
  pvp?: boolean
  world_border_size?: number
  idle_timeout?: number
  [k: string]: unknown
}

export interface PerfSample {
  tps: number
  tps_pct: number
  mspt_ms: number
  ts: string
  [k: string]: unknown
}

export interface DashboardData {
  events24h: number
  alertsOpen: number
  traces: number
  movements24h: number
  audit24h: number
  usersCount: number
  onlineCount: number
  pendingActions: number
  failedActions: number
  runtime: RuntimeSnapshot
  recentEvents: GameEvent[]
  recentAlerts: Alert[]
  recentAudit: AuditLog[]
  schedules: Schedule[]
  tpsSamples: PerfSample[]
}

export interface GameEvent {
  event_id: number
  ts: string
  category: string
  action: string
  actor_name: string
  item_id?: string
  trace_id?: string
  detail: string
  [k: string]: unknown
}

export interface Alert {
  alert_id: number
  ts: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'CRITICAL'
  title: string
  message: string
  actor_name: string
  acknowledged: number
  rule_code?: string
  related_event_id?: number
  [k: string]: unknown
}

export interface AuditLog {
  id: number
  ts: string
  uuid: string
  action: string
  detail: string
  success: number
  [k: string]: unknown
}

export interface OnlinePlayer {
  uuid: string
  name: string
  display_name?: string
  health?: number
  food?: number
  saturation?: number
  level?: number
  gamemode?: string
  dimension?: string
  x?: number
  y?: number
  z?: number
  ping?: number
  ip?: string
  [k: string]: unknown
}

export interface UserSummary {
  uuid: string
  name: string
  role: string
  op_bound: number
  created_at: string
  updated_at: string
  failed_attempts: number
  locked_until?: number
  [k: string]: unknown
}

export interface Schedule {
  id: number
  note?: string
  payload?: string
  interval_seconds: number
  next_run_at: string
  enabled: number
  kind?: string
  [k: string]: unknown
}

export interface ServerLog {
  id: number
  ts: string
  level: string
  logger?: string
  message: string
  [k: string]: unknown
}

export interface Gamerule {
  rule_id: string
  value: string
  category?: string
  [k: string]: unknown
}

export interface Dimension {
  key: string
  name?: string
  chunk_count?: number
  entity_count?: number
  loaded_chunks?: number
  [k: string]: unknown
}

export interface EntityType {
  type: string
  count: number
  dimension?: string
  [k: string]: unknown
}

export interface Bossbar {
  id: string
  name: string
  color: string
  value: number
  max: number
  visible: boolean
  [k: string]: unknown
}

export interface ChatFilterWord {
  id: number
  word: string
  enabled: number
  [k: string]: unknown
}

export interface Mute {
  key: string
  player: string
  uuid: string
  expires_at?: string
  reason?: string
  [k: string]: unknown
}

export interface ScoreboardObjective {
  name: string
  criteria: string
  display_name?: string
}

export interface Team {
  name: string
  display_name?: string
  color: string
  friendly_fire: boolean
  members?: string[]
  [k: string]: unknown
}

export interface PanelAction {
  id: number
  action: string
  target_uuid?: string
  target_name?: string
  status: string
  result?: string
  created_at: string
  processed_at?: string
  params?: string
  [k: string]: unknown
}

export interface MovementSample {
  ts: string
  dimension: string
  x: number
  y: number
  z: number
  [k: string]: unknown
}

export interface ItemTraceLink {
  ts: string
  action: string
  actor_name: string
  actorName?: string
  detail: string
  [k: string]: unknown
}

export interface ItemTraceData {
  traceId: string
  itemTrace?: unknown
  trace?: unknown
  itemLinks?: ItemTraceLink[]
  links?: ItemTraceLink[]
  graph?: { nodes: unknown[]; edges: unknown[] }
}

export interface IncidentData {
  seed?: unknown
  events: GameEvent[]
  movements: MovementSample[]
}

export interface Approval {
  id: number
  action: string
  payload?: string
  status: string
  requested_by?: string
  created_at: string
  decided_by?: string
  decided_at?: string
  [k: string]: unknown
}

export interface Snapshot {
  id: number
  label?: string
  created_at: string
  source?: string
  [k: string]: unknown
}

export interface ConfigRevision {
  id: number
  rule_id: string
  old_value: string
  new_value: string
  changed_by?: string
  ts: string
}

export interface AutomationTask {
  id: number
  name: string
  description?: string
  trigger_type: string
  trigger_interval_secs?: number
  trigger_cron?: string
  enabled: boolean
  nodes?: AutomationNode[]
  operations?: AutomationOp[]
  [k: string]: unknown
}

export interface AutomationNode {
  id: number
  name: string
  task_id: number
  [k: string]: unknown
}

export interface AutomationOp {
  id: number
  node_id: number
  action_type: string
  params?: string
  enabled: boolean
  [k: string]: unknown
}

export interface ApiResult<T> {
  ok: boolean
  data?: T
  error?: string
}
