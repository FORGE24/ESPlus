import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Schedule } from '../lib/types'

export default function SystemSchedules() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.schedules(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  const handleCreate = async (formData: Record<string, string | number | boolean>) => {
    const kind = String(formData.kind || 'broadcast')
    const payload = String(formData.payload || '')
    const delaySeconds = Number(formData.delaySeconds || 0)
    const intervalSeconds = Number(formData.intervalSeconds || 0)
    const note = String(formData.note || '')

    if (!payload) {
      notify('error', '请输入任务内容')
      return
    }
    setBusy(true)
    try {
      await PanelAPI.createSchedule({
        kind,
        payload,
        delay_seconds: delaySeconds,
        interval_seconds: intervalSeconds,
        note,
      })
      notify('success', '定时任务已创建')
      refetch()
    } catch (e) {
      notify('error', `创建失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleToggle = async (id: number, enabled: boolean) => {
    setBusy(true)
    try {
      await PanelAPI.toggleSchedule(id, !enabled)
      notify('success', `任务 #${id} 已${enabled ? '停用' : '启用'}`)
      refetch()
    } catch (e) {
      notify('error', `操作失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm(`确认删除定时任务 #${id}？`)) return
    setBusy(true)
    try {
      await PanelAPI.deleteSchedule(id)
      notify('success', `任务 #${id} 已删除`)
      refetch()
    } catch (e) {
      notify('error', `删除失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="任务调度" subtitle="定时任务与周期性操作管理">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="任务调度"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const schedules = data as Schedule[]
  const enabledCount = schedules.filter((s) => s.enabled === 1).length

  const columns = [
    { key: 'id', header: 'ID' },
    { key: 'kind', header: '类型', render: (s: any) => <Badge type="info">{s.kind || 'broadcast'}</Badge> },
    { key: 'note', header: '备注', render: (s: any) => String(s.note ?? '—') },
    { key: 'payload', header: '内容', className: 'mono', render: (s: any) => String(s.payload ?? '—') },
    {
      key: 'interval_seconds', header: '间隔',
      render: (s: any) => {
        const sec = s.interval_seconds
        if (!sec) return '一次性'
        if (sec >= 3600) return `${(sec / 3600).toFixed(1)} 小时`
        if (sec >= 60) return `${(sec / 60).toFixed(1)} 分钟`
        return `${sec} 秒`
      },
    },
    { key: 'next_run_at', header: '下次执行', className: 'mono' },
    {
      key: 'enabled', header: '状态',
      render: (s: any) => <Badge type={s.enabled === 1 ? 'ok' : 'warn'}>{s.enabled === 1 ? '启用' : '停用'}</Badge>,
    },
    {
      key: 'actions', header: '操作',
      render: (s: any) => (
        <div style={{ display: 'flex', gap: 6 }}>
          <button
            className={`btn btn-sm ${s.enabled === 1 ? 'btn-ghost' : 'btn-primary'}`}
            disabled={busy}
            onClick={() => handleToggle(s.id, s.enabled === 1)}
          >
            {s.enabled === 1 ? '停用' : '启用'}
          </button>
          <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => handleDelete(s.id)}>
            删除
          </button>
        </div>
      ),
    },
  ]

  return (
    <PageContainer title="任务调度" subtitle="定时任务与周期性操作管理">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="任务总数" value={schedules.length} color="cyan" />
        <StatCard label="已启用" value={enabledCount} color="emerald" />
        <StatCard label="已停用" value={schedules.length - enabledCount} color="amber" />
      </div>

      <Card title="创建定时任务" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            {
              name: 'kind', label: '任务类型', type: 'select',
              options: [
                { value: 'broadcast', label: '广播消息' },
                { value: 'command', label: '执行命令' },
                { value: 'save', label: '保存世界' },
                { value: 'restart', label: '重启警告' },
              ],
              defaultValue: 'broadcast', width: '100%',
            },
            { name: 'payload', label: '任务内容', placeholder: '如: 消息内容或命令', required: true, width: '100%' },
            { name: 'delaySeconds', label: '延迟执行（秒）', type: 'number', min: 0, defaultValue: 0, width: '100%' },
            { name: 'intervalSeconds', label: '执行间隔（秒，0=一次性）', type: 'number', min: 0, defaultValue: 0, width: '100%' },
            { name: 'note', label: '备注（可选）', placeholder: '如: 每日重启提醒', width: '100%' },
          ]}
          onSubmit={handleCreate}
          submitLabel={busy ? '创建中…' : '创建任务'}
          loading={busy}
        />
      </Card>

      <Card title="定时任务列表">
        <DataTable
          columns={columns}
          data={schedules as unknown as Record<string, unknown>[]}
          emptyMessage="暂无定时任务"
          rowKey={(s) => (s as unknown as Schedule).id}
        />
      </Card>
    </PageContainer>
  )
}
