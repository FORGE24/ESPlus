import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'

export default function SecurityRisk() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.governance.riskPage(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  const handleRecompute = async () => {
    setBusy(true)
    try {
      await PanelAPI.governance.recomputeRisk()
      notify('success', '风险评分已重新计算')
      refetch()
    } catch (e) {
      notify('error', `重新计算失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="风险评分" subtitle="管理员行为风险分析与评分">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="风险评分"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const d = data as Record<string, unknown>
  const scores = (d.scores ?? d.risks ?? []) as Record<string, unknown>[]
  const avgScore = Number(d.avgScore ?? d.avg_score ?? 0)
  const highRisk = Number(d.highRiskCount ?? d.high_risk_count ?? 0)
  const totalUsers = scores.length

  const riskColumns = [
    { key: 'name', header: '管理员', render: (r: Record<string, unknown>) => String(r.name ?? r.user ?? r.uuid ?? '—') },
    { key: 'role', header: '角色', render: (r: Record<string, unknown>) => <Badge type="info">{String(r.role ?? '—')}</Badge> },
    {
      key: 'score', header: '风险评分',
      render: (r: Record<string, unknown>) => {
        const s = Number(r.score ?? r.risk_score ?? 0)
        const color = s >= 70 ? 'var(--accent-rose)' : s >= 40 ? 'var(--accent-amber)' : 'var(--accent-emerald)'
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 16, fontWeight: 700, color }}>{s}</span>
            <div style={{ flex: 1, maxWidth: 100, height: 6, background: 'var(--bg-700)', borderRadius: 3, overflow: 'hidden' }}>
              <div style={{ width: `${Math.min(s, 100)}%`, height: '100%', background: color, borderRadius: 3 }} />
            </div>
          </div>
        )
      },
    },
    { key: 'level', header: '风险等级', render: (r: Record<string, unknown>) => {
      const s = Number(r.score ?? r.risk_score ?? 0)
      return <Badge type={s >= 70 ? 'danger' : s >= 40 ? 'warn' : 'ok'}>{s >= 70 ? '高危' : s >= 40 ? '中危' : '低危'}</Badge>
    }},
    { key: 'factors', header: '风险因子', render: (r: Record<string, unknown>) => String(r.factors ?? r.reason ?? r.detail ?? '—') },
    { key: 'lastUpdated', header: '更新时间', className: 'mono', render: (r: Record<string, unknown>) => String(r.lastUpdated ?? r.updated_at ?? r.ts ?? '—') },
  ]

  return (
    <PageContainer
      title="风险评分"
      subtitle="管理员行为风险分析与评分"
      actions={
        <button className="btn btn-primary" disabled={busy} onClick={handleRecompute}>
          {busy ? '计算中…' : '重新计算风险'}
        </button>
      }
    >
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="评估管理员数" value={totalUsers} color="cyan" />
        <StatCard label="平均风险评分" value={avgScore} decimals={1} color={avgScore >= 50 ? 'amber' : 'emerald'} />
        <StatCard label="高危管理员" value={highRisk} color={highRisk > 0 ? 'rose' : 'emerald'} />
      </div>

      <Card title="风险评分明细">
        <DataTable
          columns={riskColumns}
          data={scores}
          emptyMessage="暂无风险评分数据"
          rowKey={(r, i) => String(r.uuid ?? r.id ?? i)}
        />
      </Card>
    </PageContainer>
  )
}
