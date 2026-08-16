import { useParams, Link } from 'react-router-dom'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { IncidentData, GameEvent, MovementSample } from '../lib/types'

const DIM_LABELS: Record<string, string> = {
  'minecraft:overworld': '主世界',
  'minecraft:the_nether': '下界',
  'minecraft:the_end': '末地',
}

export default function Incident() {
  const { eventId } = useParams<{ eventId: string }>()

  const { data, loading, error } = useApi(
    () => eventId ? PanelAPI.incident(eventId) : Promise.resolve(null),
    [eventId],
  )

  if (!eventId) {
    return (
      <PageContainer title="事件链还原" subtitle="从种子事件还原完整因果链">
        <div className="flash-err">缺少事件 ID</div>
      </PageContainer>
    )
  }

  if (loading && !data) {
    return (
      <PageContainer title="事件链还原" subtitle={`事件 ID: ${eventId}`}>
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer title="事件链还原" subtitle={`事件 ID: ${eventId}`}>
        <div className="flash-err">{error}</div>
      </PageContainer>
    )
  }

  if (!data) return null

  const seed = (data.seed || {}) as Record<string, unknown>
  const events: GameEvent[] = data.events || []
  const movements: MovementSample[] = data.movements || []

  const seedEntries = Object.entries(seed).filter(([, v]) => v !== null && v !== undefined)

  const eventColumns = [
    { key: 'ts', header: '时间', className: 'mono' as const },
    {
      key: 'category', header: '类别', render: (e: any) => (
        <Badge type="info">{e.category}</Badge>
      ),
    },
    {
      key: 'action', header: '动作', render: (e: any) => (
        <span style={{ fontWeight: 500, color: 'var(--ink-100)' }}>{e.action}</span>
      ),
    },
    {
      key: 'actor_name', header: '玩家', render: (e: any) => (
        <span style={{ color: 'var(--accent-cyan)' }}>{e.actor_name || '—'}</span>
      ),
    },
    {
      key: 'item_id', header: '物品', render: (e: any) => (
        <span className="mono" style={{ fontSize: 12, color: 'var(--accent-amber)' }}>
          {e.item_id || '—'}
        </span>
      ),
    },
    {
      key: 'detail', header: '详情', render: (e: any) => (
        <span style={{ fontSize: 12, color: 'var(--ink-300)' }}>{e.detail || '—'}</span>
      ),
    },
  ]

  const movementColumns = [
    { key: 'ts', header: '时间', className: 'mono' as const },
    {
      key: 'dimension', header: '维度', render: (m: any) => (
        <span style={{ color: 'var(--accent-violet)', fontSize: 12 }}>
          {DIM_LABELS[m.dimension] || m.dimension || '—'}
        </span>
      ),
    },
    {
      key: 'coords', header: '坐标', render: (m: any) => (
        <span className="mono" style={{ fontSize: 12, color: 'var(--ink-300)' }}>
          {m.x !== undefined ? `${Math.round(m.x)}, ${Math.round(m.y)}, ${Math.round(m.z)}` : '—'}
        </span>
      ),
    },
  ]

  return (
    <PageContainer
      title="事件链还原"
      subtitle={`事件 ID: ${eventId} · 种子事件 → 因果链 → 移动轨迹`}
      actions={
        <Link to="/search" className="btn btn-ghost" style={{ textDecoration: 'none' }}>
          返回搜索
        </Link>
      }
    >
      {/* Stats */}
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="关联事件" value={events.length} color="cyan" />
        <StatCard label="移动记录" value={movements.length} color="violet" />
        <StatCard label="种子事件" value={seedEntries.length > 0 ? 1 : 0} color="amber" />
      </div>

      {/* Seed event */}
      {seedEntries.length > 0 && (
        <Card title="种子事件" style={{ marginBottom: 20 }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
            gap: 12,
          }}>
            {seedEntries.map(([key, val]) => (
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
        </Card>
      )}

      {/* Timeline */}
      <Card title={`事件时间线（${events.length} 条）`} style={{ marginBottom: 20 }}>
        <DataTable
          columns={eventColumns}
          data={events as unknown as Record<string, unknown>[]}
          emptyMessage="暂无关联事件"
          rowKey={(e) => (e as unknown as GameEvent).event_id}
        />
      </Card>

      {/* Movement trajectory */}
      <Card title={`移动轨迹（${movements.length} 条）`}>
        <DataTable
          columns={movementColumns}
          data={movements as unknown as Record<string, unknown>[]}
          emptyMessage="暂无移动记录"
          rowKey={(m, i) => `${(m as unknown as MovementSample).ts}-${i}`}
        />
      </Card>
    </PageContainer>
  )
}
