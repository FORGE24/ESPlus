import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { UserSummary } from '../lib/types'

export default function AccessOps() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.adminsPage(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  const handleRevoke = async (player: string, uuid: string) => {
    if (!window.confirm(`确认撤销 ${player} 的 OP 权限？`)) return
    setBusy(true)
    try {
      await PanelAPI.revokeOp(player, uuid)
      notify('success', `已撤销 ${player} 的 OP 权限`)
      refetch()
    } catch (e) {
      notify('error', `撤销失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleGrant = async (formData: Record<string, string | number | boolean>) => {
    setBusy(true)
    try {
      await PanelAPI.grantOp(String(formData.player || ''), String(formData.uuid || ''))
      notify('success', `已授予 ${formData.player || formData.uuid} OP 权限`)
      refetch()
    } catch (e) {
      notify('error', `授权失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="OP 列表" subtitle="服务器管理员（OP）权限管理">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="OP 列表"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const users: UserSummary[] = (data.users || []).filter((u) => u.op_bound === 1)
  const opActions = data.recentOpActions || []

  const columns = [
    { key: 'name', header: '玩家名' },
    { key: 'uuid', header: 'UUID', className: 'mono' },
    {
      key: 'role', header: '面板角色',
      render: (u: any) => {
        const type: 'ok' | 'warn' | 'info' = u.role === 'ADMIN' ? 'danger' as 'ok' : u.role === 'MODERATOR' ? 'warn' : 'info'
        return <Badge type={type as 'ok'}>{u.role}</Badge>
      },
    },
    { key: 'created_at', header: '授权时间', className: 'mono' },
    {
      key: 'actions', header: '操作',
      render: (u: any) => (
        <button
          className="btn btn-danger btn-sm"
          disabled={busy}
          onClick={() => handleRevoke(u.name, u.uuid)}
        >
          {busy ? '处理中…' : '撤销 OP'}
        </button>
      ),
    },
  ]

  const opActionColumns = [
    { key: 'created_at', header: '时间', className: 'mono' },
    { key: 'action', header: '操作' },
    { key: 'target_name', header: '目标玩家' },
    {
      key: 'status', header: '状态',
      render: (r: any) => <Badge type={r.status === 'DONE' ? 'ok' : r.status === 'FAILED' ? 'danger' : 'warn'}>{String(r.status)}</Badge>,
    },
    { key: 'result', header: '结果', render: (r: Record<string, unknown>) => String(r.result ?? '—') },
  ]

  return (
    <PageContainer title="OP 列表" subtitle="服务器管理员（OP）权限管理">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="OP 玩家数" value={users.length} color="amber" />
        <StatCard label="总用户数" value={(data.users || []).length} color="cyan" />
      </div>

      <Card title="授予 OP 权限" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'player', label: '玩家名', placeholder: '输入游戏内玩家名', required: true, width: '100%' },
            { name: 'uuid', label: 'UUID（可选）', placeholder: '可选，留空自动匹配', width: '100%' },
          ]}
          onSubmit={handleGrant}
          submitLabel="授予 OP"
          loading={busy}
          layout="stack"
        />
      </Card>

      <Card title="OP 玩家列表" style={{ marginBottom: 20 }}>
        <DataTable
          columns={columns}
          data={users as unknown as Record<string, unknown>[]}
          emptyMessage="暂无 OP 玩家"
          rowKey={(u) => (u as unknown as UserSummary).uuid}
        />
      </Card>

      <Card title="最近 OP 操作">
        <DataTable
          columns={opActionColumns}
          data={opActions as unknown as Record<string, unknown>[]}
          emptyMessage="暂无操作记录"
          rowKey={(r) => String((r as unknown as { id: number }).id)}
        />
      </Card>
    </PageContainer>
  )
}
