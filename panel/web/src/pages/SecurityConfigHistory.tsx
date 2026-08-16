import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { ConfigRevision } from '../lib/types'

export default function SecurityConfigHistory() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.governance.configHistory(), [], { interval: 15000 })
  const [busy, setBusy] = useState<number | null>(null)

  const handleRollback = async (id: number, ruleId: string) => {
    if (!window.confirm(`确认将配置项 "${ruleId}" 回滚到修订 #${id} 的值？此操作不可撤销。`)) return
    setBusy(id)
    try {
      await PanelAPI.governance.rollbackConfig(id)
      notify('success', `配置 "${ruleId}" 已回滚到修订 #${id}`)
      refetch()
    } catch (e) {
      notify('error', `回滚失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(null)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="配置历史" subtitle="游戏规则与配置变更记录 · 支持回滚">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="配置历史"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const revisions = data as ConfigRevision[]

  const columns = [
    { key: 'id', header: '修订 ID' },
    { key: 'rule_id', header: '配置项', className: 'mono' },
    {
      key: 'old_value', header: '旧值',
      render: (r: any) => (
        <span className="mono" style={{ color: 'var(--ink-400)' }}>{String(r.old_value ?? '—')}</span>
      ),
    },
    {
      key: 'new_value', header: '新值',
      render: (r: any) => (
        <span className="mono" style={{ color: 'var(--accent-cyan)' }}>{String(r.new_value ?? '—')}</span>
      ),
    },
    { key: 'changed_by', header: '修改人', render: (r: any) => String(r.changed_by ?? '系统') },
    { key: 'ts', header: '时间', className: 'mono' },
    {
      key: 'actions', header: '操作',
      render: (r: any) => (
        <button
          className="btn btn-ghost btn-sm"
          disabled={busy === r.id}
          onClick={() => handleRollback(r.id, r.rule_id)}
        >
          {busy === r.id ? '回滚中…' : '回滚到此版本'}
        </button>
      ),
    },
  ]

  return (
    <PageContainer title="配置历史" subtitle="游戏规则与配置变更记录 · 支持回滚">
      <Card
        title={`配置修订记录（共 ${revisions.length} 条）`}
        actions={<Badge type="info">仅管理员可回滚</Badge>}
      >
        <DataTable
          columns={columns}
          data={revisions as unknown as Record<string, unknown>[]}
          emptyMessage="暂无配置变更记录"
          rowKey={(r) => (r as unknown as ConfigRevision).id}
        />
      </Card>
    </PageContainer>
  )
}
