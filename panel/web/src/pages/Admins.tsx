import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { UserSummary, PanelAction } from '../lib/types'

export default function Admins() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.adminsPage(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  if (loading && !data) {
    return (
      <PageContainer title="设置管理员" subtitle="管理面板用户 · 角色 · OP 绑定">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="设置管理员"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const users: UserSummary[] = data.users || []
  const recentOpActions: PanelAction[] = data.recentOpActions || []

  const doUnlock = async (uuid: string, name: string) => {
    setBusy(true)
    try {
      await PanelAPI.unlockUser(uuid)
      notify('success', `已解锁用户 ${name}`)
      refetch()
    } catch (e) {
      notify('error', `解锁失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const doResetPassword = async (uuid: string, name: string) => {
    setBusy(true)
    try {
      await PanelAPI.resetUserPassword(uuid)
      notify('success', `已重置用户 ${name} 的密码`)
      refetch()
    } catch (e) {
      notify('error', `重置失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const doRoleChange = async (uuid: string, role: string, name: string) => {
    setBusy(true)
    try {
      await PanelAPI.updateUserRole(uuid, role)
      notify('success', `已将 ${name} 的角色改为 ${role}`)
      refetch()
    } catch (e) {
      notify('error', `角色修改失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const doToggleOpBound = async (uuid: string, current: number, name: string) => {
    setBusy(true)
    try {
      await PanelAPI.updateOpBound(uuid, current === 0)
      notify('success', `已${current === 0 ? '启用' : '禁用'} ${name} 的 OP 绑定`)
      refetch()
    } catch (e) {
      notify('error', `操作失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleGrantOp = async (formData: Record<string, string | number | boolean>) => {
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

  const handleRevokeOp = async (formData: Record<string, string | number | boolean>) => {
    setBusy(true)
    try {
      await PanelAPI.revokeOp(String(formData.player || ''), String(formData.uuid || ''))
      notify('success', `已撤销 ${formData.player || formData.uuid} 的 OP 权限`)
      refetch()
    } catch (e) {
      notify('error', `撤销失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const userColumns = [
    { key: 'name', header: '用户名' },
    { key: 'uuid', header: 'UUID', className: 'mono' },
    {
      key: 'role', header: '角色',
      render: (u: any) => {
        const type: 'ok' | 'warn' | 'info' | 'danger' =
          u.role === 'ADMIN' ? 'danger' : u.role === 'MODERATOR' ? 'warn' : 'info'
        return <Badge type={type}>{u.role}</Badge>
      },
    },
    {
      key: 'op_bound', header: 'OP 绑定',
      render: (u: any) => <Badge type={u.op_bound === 1 ? 'ok' : 'info'}>{u.op_bound === 1 ? '已绑定' : '未绑定'}</Badge>,
    },
    {
      key: 'locked', header: '锁定状态',
      render: (u: any) => {
        const isLocked = u.locked_until && u.locked_until > Date.now() / 1000
        return <Badge type={isLocked ? 'danger' : 'ok'}>{isLocked ? '已锁定' : '正常'}</Badge>
      },
    },
    { key: 'failed_attempts', header: '失败次数' },
    {
      key: 'actions', header: '操作',
      render: (u: any) => (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {u.locked_until && u.locked_until > Date.now() / 1000 && (
            <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => doUnlock(u.uuid, u.name)}>解锁</button>
          )}
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => doResetPassword(u.uuid, u.name)}>重置密码</button>
          <select
            className="select"
            style={{ width: 'auto', padding: '4px 8px', fontSize: 12 }}
            value={u.role}
            disabled={busy}
            onChange={(e) => doRoleChange(u.uuid, e.target.value, u.name)}
          >
            {(data.roles || ['ADMIN', 'MODERATOR', 'VIEWER']).map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
          <button
            className={`btn btn-sm ${u.op_bound === 1 ? 'btn-ghost' : 'btn-primary'}`}
            disabled={busy}
            onClick={() => doToggleOpBound(u.uuid, u.op_bound, u.name)}
          >
            {u.op_bound === 1 ? '取消绑定' : '绑定 OP'}
          </button>
        </div>
      ),
    },
  ]

  const opActionColumns = [
    { key: 'created_at', header: '时间', className: 'mono' },
    { key: 'action', header: '动作' },
    { key: 'target_name', header: '目标' },
    { key: 'status', header: '状态', render: (a: any) => <Badge type={a.status === 'DONE' ? 'ok' : a.status === 'FAILED' ? 'danger' : 'warn'}>{a.status}</Badge> },
    { key: 'result', header: '结果' },
  ]

  return (
    <PageContainer title="设置管理员" subtitle="管理面板用户 · 角色 · OP 绑定">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="总用户数" value={users.length} color="cyan" />
        <StatCard label="已锁定" value={data.lockedCount || 0} color={data.lockedCount > 0 ? 'rose' : 'emerald'} />
      </div>

      <Card title="面板用户列表" style={{ marginBottom: 20 }}>
        <DataTable
          columns={userColumns}
          data={users as unknown as Record<string, unknown>[]}
          emptyMessage="暂无用户"
          rowKey={(u) => (u as unknown as UserSummary).uuid}
        />
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="授予 OP 权限">
          <FormBuilder
            fields={[
              { name: 'player', label: '玩家名', placeholder: '输入游戏内玩家名', width: '100%' },
              { name: 'uuid', label: 'UUID（可选）', placeholder: '可选，留空自动匹配', width: '100%' },
            ]}
            onSubmit={handleGrantOp}
            submitLabel="授予 OP"
            loading={busy}
            layout="stack"
          />
        </Card>
        <Card title="撤销 OP 权限">
          <FormBuilder
            fields={[
              { name: 'player', label: '玩家名', placeholder: '输入游戏内玩家名', width: '100%' },
              { name: 'uuid', label: 'UUID（可选）', placeholder: '可选，留空自动匹配', width: '100%' },
            ]}
            onSubmit={handleRevokeOp}
            submitLabel="撤销 OP"
            loading={busy}
            layout="stack"
          />
        </Card>
      </div>

      <Card title="最近 OP 操作记录">
        <DataTable
          columns={opActionColumns}
          data={recentOpActions as unknown as Record<string, unknown>[]}
          emptyMessage="暂无操作记录"
          rowKey={(a) => (a as unknown as PanelAction).id}
        />
      </Card>
    </PageContainer>
  )
}
