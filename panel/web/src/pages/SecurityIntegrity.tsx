import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'

export default function SecurityIntegrity() {
  const { data, loading, error } = useApi(() => PanelAPI.governance.integrity(), [], { interval: 15000 })

  if (loading && !data) {
    return (
      <PageContainer title="审计完整性" subtitle="哈希链验证与审计数据完整性检查">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="审计完整性"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const d = data as Record<string, unknown>
  const chainValid = Boolean(d.chainValid ?? d.chain_valid ?? d.valid ?? true)
  const totalEntries = Number(d.totalEntries ?? d.total_entries ?? 0)
  const verifiedEntries = Number(d.verifiedEntries ?? d.verified_entries ?? 0)
  const brokenLinks = Number(d.brokenLinks ?? d.broken_links ?? 0)
  const lastVerified = String(d.lastVerified ?? d.last_verified ?? d.verified_at ?? '—')
  const issues = (d.issues ?? d.violations ?? []) as Record<string, unknown>[]
  const headHash = String(d.headHash ?? d.head_hash ?? '—')
  const chainDepth = Number(d.chainDepth ?? d.chain_depth ?? 0)

  const issueColumns = [
    { key: 'ts', header: '时间', className: 'mono', render: (r: Record<string, unknown>) => String(r.ts ?? r.timestamp ?? '—') },
    { key: 'entry_id', header: '条目 ID', render: (r: Record<string, unknown>) => String(r.entry_id ?? r.id ?? '—') },
    { key: 'expected_hash', header: '预期哈希', className: 'mono', render: (r: Record<string, unknown>) => String(r.expected_hash ?? r.expectedHash ?? '—') },
    { key: 'actual_hash', header: '实际哈希', className: 'mono', render: (r: Record<string, unknown>) => String(r.actual_hash ?? r.actualHash ?? '—') },
    { key: 'detail', header: '说明', render: (r: Record<string, unknown>) => String(r.detail ?? r.reason ?? r.message ?? '—') },
  ]

  return (
    <PageContainer title="审计完整性" subtitle="哈希链验证与审计数据完整性检查">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="链状态" value={chainValid ? '完整' : '损坏'} color={chainValid ? 'emerald' : 'rose'} />
        <StatCard label="总条目数" value={totalEntries} color="cyan" />
        <StatCard label="已验证" value={verifiedEntries} color="emerald" />
        <StatCard label="断裂链接" value={brokenLinks} color={brokenLinks > 0 ? 'rose' : 'emerald'} />
        <StatCard label="链深度" value={chainDepth} color="violet" />
      </div>

      <Card title="哈希链概况" style={{ marginBottom: 20 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div style={{ padding: '14px 16px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">链头哈希（HEAD）</div>
            <div className="mono" style={{ fontSize: 13, color: 'var(--accent-cyan)', wordBreak: 'break-all' }}>
              {headHash}
            </div>
          </div>
          <div style={{ padding: '14px 16px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">上次验证时间</div>
            <div className="mono" style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              {lastVerified}
            </div>
          </div>
        </div>
        <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
          <Badge type={chainValid ? 'ok' : 'danger'}>
            {chainValid ? '验证通过' : '验证失败'}
          </Badge>
          <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>
            {chainValid
              ? '审计日志哈希链完整，未检测到篡改。'
              : '审计日志哈希链存在异常，可能存在数据篡改，请立即排查！'}
          </span>
        </div>
      </Card>

      <Card title="完整性问题记录">
        <DataTable
          columns={issueColumns}
          data={issues}
          emptyMessage="未发现完整性问题，审计链验证通过"
          rowKey={(r, i) => String(r.id ?? r.entry_id ?? i)}
        />
      </Card>
    </PageContainer>
  )
}
