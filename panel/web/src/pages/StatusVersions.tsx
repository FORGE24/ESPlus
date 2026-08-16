import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { RuntimeSnapshot, DashboardData } from '../lib/types'

export default function StatusVersions() {
  const { data: runtime, loading: rtLoading, error: rtError } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 30000 },
  )
  const { data: dash, loading: dashLoading, error: dashError } = useApi<DashboardData>(
    () => PanelAPI.dashboard(),
    [],
    { interval: 30000 },
  )

  const loading = rtLoading && !runtime
  const error = rtError || dashError

  if (loading) {
    return (
      <PageContainer title="模组 / 版本" subtitle="模组版本 · NeoForge · Minecraft · Java · 运行环境">
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer title="模组 / 版本">
        <div className="flash-err">{error}</div>
      </PageContainer>
    )
  }

  if (!runtime) return null

  const mcVersion = String(runtime['mc_version'] ?? runtime['minecraft_version'] ?? '1.21.1')
  const neoforgeVersion = String(runtime['neoforge_version'] ?? runtime['forge_version'] ?? '—')
  const javaVersion = String(runtime['java_version'] ?? '—')
  const modVersion = String(runtime['mod_version'] ?? runtime['es_version'] ?? '—')
  const javaVendor = String(runtime['java_vendor'] ?? '—')
  const osName = String(runtime['os_name'] ?? '—')
  const osArch = String(runtime['os_arch'] ?? '—')
  const cpuCount = Number(runtime['cpu_count'] ?? runtime['available_processors'] ?? 0)
  const serverName = String(runtime.server_name ?? '—')
  const serverId = String(runtime.server_id ?? '—')

  const userCount = dash?.usersCount ?? 0
  const onlineCount = dash?.onlineCount ?? 0
  const audit24h = dash?.audit24h ?? 0

  const stats = [
    { label: '注册用户', value: userCount, color: 'cyan' },
    { label: '在线玩家', value: onlineCount, color: 'emerald' },
    { label: '24h 审计', value: audit24h, color: 'violet' },
    { label: '24h 事件', value: dash?.events24h ?? 0, color: 'amber' },
  ]

  const versionItems = [
    { label: 'Minecraft 版本', value: mcVersion, color: 'var(--accent-emerald)', badge: 'MC' },
    { label: 'NeoForge 版本', value: neoforgeVersion, color: 'var(--accent-cyan)', badge: 'NF' },
    { label: 'ES+ 模组版本', value: modVersion, color: 'var(--accent-violet)', badge: 'ES+' },
    { label: 'Java 版本', value: javaVersion, color: 'var(--accent-amber)', badge: 'JVM' },
  ]

  const envItems = [
    { label: 'Java 供应商', value: javaVendor },
    { label: '操作系统', value: osName },
    { label: '系统架构', value: osArch },
    { label: 'CPU 核心数', value: cpuCount > 0 ? String(cpuCount) : '—' },
    { label: '服务器名称', value: serverName },
    { label: '服务器 ID', value: serverId, mono: true },
  ]

  return (
    <PageContainer title="模组 / 版本" subtitle="模组版本 · NeoForge · Minecraft · Java · 运行环境信息">
      {/* Stats */}
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        {stats.map((s) => (
          <StatCard key={s.label} label={s.label} value={s.value} color={s.color} />
        ))}
      </div>

      {/* Version cards */}
      <Card title="版本信息" style={{ marginBottom: 20 }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
          gap: 14,
        }}>
          {versionItems.map((item) => (
            <div key={item.label} style={{
              padding: '18px 20px',
              background: 'var(--bg-700)',
              borderRadius: 8,
              border: '1px solid var(--glass-border)',
              position: 'relative',
              overflow: 'hidden',
            }}>
              <div style={{
                position: 'absolute', top: 0, left: 0, right: 0, height: 2,
                background: `linear-gradient(90deg, ${item.color}, transparent)`,
              }} />
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <span style={{
                  display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                  width: 32, height: 32, borderRadius: 6,
                  background: `${item.color}20`, color: item.color,
                  fontSize: 11, fontWeight: 700, fontFamily: 'JetBrains Mono, monospace',
                }}>
                  {item.badge}
                </span>
                <div style={{
                  fontSize: 11, fontWeight: 600, color: 'var(--ink-400)',
                  textTransform: 'uppercase', letterSpacing: '0.05em',
                }}>
                  {item.label}
                </div>
              </div>
              <div style={{
                fontSize: 18, fontWeight: 700,
                color: item.color,
                fontFamily: 'JetBrains Mono, monospace',
              }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Environment info */}
      <Card title="运行环境">
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
          gap: 12,
        }}>
          {envItems.map((item) => (
            <div key={item.label} style={{
              padding: '12px 16px',
              background: 'var(--bg-700)',
              borderRadius: 6,
              border: '1px solid var(--glass-border)',
            }}>
              <div style={{
                fontSize: 11, fontWeight: 600, color: 'var(--ink-400)',
                textTransform: 'uppercase', letterSpacing: '0.05em',
              }}>
                {item.label}
              </div>
              <div style={{
                fontSize: 14, marginTop: 4, color: 'var(--ink-100)',
                fontFamily: item.mono ? 'JetBrains Mono, monospace' : 'inherit',
                wordBreak: 'break-all',
              }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Compatibility badges */}
      <Card title="兼容性状态" style={{ marginTop: 20 }}>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Badge type="ok">NeoForge 兼容</Badge>
          <Badge type="ok">Java {javaVersion !== '—' ? javaVersion : '17+'}</Badge>
          <Badge type="info">MC {mcVersion}</Badge>
          {modVersion !== '—' && <Badge type="violet">ES+ {modVersion}</Badge>}
          {runtime.whitelist_on !== undefined && (
            <Badge type={runtime.whitelist_on ? 'warn' : 'ok'}>
              {runtime.whitelist_on ? '白名单已开启' : '白名单已关闭'}
            </Badge>
          )}
          {runtime.pvp !== undefined && (
            <Badge type={runtime.pvp ? 'danger' : 'ok'}>
              {runtime.pvp ? 'PVP 已开启' : 'PVP 已关闭'}
            </Badge>
          )}
        </div>
      </Card>
    </PageContainer>
  )
}
