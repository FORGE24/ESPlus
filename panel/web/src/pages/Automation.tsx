import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { AutomationTask } from '../lib/types'

export default function Automation() {
  const { notify } = useToast()
  const navigate = useNavigate()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.automation.list(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  const handleCreate = async (formData: Record<string, string | number | boolean>) => {
    setBusy(true)
    try {
      const result = await PanelAPI.automation.create({
        name: String(formData.name || ''),
        description: String(formData.description || ''),
        trigger_type: String(formData.trigger_type || 'interval'),
        trigger_interval_secs: Number(formData.trigger_interval_secs || 60),
      })
      notify('success', `自动化任务已创建（ID: ${result.id}）`)
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
      await PanelAPI.automation.toggle(id, !enabled)
      notify('success', `任务 #${id} 已${enabled ? '停用' : '启用'}`)
      refetch()
    } catch (e) {
      notify('error', `操作失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleTrigger = async (id: number) => {
    setBusy(true)
    try {
      await PanelAPI.automation.trigger(id)
      notify('success', `任务 #${id} 已手动触发`)
      refetch()
    } catch (e) {
      notify('error', `触发失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleClone = async (id: number) => {
    setBusy(true)
    try {
      const result = await PanelAPI.automation.clone(id)
      notify('success', `任务 #${id} 已克隆为新任务 #${result.id}`)
      refetch()
    } catch (e) {
      notify('error', `克隆失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm(`确认删除自动化任务 #${id}？此操作不可撤销。`)) return
    setBusy(true)
    try {
      await PanelAPI.automation.delete(id)
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
      <PageContainer title="自动化" subtitle="自动化任务编排与调度">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="自动化"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const tasks = data as AutomationTask[]
  const enabledCount = tasks.filter((t) => t.enabled).length

  const columns = [
    {
      key: 'id', header: 'ID',
      render: (t: any) => (
        <a href={`/automation/${t.id}`} onClick={(e) => { e.preventDefault(); navigate(`/automation/${t.id}`) }} style={{ fontWeight: 600 }}>
          #{t.id}
        </a>
      ),
    },
    {
      key: 'name', header: '任务名称',
      render: (t: any) => (
        <a href={`/automation/${t.id}`} onClick={(e) => { e.preventDefault(); navigate(`/automation/${t.id}`) }}>
          {t.name}
        </a>
      ),
    },
    { key: 'description', header: '描述', render: (t: any) => String(t.description ?? '—') },
    {
      key: 'trigger_type', header: '触发方式',
      render: (t: any) => <Badge type="info">{t.trigger_type}</Badge>,
    },
    {
      key: 'trigger_interval_secs', header: '间隔',
      render: (t: any) => {
        const sec = t.trigger_interval_secs
        if (!sec) return t.trigger_cron || '—'
        if (sec >= 3600) return `${(sec / 3600).toFixed(1)} 小时`
        if (sec >= 60) return `${(sec / 60).toFixed(1)} 分钟`
        return `${sec} 秒`
      },
    },
    {
      key: 'enabled', header: '状态',
      render: (t: any) => <Badge type={t.enabled ? 'ok' : 'warn'}>{t.enabled ? '启用' : '停用'}</Badge>,
    },
    {
      key: 'actions', header: '操作',
      render: (t: any) => (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <button
            className={`btn btn-sm ${t.enabled ? 'btn-ghost' : 'btn-primary'}`}
            disabled={busy}
            onClick={() => handleToggle(t.id, t.enabled)}
          >
            {t.enabled ? '停用' : '启用'}
          </button>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => handleTrigger(t.id)}>
            触发
          </button>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => handleClone(t.id)}>
            克隆
          </button>
          <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => handleDelete(t.id)}>
            删除
          </button>
        </div>
      ),
    },
  ]

  return (
    <PageContainer title="自动化" subtitle="自动化任务编排与调度">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="任务总数" value={tasks.length} color="cyan" />
        <StatCard label="已启用" value={enabledCount} color="emerald" />
        <StatCard label="已停用" value={tasks.length - enabledCount} color="amber" />
      </div>

      <Card title="创建自动化任务" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'name', label: '任务名称', placeholder: '如: 自动清理掉落物', required: true, width: '100%' },
            { name: 'description', label: '描述', placeholder: '任务说明（可选）', width: '100%' },
            {
              name: 'trigger_type', label: '触发方式', type: 'select',
              options: [
                { value: 'interval', label: '定时间隔' },
                { value: 'cron', label: 'Cron 表达式' },
                { value: 'event', label: '事件触发' },
                { value: 'manual', label: '仅手动' },
              ],
              defaultValue: 'interval', width: '100%',
            },
            { name: 'trigger_interval_secs', label: '间隔秒数', type: 'number', min: 1, defaultValue: 60, width: '100%' },
          ]}
          onSubmit={handleCreate}
          submitLabel={busy ? '创建中…' : '创建任务'}
          loading={busy}
        />
      </Card>

      <Card title="任务列表">
        <DataTable
          columns={columns}
          data={tasks as unknown as Record<string, unknown>[]}
          emptyMessage="暂无自动化任务"
          rowKey={(t) => (t as unknown as AutomationTask).id}
        />
      </Card>
    </PageContainer>
  )
}
