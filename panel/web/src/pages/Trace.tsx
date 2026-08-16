import { useParams, Link } from 'react-router-dom'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { ItemTraceData, ItemTraceLink } from '../lib/types'

export default function Trace() {
  const { traceId } = useParams<{ traceId: string }>()

  const { data, loading, error } = useApi(
    () => traceId ? PanelAPI.trace(traceId) : Promise.resolve(null),
    [traceId],
  )

  if (!traceId) {
    return (
      <PageContainer title="物品溯源" subtitle="查看物品的完整流转链路">
        <div className="flash-err">缺少溯源 ID</div>
      </PageContainer>
    )
  }

  if (loading && !data) {
    return (
      <PageContainer title="物品溯源" subtitle={`溯源 ID: ${traceId}`}>
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer title="物品溯源" subtitle={`溯源 ID: ${traceId}`}>
        <div className="flash-err">{error}</div>
      </PageContainer>
    )
  }

  if (!data) return null

  // Extract trace info and links from the response
  const traceInfo = (data.itemTrace || data.trace || {}) as Record<string, unknown>
  const links: ItemTraceLink[] = (data.itemLinks || data.links || []) as ItemTraceLink[]
  const infoEntries = Object.entries(traceInfo).filter(([, v]) => v !== null && v !== undefined)

  const linkColumns = [
    { key: 'ts', header: '时间', className: 'mono' as const },
    {
      key: 'action', header: '动作', render: (l: any) => (
        <Badge type="info">{l.action}</Badge>
      ),
    },
    {
      key: 'actor_name', header: '玩家', render: (l: any) => (
        <span style={{ color: 'var(--accent-cyan)', fontWeight: 500 }}>
          {l.actor_name || l.actorName || '—'}
        </span>
      ),
    },
    {
      key: 'detail', header: '详情', render: (l: any) => (
        <span style={{ fontSize: 12, color: 'var(--ink-300)' }}>{l.detail || '—'}</span>
      ),
    },
  ]

  return (
    <PageContainer
      title="物品溯源"
      subtitle={`溯源 ID: ${traceId}`}
      actions={
        <Link to="/search" className="btn btn-ghost" style={{ textDecoration: 'none' }}>
          返回搜索
        </Link>
      }
    >
      {/* Trace info card */}
      <Card title="溯源信息" style={{ marginBottom: 20 }}>
        {infoEntries.length > 0 ? (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
            gap: 12,
          }}>
            {infoEntries.map(([key, val]) => (
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
                  {key}
                </div>
                <div style={{
                  fontSize: 14, marginTop: 4, color: 'var(--ink-100)',
                  fontFamily: 'JetBrains Mono, monospace',
                  wordBreak: 'break-all',
                }}>
                  {typeof val === 'object' ? JSON.stringify(val) : String(val)}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '30px 20px', color: 'var(--ink-500)' }}>
            <p style={{ fontSize: 14 }}>暂无溯源元数据</p>
          </div>
        )}
      </Card>

      {/* Link nodes table */}
      <Card title={`流转节点（${links.length} 个）`}>
        <DataTable
          columns={linkColumns}
          data={links as unknown as Record<string, unknown>[]}
          emptyMessage="暂无流转记录"
          rowKey={(l, i) => `${(l as unknown as ItemTraceLink).ts}-${i}`}
        />
      </Card>

      {/* Graph info */}
      {data.graph && (
        <Card title="关系图谱" style={{ marginTop: 20 }}>
          <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
            <div style={{
              padding: '14px 20px', background: 'var(--bg-700)', borderRadius: 6,
              border: '1px solid var(--glass-border)',
            }}>
              <div style={{ fontSize: 11, color: 'var(--ink-400)', textTransform: 'uppercase' }}>节点数</div>
              <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--accent-cyan)', marginTop: 4 }}>
                {Array.isArray((data.graph as any).nodes) ? (data.graph as any).nodes.length : 0}
              </div>
            </div>
            <div style={{
              padding: '14px 20px', background: 'var(--bg-700)', borderRadius: 6,
              border: '1px solid var(--glass-border)',
            }}>
              <div style={{ fontSize: 11, color: 'var(--ink-400)', textTransform: 'uppercase' }}>边数</div>
              <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--accent-violet)', marginTop: 4 }}>
                {Array.isArray((data.graph as any).edges) ? (data.graph as any).edges.length : 0}
              </div>
            </div>
          </div>
        </Card>
      )}
    </PageContainer>
  )
}
