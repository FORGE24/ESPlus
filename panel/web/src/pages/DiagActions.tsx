import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { PanelAction } from '../lib/types'

export default function DiagActions() {
  const { data, loading, error } = useApi(() => PanelAPI.actions(), [], { interval: 10000 })

  if (loading && !data) {
    return (
      <PageContainer title="面板动作" subtitle="面板操作诊断与状态跟踪">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="面板动作"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const actions = data as PanelAction[]
  const pendingCount = actions.filter((a) => a.status === 'PENDING' || a.status === 'QUEUED').length
  const doneCount = actions.filter((a) => a.status === 'DONE' || a.status === 'COMPLETED').length
  const failedCount = actions.filter((a) => a.status === 'FAILED' || a.status === 'ERROR').length

  const columns = [
    { key: 'id', header: 'ID' },
    { key: 'action', header: '动作' },
    { key: 'target_name', header: '目标', render: (a: any) => String(a.target_name ?? a.target_uuid ?? '—') },
    {
      key: 'status', header: '状态',
      render: (a: any) => {
        const map: Record<string, 'ok' | 'warn' | 'danger' | 'info'> = {
          PENDING: 'warn', QUEUED: 'warn', PROCESSING: 'info',
          DONE: 'ok', COMPLETED: 'ok',
          FAILED: 'danger', ERROR: 'danger',
        }
        return <Badge type={map[a.status] || 'info'}>{a.status}</Badge>
      },
    },
    { key: 'params', header: '参数', className: 'mono', render: (a: any) => String(a.params ?? '—') },
    { key: 'result', header: '结果', render: (a: any) => String(a.result ?? '—') },
    { key: 'created_at', header: '创建时间', className: 'mono' },
    { key: 'processed_at', header: '处理时间', className: 'mono', render: (a: any) => String(a.processed_at ?? '—') },
  ]

  return (
    <PageContainer title="面板动作" subtitle="面板操作诊断与状态跟踪">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="待处理" value={pendingCount} color={pendingCount > 0 ? 'amber' : 'emerald'} />
        <StatCard label="已完成" value={doneCount} color="emerald" />
        <StatCard label="已失败" value={failedCount} color={failedCount > 0 ? 'rose' : 'emerald'} />
        <StatCard label="总计" value={actions.length} color="cyan" />
      </div>

      <Card title="动作记录">
        <DataTable
          columns={columns}
          data={actions as unknown as Record<string, unknown>[]}
          emptyMessage="暂无动作记录"
          rowKey={(a) => (a as unknown as PanelAction).id}
        />
      </Card>
    </PageContainer>
  )
}
