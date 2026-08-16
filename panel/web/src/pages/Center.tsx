import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'

export default function Center() {
  const { data, loading, error } = useApi(() => PanelAPI.governance.center(), [], { interval: 15000 })

  if (loading && !data) {
    return (
      <PageContainer title="本机 Center" subtitle="本地中心节点信息与统计数据">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="本机 Center"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const d = data as Record<string, unknown>

  // Extract all key-value pairs for display
  const kvEntries = Object.entries(d).filter(([, v]) => {
    if (v === null || v === undefined) return false
    if (Array.isArray(v) || typeof v === 'object') return false
    return true
  })

  const tables = Object.entries(d).filter(([, v]) => Array.isArray(v) && v.length > 0) as [string, Record<string, unknown>[]][]

  const statKeys = ['nodeId', 'node_id', 'nodeName', 'node_name', 'uptime', 'uptime_seconds', 'version', 'build']
  const stats = kvEntries.filter(([k]) => statKeys.some((s) => k.toLowerCase().includes(s.toLowerCase())))

  return (
    <PageContainer title="本机 Center" subtitle="本地中心节点信息与统计数据">
      {stats.length > 0 && (
        <div className="stat-grid" style={{ marginBottom: 20 }}>
          {stats.slice(0, 6).map(([k, v]) => (
            <StatCard key={k} label={k.replace(/_/g, ' ').toUpperCase()} value={String(v)} color="cyan" />
          ))}
        </div>
      )}

      <Card title="节点配置信息" style={{ marginBottom: 20 }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
          gap: 12,
        }}>
          {kvEntries.map(([k, v]) => (
            <div
              key={k}
              style={{
                padding: '12px 14px',
                background: 'var(--bg-700)',
                border: '1px solid var(--glass-border)',
                borderRadius: 'var(--radius-sm)',
              }}
            >
              <div className="label">{k.replace(/_/g, ' ')}</div>
              <div className="mono" style={{ fontSize: 13, color: 'var(--accent-cyan)', wordBreak: 'break-all' }}>
                {String(v)}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {tables.map(([key, rows]) => (
        <Card key={key} title={key.replace(/_/g, ' ')} style={{ marginBottom: 20 }}>
          <DataTable
            columns={Object.keys(rows[0]).slice(0, 8).map((colKey) => ({
              key: colKey,
              header: colKey.replace(/_/g, ' '),
              render: (r: Record<string, unknown>) => {
                const val = r[colKey]
                if (val === null || val === undefined) return '—'
                if (typeof val === 'boolean') return <Badge type={val ? 'ok' : 'danger'}>{val ? '是' : '否'}</Badge>
                return String(val)
              },
            }))}
            data={rows}
            emptyMessage="暂无数据"
            rowKey={(r, i) => String(r.id ?? i)}
          />
        </Card>
      ))}

      {kvEntries.length === 0 && tables.length === 0 && (
        <Card title="信息">
          <p style={{ color: 'var(--ink-500)', fontSize: 14, textAlign: 'center', padding: 20 }}>
            暂无 Center 节点数据
          </p>
        </Card>
      )}
    </PageContainer>
  )
}
