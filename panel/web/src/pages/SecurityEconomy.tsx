import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'

export default function SecurityEconomy() {
  const { data, loading, error } = useApi(() => PanelAPI.governance.economy(), [], { interval: 15000 })

  if (loading && !data) {
    return (
      <PageContainer title="经济审计" subtitle="游戏内经济交易审计与监控">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="经济审计"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const events = (data as unknown[]) as Record<string, unknown>[]
  const totalEvents = events.length
  const totalValue = events.reduce((sum, e) => sum + Number(e.amount ?? e.value ?? 0), 0)
  const flaggedCount = events.filter((e) => Boolean(e.flagged ?? e.suspicious)).length

  const columns = [
    { key: 'ts', header: '时间', className: 'mono', render: (r: Record<string, unknown>) => String(r.ts ?? r.timestamp ?? '—') },
    { key: 'player', header: '玩家', render: (r: Record<string, unknown>) => String(r.player ?? r.player_name ?? r.actor_name ?? '—') },
    { key: 'action', header: '交易类型', render: (r: Record<string, unknown>) => <Badge type="info">{String(r.action ?? r.type ?? '—')}</Badge> },
    { key: 'item', header: '物品', className: 'mono', render: (r: Record<string, unknown>) => String(r.item ?? r.item_id ?? '—') },
    {
      key: 'amount', header: '数量 / 金额',
      render: (r: Record<string, unknown>) => {
        const amt = Number(r.amount ?? r.value ?? 0)
        return <span className="mono" style={{ color: 'var(--accent-amber)', fontWeight: 600 }}>{amt}</span>
      },
    },
    { key: 'target', header: '交易对象', render: (r: Record<string, unknown>) => String(r.target ?? r.counterparty ?? '—') },
    {
      key: 'flagged', header: '标记',
      render: (r: Record<string, unknown>) => {
        const flagged = Boolean(r.flagged ?? r.suspicious)
        return flagged ? <Badge type="danger">异常</Badge> : <Badge type="ok">正常</Badge>
      },
    },
    { key: 'detail', header: '详情', render: (r: Record<string, unknown>) => String(r.detail ?? r.reason ?? '—') },
  ]

  return (
    <PageContainer title="经济审计" subtitle="游戏内经济交易审计与监控">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="交易总数" value={totalEvents} color="cyan" />
        <StatCard label="交易总量" value={totalValue} color="amber" />
        <StatCard label="异常标记" value={flaggedCount} color={flaggedCount > 0 ? 'rose' : 'emerald'} />
      </div>

      <Card title="经济交易记录">
        <DataTable
          columns={columns}
          data={events}
          emptyMessage="暂无经济交易记录"
          rowKey={(r, i) => String(r.id ?? i)}
        />
      </Card>
    </PageContainer>
  )
}
