import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Alert } from '../lib/types'

const SEVERITY_BADGE_TYPE: Record<string, 'danger' | 'warn' | 'ok' | 'info'> = {
  CRITICAL: 'danger',
  HIGH: 'danger',
  MEDIUM: 'warn',
  LOW: 'ok',
}

const SEVERITY_LABEL: Record<string, string> = {
  CRITICAL: '严重',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

export default function Alerts() {
  const { notify } = useToast()
  const [showOpen, setShowOpen] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)

  const { data, loading, error, refetch } = useApi(
    () => PanelAPI.alerts(showOpen),
    [showOpen],
    { interval: 15000 },
  )

  const alerts = data ?? []

  async function handleAck(alertId: number) {
    setBusyId(alertId)
    try {
      await PanelAPI.ackAlert(alertId)
      notify('success', '告警已确认')
      refetch()
    } catch (e) {
      notify('error', `确认失败: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusyId(null)
    }
  }

  const stats = {
    critical: alerts.filter((a) => a.severity === 'CRITICAL' || a.severity === 'HIGH').length,
    medium: alerts.filter((a) => a.severity === 'MEDIUM').length,
    low: alerts.filter((a) => a.severity === 'LOW').length,
    total: alerts.length,
  }

  const columns = [
    { key: 'ts', header: '时间', className: 'mono' as const },
    {
      key: 'severity', header: '级别', render: (a: any) => (
        <Badge type={SEVERITY_BADGE_TYPE[a.severity] || 'info'}>
          {SEVERITY_LABEL[a.severity] || a.severity}
        </Badge>
      ),
    },
    {
      key: 'title', header: '标题', render: (a: any) => (
        <span style={{ fontWeight: 600, color: 'var(--ink-100)' }}>{a.title}</span>
      ),
    },
    {
      key: 'message', header: '内容', render: (a: any) => (
        <span style={{ fontSize: 12, color: 'var(--ink-300)', maxWidth: 400, display: 'block' }}>
          {a.message}
        </span>
      ),
    },
    {
      key: 'actor_name', header: '关联玩家', render: (a: any) => (
        <span style={{ color: 'var(--accent-cyan)' }}>{a.actor_name || '—'}</span>
      ),
    },
    {
      key: 'rule_code', header: '规则', render: (a: any) => (
        <span className="mono" style={{ fontSize: 11, color: 'var(--ink-500)' }}>
          {a.rule_code || '—'}
        </span>
      ),
    },
    {
      key: 'actions', header: '操作', render: (a: any) => (
        <div style={{ display: 'flex', gap: 6 }}>
          {showOpen ? (
            <button
              className="btn btn-primary btn-sm"
              disabled={busyId === a.alert_id}
              onClick={() => handleAck(a.alert_id)}
            >
              {busyId === a.alert_id ? '处理中…' : '确认'}
            </button>
          ) : (
            <Badge type="ok">已确认</Badge>
          )}
          {a.related_event_id && (
            <a
              href={`/incident/${a.related_event_id}`}
              className="btn btn-ghost btn-sm"
              style={{ textDecoration: 'none' }}
            >
              查看事件
            </a>
          )}
        </div>
      ),
    },
  ]

  return (
    <PageContainer
      title="异常告警"
      subtitle="安全规则触发的告警 · 可确认 / 查看 / 切换状态"
      actions={
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            className={`btn btn-sm ${showOpen ? 'btn-primary' : 'btn-ghost'}`}
            onClick={() => setShowOpen(true)}
          >
            未处理
          </button>
          <button
            className={`btn btn-sm ${!showOpen ? 'btn-primary' : 'btn-ghost'}`}
            onClick={() => setShowOpen(false)}
          >
            已确认
          </button>
        </div>
      }
    >
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="告警总数" value={stats.total} color={showOpen ? 'rose' : 'cyan'} />
        <StatCard label="严重 / 高" value={stats.critical} color="rose" />
        <StatCard label="中" value={stats.medium} color="amber" />
        <StatCard label="低" value={stats.low} color="emerald" />
      </div>

      {loading && !data ? (
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      ) : error ? (
        <div className="flash-err">{error}</div>
      ) : (
        <Card title={showOpen ? '未处理告警' : '已确认告警'}>
          <DataTable
            columns={columns}
            data={alerts as unknown as Record<string, unknown>[]}
            emptyMessage={showOpen ? '暂无未处理告警' : '暂无已确认告警'}
            rowKey={(a) => (a as unknown as Alert).alert_id}
          />
        </Card>
      )}
    </PageContainer>
  )
}
