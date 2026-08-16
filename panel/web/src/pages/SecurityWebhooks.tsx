import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'

export default function SecurityWebhooks() {
  const { data, loading, error } = useApi(() => PanelAPI.governance.webhooks(), [], { interval: 15000 })

  if (loading && !data) {
    return (
      <PageContainer title="告警通道" subtitle="Webhook 告警推送配置与投递记录">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="告警通道"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const d = data as Record<string, unknown>
  const webhookUrl = String(d.url ?? d.webhook_url ?? d.webhookUrl ?? '未配置')
  const minSeverity = String(d.minSeverity ?? d.min_severity ?? 'MEDIUM')
  const enabled = Boolean(d.enabled ?? true)
  const dispatches = (d.dispatches ?? d.recent ?? d.recentDispatches ?? []) as Record<string, unknown>[]
  const totalSent = Number(d.totalSent ?? d.total_sent ?? dispatches.length)
  const totalFailed = Number(d.totalFailed ?? d.total_failed ?? 0)

  const sevMap: Record<string, 'ok' | 'warn' | 'danger' | 'info'> = {
    LOW: 'ok', MEDIUM: 'warn', HIGH: 'danger', CRITICAL: 'danger',
  }

  const dispatchColumns = [
    { key: 'ts', header: '时间', className: 'mono', render: (r: Record<string, unknown>) => String(r.ts ?? r.timestamp ?? '—') },
    {
      key: 'severity', header: '级别',
      render: (r: Record<string, unknown>) => {
        const sev = String(r.severity ?? '—')
        return <span className={`sev-${sev}`}>{sev}</span>
      },
    },
    { key: 'title', header: '标题', render: (r: Record<string, unknown>) => String(r.title ?? r.alert_title ?? '—') },
    { key: 'url', header: '目标 URL', className: 'mono', render: (r: Record<string, unknown>) => String(r.url ?? webhookUrl ?? '—') },
    {
      key: 'status', header: '状态',
      render: (r: Record<string, unknown>) => {
        const ok = Boolean(r.success ?? r.ok)
        return <Badge type={ok ? 'ok' : 'danger'}>{ok ? '成功' : '失败'}</Badge>
      },
    },
    { key: 'response_code', header: 'HTTP 状态', render: (r: Record<string, unknown>) => String(r.response_code ?? r.responseCode ?? r.status_code ?? '—') },
    { key: 'error', header: '错误信息', render: (r: Record<string, unknown>) => String(r.error ?? r.message ?? '—') },
  ]

  return (
    <PageContainer title="告警通道" subtitle="Webhook 告警推送配置与投递记录">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="通道状态" value={enabled ? '启用' : '停用'} color={enabled ? 'emerald' : 'rose'} />
        <StatCard label="最低告警级别" value={minSeverity} color={sevMap[minSeverity] === 'danger' ? 'rose' : sevMap[minSeverity] === 'warn' ? 'amber' : 'emerald'} />
        <StatCard label="已推送" value={totalSent} color="cyan" />
        <StatCard label="推送失败" value={totalFailed} color={totalFailed > 0 ? 'rose' : 'emerald'} />
      </div>

      <Card title="Webhook 配置" style={{ marginBottom: 20 }}>
        <div style={{ display: 'grid', gap: 16 }}>
          <div style={{ padding: '14px 16px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">Webhook URL</div>
            <div className="mono" style={{ fontSize: 13, color: 'var(--accent-cyan)', wordBreak: 'break-all' }}>
              {webhookUrl}
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div style={{ padding: '14px 16px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">最低告警级别</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Badge type={sevMap[minSeverity] || 'info'}>{minSeverity}</Badge>
                <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>仅推送此级别及以上的告警</span>
              </div>
            </div>
            <div style={{ padding: '14px 16px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">启用状态</div>
              <Badge type={enabled ? 'ok' : 'danger'}>{enabled ? '已启用' : '已停用'}</Badge>
            </div>
          </div>
        </div>
      </Card>

      <Card title="最近投递记录">
        <DataTable
          columns={dispatchColumns}
          data={dispatches}
          emptyMessage="暂无投递记录"
          rowKey={(r, i) => String(r.id ?? i)}
        />
      </Card>
    </PageContainer>
  )
}
