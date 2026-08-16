import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import type { RuntimeSnapshot, Dimension, EntityType } from '../lib/types'

export default function Entities() {
  const { data: runtime, loading: rtLoading } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 15000 },
  )

  const { data: dims, loading: dimLoading } = useApi<Dimension[]>(
    () => api.get<Dimension[]>('/api/world/dimensions'),
    [],
    { interval: 15000 },
  )

  const { data: entityTypes, loading: etLoading, error } = useApi<EntityType[]>(
    () => api.get<EntityType[]>('/api/entities'),
    [],
    { interval: 15000 },
  )

  const totalEntities = (dims || []).reduce((sum, d) => sum + (d.entity_count || 0), 0)
  const totalChunks = (dims || []).reduce((sum, d) => sum + (d.chunk_count || d.loaded_chunks || 0), 0)

  const dimColumns = [
    { key: 'key', header: '维度', className: 'mono' },
    {
      key: 'name',
      header: '名称',
      render: (d: Dimension) => {
        const name = d.name || d.key
        if (d.key?.includes('nether') || d.key?.includes('NETHER')) return <Badge type="danger">{name}</Badge>
        if (d.key?.includes('end') || d.key?.includes('THE_END')) return <Badge type="violet">{name}</Badge>
        return <Badge type="ok">{name}</Badge>
      },
    },
    { key: 'chunk_count', header: '区块数', render: (d: Dimension) => (d.chunk_count ?? d.loaded_chunks ?? 0).toLocaleString() },
    { key: 'entity_count', header: '实体数', render: (d: Dimension) => (d.entity_count || 0).toLocaleString() },
  ]

  const entityColumns = [
    { key: 'type', header: '实体类型', className: 'mono' },
    { key: 'count', header: '数量', render: (e: EntityType) => <Badge type={e.count > 100 ? 'warn' : 'info'}>{e.count}</Badge> },
    { key: 'dimension', header: '所在维度' },
  ]

  const loading = rtLoading && !runtime

  return (
    <PageContainer title="实体统计" subtitle="世界实体分布与类型统计">
      {loading ? (
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      ) : (
        <>
          <div className="stat-grid" style={{ marginBottom: 20 }}>
            <StatCard label="TPS 近似" value={Number(runtime?.tps_approx ?? runtime?.tps ?? 0)} decimals={2} color="cyan" suffix="/20" />
            <StatCard label="MSPT" value={Number(runtime?.mspt_ms ?? 0)} decimals={1} color="amber" suffix=" ms" />
            <StatCard label="总实体数" value={totalEntities} color="violet" />
            <StatCard label="总区块数" value={totalChunks} color="emerald" />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
            <Card title="维度分布">
              {dimLoading && !dims ? (
                <div className="shimmer-bg" style={{ height: 120, borderRadius: 10 }} />
              ) : (
                <DataTable
                  columns={dimColumns}
                  data={dims || []}
                  emptyMessage="暂无维度数据"
                  rowKey={(d) => d.key}
                />
              )}
            </Card>

            <Card title="实体类型 Top 列表">
              {etLoading && !entityTypes ? (
                <div className="shimmer-bg" style={{ height: 120, borderRadius: 10 }} />
              ) : error ? (
                <div className="flash-err">{error}</div>
              ) : (
                <DataTable
                  columns={entityColumns}
                  data={(entityTypes || []).slice().sort((a, b) => b.count - a.count)}
                  emptyMessage="暂无实体数据"
                  rowKey={(e, i) => `${e.type}-${i}`}
                />
              )}
            </Card>
          </div>
        </>
      )}
    </PageContainer>
  )
}
