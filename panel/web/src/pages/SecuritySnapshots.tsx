import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Snapshot } from '../lib/types'

export default function SecuritySnapshots() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.governance.snapshots(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  const handleCreate = async (formData: Record<string, string | number | boolean>) => {
    setBusy(true)
    try {
      await PanelAPI.governance.createSnapshot(String(formData.label || ''))
      notify('success', '安全快照已创建')
      refetch()
    } catch (e) {
      notify('error', `创建失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleRestore = async (id: number, label?: string) => {
    if (!window.confirm(`确认恢复快照 #${id}${label ? `（${label}）` : ''}？当前安全配置将被覆盖。`)) return
    setBusy(true)
    try {
      await PanelAPI.governance.restoreSnapshot(id)
      notify('success', `快照 #${id} 已恢复`)
      refetch()
    } catch (e) {
      notify('error', `恢复失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="安全快照" subtitle="安全配置快照管理 · 创建与恢复">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="安全快照"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const snapshots = data as Snapshot[]

  const columns = [
    { key: 'id', header: '快照 ID' },
    { key: 'label', header: '标签', render: (s: any) => String(s.label || '未命名') },
    { key: 'source', header: '来源', render: (s: any) => <Badge type="info">{String(s.source ?? 'manual')}</Badge> },
    { key: 'created_at', header: '创建时间', className: 'mono' },
    {
      key: 'actions', header: '操作',
      render: (s: any) => (
        <button
          className="btn btn-ghost btn-sm"
          disabled={busy}
          onClick={() => handleRestore(s.id, s.label || undefined)}
        >
          恢复此快照
        </button>
      ),
    },
  ]

  return (
    <PageContainer title="安全快照" subtitle="安全配置快照管理 · 创建与恢复">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="快照总数" value={snapshots.length} color="cyan" />
        <StatCard label="最近快照" value={snapshots[0]?.label || snapshots[0]?.id || '无'} color="violet" />
      </div>

      <Card title="创建新快照" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'label', label: '快照标签', placeholder: '为此次快照添加备注（可选）', width: '100%' },
          ]}
          onSubmit={handleCreate}
          submitLabel="创建快照"
          loading={busy}
          layout="stack"
        />
      </Card>

      <Card title="快照列表">
        <DataTable
          columns={columns}
          data={snapshots as unknown as Record<string, unknown>[]}
          emptyMessage="暂无快照"
          rowKey={(s) => (s as unknown as Snapshot).id}
        />
      </Card>
    </PageContainer>
  )
}
