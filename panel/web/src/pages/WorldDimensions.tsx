import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { api } from '../lib/api'
import type { Dimension } from '../lib/types'

export default function WorldDimensions() {
  const { data: dims, loading, error } = useApi<Dimension[]>(
    () => api.get<Dimension[]>('/api/world/dimensions'),
    [],
    { interval: 15000 },
  )

  const totalChunks = (dims || []).reduce((sum, d) => sum + (d.chunk_count || d.loaded_chunks || 0), 0)
  const totalEntities = (dims || []).reduce((sum, d) => sum + (d.entity_count || 0), 0)

  const columns = [
    { key: 'key', header: '维度 Key', className: 'mono' },
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

  return (
    <PageContainer title="维度总览" subtitle="查看各维度区块与实体统计">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="维度总数" value={dims?.length || 0} color="cyan" />
        <StatCard label="总区块数" value={totalChunks} color="emerald" />
        <StatCard label="总实体数" value={totalEntities} color="amber" />
      </div>

      <Card title="维度列表">
        {loading && !dims ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={columns}
            data={dims || []}
            emptyMessage="暂无维度数据"
            rowKey={(d) => d.key}
          />
        )}
      </Card>
    </PageContainer>
  )
}
