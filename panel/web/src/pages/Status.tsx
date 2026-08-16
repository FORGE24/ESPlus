import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { RuntimeSnapshot, DashboardData } from '../lib/types'

const DIM_LABELS: Record<string, string> = {
  'minecraft:overworld': '主世界',
  'minecraft:the_nether': '下界',
  'minecraft:the_end': '末地',
}

function formatUptime(seconds?: number): string {
  if (!seconds || seconds <= 0) return '—'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (d > 0) return `${d}天 ${h}小时 ${m}分`
  if (h > 0) return `${h}小时 ${m}分 ${s}秒`
  if (m > 0) return `${m}分 ${s}秒`
  return `${s}秒`
}

const RUNTIME_LABELS: Record<string, string> = {
  server_name: '服务器名称',
  server_id: '服务器 ID',
  motd: 'MOTD',
  tps: 'TPS',
  tps_approx: 'TPS（近似）',
  mspt_ms: 'MSPT (毫秒)',
  uptime_seconds: '运行时长',
  max_players: '最大玩家数',
  difficulty: '难度',
  gamemode: '默认模式',
  world_time: '世界时间',
  weather: '天气',
  whitelist_on: '白名单',
  pvp: 'PVP',
  world_border_size: '世界边界',
  idle_timeout: '挂机超时',
}

const SKIP_RUNTIME_KEYS = new Set(['tps', 'tps_approx', 'mspt_ms', 'uptime_seconds', 'motd'])

function formatRuntimeValue(key: string, val: unknown): string {
  if (val === null || val === undefined) return '—'
  if (typeof val === 'boolean') return val ? '是' : '否'
  if (key === 'uptime_seconds') return formatUptime(Number(val))
  if (key === 'world_time') return String(Math.round(Number(val)))
  if (key === 'world_border_size') return `${Math.round(Number(val))} 格`
  if (key === 'idle_timeout') return `${val} 分钟`
  if (typeof val === 'string' && DIM_LABELS[val]) return DIM_LABELS[val]
  return String(val)
}

export default function Status() {
  const { data: runtime, loading: rtLoading, error: rtError } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 15000 },
  )
  const { data: dash, loading: dashLoading, error: dashError } = useApi<DashboardData>(
    () => PanelAPI.dashboard(),
    [],
    { interval: 15000 },
  )

  const loading = rtLoading && !runtime
  const error = rtError || dashError

  if (loading) {
    return (
      <PageContainer title="服务总览" subtitle="服务器运行状态 · 配置 · 概览">
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer title="服务总览">
        <div className="flash-err">{error}</div>
      </PageContainer>
    )
  }

  if (!runtime) return null

  const tps = runtime.tps_approx ?? runtime.tps ?? 0
  const tpsColor = tps > 18 ? 'emerald' : tps > 15 ? 'amber' : 'rose'
  const motd = runtime.motd || (dash?.runtime?.motd) || '—'
  const onlineCount = dash?.onlineCount ?? 0
  const maxPlayers = runtime.max_players ?? 0

  const stats = [
    { label: 'TPS', value: tps, decimals: 2, color: tpsColor, suffix: '/20' },
    { label: 'MSPT', value: runtime.mspt_ms ?? 0, decimals: 1, color: 'amber', suffix: 'ms' },
    { label: '在线玩家', value: onlineCount, color: 'cyan', suffix: maxPlayers ? `/${maxPlayers}` : '' },
    { label: '运行天数', value: Math.floor((runtime.uptime_seconds ?? 0) / 86400), color: 'violet', suffix: '天' },
    { label: '待处理动作', value: dash?.pendingActions ?? 0, color: 'amber' },
    { label: '24h 事件', value: dash?.events24h ?? 0, color: 'cyan' },
    { label: '未确认告警', value: dash?.alertsOpen ?? 0, color: (dash?.alertsOpen ?? 0) > 0 ? 'rose' : 'emerald' },
    { label: '24h 审计', value: dash?.audit24h ?? 0, color: 'violet' },
  ]

  const runtimeEntries = Object.entries(runtime).filter(
    ([k]) => !SKIP_RUNTIME_KEYS.has(k),
  )

  return (
    <PageContainer title="服务总览" subtitle="服务器运行状态 · 运行时配置 · 全局概览">
      {/* Stats */}
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        {stats.map((s) => (
          <StatCard key={s.label} label={s.label} value={s.value} decimals={s.decimals} color={s.color} suffix={s.suffix} />
        ))}
      </div>

      {/* MOTD + uptime highlight */}
      <Card style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 20, flexWrap: 'wrap' }}>
          <div style={{ flex: '1 1 300px' }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-400)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              MOTD（服务器描述）
            </div>
            <div style={{ fontSize: 16, color: 'var(--ink-100)', marginTop: 6, fontFamily: 'JetBrains Mono, monospace' }}>
              {motd}
            </div>
          </div>
          <div style={{ flex: '0 0 auto' }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-400)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              运行时长
            </div>
            <div style={{ fontSize: 16, color: 'var(--accent-emerald)', marginTop: 6, fontWeight: 600 }}>
              {formatUptime(runtime.uptime_seconds)}
            </div>
          </div>
          <div style={{ flex: '0 0 auto', display: 'flex', gap: 8 }}>
            {runtime.pvp !== undefined && (
              <Badge type={runtime.pvp ? 'danger' : 'ok'}>{runtime.pvp ? 'PVP 开启' : 'PVP 关闭'}</Badge>
            )}
            {runtime.whitelist_on !== undefined && (
              <Badge type={runtime.whitelist_on ? 'warn' : 'ok'}>{runtime.whitelist_on ? '白名单开启' : '白名单关闭'}</Badge>
            )}
          </div>
        </div>
      </Card>

      {/* Runtime config grid */}
      <Card title="运行时配置">
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
          gap: 12,
        }}>
          {runtimeEntries.map(([key, val]) => (
            <div key={key} style={{
              padding: '12px 16px',
              background: 'var(--bg-700)',
              borderRadius: 6,
              border: '1px solid var(--glass-border)',
            }}>
              <div style={{
                fontSize: 11, fontWeight: 600, color: 'var(--ink-400)',
                textTransform: 'uppercase', letterSpacing: '0.05em',
              }}>
                {RUNTIME_LABELS[key] || key}
              </div>
              <div style={{
                fontSize: 14, marginTop: 4, color: 'var(--ink-100)',
                wordBreak: 'break-all',
              }}>
                {formatRuntimeValue(key, val)}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </PageContainer>
  )
}
