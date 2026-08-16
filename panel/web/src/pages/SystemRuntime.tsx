import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { RuntimeSnapshot } from '../lib/types'

export default function SystemRuntime() {
  const { data, loading, error } = useApi(() => PanelAPI.runtime(), [], { interval: 15000 })

  if (loading && !data) {
    return (
      <PageContainer title="运行时配置" subtitle="服务器运行时参数与状态">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="运行时配置"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const rt = data as RuntimeSnapshot & Record<string, unknown>

  const panelBind = String(rt.panel_bind ?? rt.panelBind ?? '0.0.0.0')
  const panelPort = String(rt.panel_port ?? rt.panelPort ?? rt.port ?? '—')
  const sshEnabled = Boolean(rt.ssh_enabled ?? rt.sshEnabled ?? true)
  const sshPort = String(rt.ssh_port ?? rt.sshPort ?? '22')
  const securityReady = Boolean(rt.security_ready ?? rt.securityReady ?? true)
  const tps = rt.tps_approx ?? rt.tps
  const mspt = rt.mspt_ms

  // System-level keys
  const systemKeys = [
    'server_name', 'server_id', 'difficulty', 'gamemode', 'max_players',
    'world_time', 'weather', 'whitelist_on', 'pvp', 'world_border_size',
    'idle_timeout', 'motd', 'uptime_seconds',
  ]

  // Panel/security keys
  const panelKeys = Object.keys(rt).filter((k) =>
    !systemKeys.includes(k) &&
    !['tps_approx', 'tps', 'mspt_ms'].includes(k) &&
    rt[k] !== null &&
    rt[k] !== undefined &&
    typeof rt[k] !== 'object'
  )

  return (
    <PageContainer title="运行时配置" subtitle="服务器运行时参数与状态">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="TPS" value={tps || 0} decimals={1} suffix="/20" color={Number(tps) > 18 ? 'emerald' : Number(tps) > 15 ? 'amber' : 'rose'} />
        <StatCard label="MSPT" value={mspt || 0} decimals={1} suffix="ms" color="cyan" />
        <StatCard label="在线人数上限" value={rt.max_players || 0} color="violet" />
        <StatCard label="安全状态" value={securityReady ? '就绪' : '未就绪'} color={securityReady ? 'emerald' : 'rose'} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="面板连接">
          <div style={{ display: 'grid', gap: 12 }}>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">绑定地址</div>
              <div className="mono" style={{ fontSize: 14, color: 'var(--accent-cyan)' }}>{panelBind}</div>
            </div>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">面板端口</div>
              <div className="mono" style={{ fontSize: 14, color: 'var(--accent-cyan)' }}>{panelPort}</div>
            </div>
          </div>
        </Card>

        <Card title="SSH 与安全">
          <div style={{ display: 'grid', gap: 12 }}>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">SSH</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Badge type={sshEnabled ? 'ok' : 'danger'}>{sshEnabled ? '已启用' : '已禁用'}</Badge>
                <span className="mono" style={{ fontSize: 13, color: 'var(--ink-300)' }}>端口 {sshPort}</span>
              </div>
            </div>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">安全套件</div>
              <Badge type={securityReady ? 'ok' : 'danger'}>{securityReady ? '安全就绪' : '未就绪'}</Badge>
            </div>
          </div>
        </Card>
      </div>

      <Card title="服务器运行参数" style={{ marginBottom: 20 }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 12,
        }}>
          {systemKeys.map((k) => {
            const val = rt[k]
            if (val === null || val === undefined) return null
            return (
              <div key={k} style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--glass-border)' }}>
                <div className="label">{k.replace(/_/g, ' ')}</div>
                <div className="mono" style={{ fontSize: 13, color: 'var(--accent-cyan)', wordBreak: 'break-all' }}>
                  {typeof val === 'boolean' ? (val ? '是' : '否') : String(val)}
                </div>
              </div>
            )
          })}
        </div>
      </Card>

      <Card title="面板与安全配置">
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 12,
        }}>
          {panelKeys.map((k) => {
            const val = rt[k]
            if (val === null || val === undefined) return null
            return (
              <div key={k} style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--glass-border)' }}>
                <div className="label">{k.replace(/_/g, ' ')}</div>
                <div className="mono" style={{ fontSize: 13, color: 'var(--ink-300)', wordBreak: 'break-all' }}>
                  {typeof val === 'boolean' ? (val ? '是' : '否') : String(val)}
                </div>
              </div>
            )
          })}
        </div>
      </Card>
    </PageContainer>
  )
}
