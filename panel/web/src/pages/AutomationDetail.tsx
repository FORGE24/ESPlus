import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { AutomationTask } from '../lib/types'

export default function AutomationDetail() {
  const { id } = useParams<{ id: string }>()
  const taskId = Number(id)
  const { notify } = useToast()
  const navigate = useNavigate()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.automation.get(taskId), [taskId], { interval: 15000 })
  const [busy, setBusy] = useState(false)
  const [logs, setLogs] = useState<unknown[]>([])

  useEffect(() => {
    if (!taskId) return
    PanelAPI.automation.logs(taskId).then((l) => setLogs(l)).catch(() => {})
  }, [taskId])

  const handleUpdate = async (formData: Record<string, string | number | boolean>) => {
    setBusy(true)
    try {
      await PanelAPI.automation.update(taskId, {
        name: String(formData.name || ''),
        description: String(formData.description || ''),
        trigger_type: String(formData.trigger_type || 'interval'),
        trigger_interval_secs: Number(formData.trigger_interval_secs || 60),
        enabled: Boolean(formData.enabled),
      })
      notify('success', '任务配置已更新')
      refetch()
    } catch (e) {
      notify('error', `更新失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleTrigger = async () => {
    setBusy(true)
    try {
      await PanelAPI.automation.trigger(taskId)
      notify('success', '任务已手动触发')
      const l = await PanelAPI.automation.logs(taskId)
      setLogs(l)
    } catch (e) {
      notify('error', `触发失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="自动化任务详情" subtitle={`任务 #${taskId}`}>
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="自动化任务详情"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const task = data as AutomationTask

  const logColumns = [
    { key: 'ts', header: '时间', className: 'mono', render: (r: Record<string, unknown>) => String(r.ts ?? r.timestamp ?? '—') },
    {
      key: 'level', header: '级别',
      render: (r: Record<string, unknown>) => {
        const level = String(r.level ?? 'INFO')
        const type: 'ok' | 'warn' | 'danger' | 'info' = level === 'ERROR' ? 'danger' : level === 'WARN' ? 'warn' : level === 'DEBUG' ? 'info' : 'ok'
        return <Badge type={type}>{level}</Badge>
      },
    },
    { key: 'message', header: '消息', render: (r: Record<string, unknown>) => String(r.message ?? r.msg ?? '—') },
  ]

  const nodeColumns = [
    { key: 'id', header: '节点 ID' },
    { key: 'name', header: '节点名称' },
    { key: 'task_id', header: '所属任务 ID' },
  ]

  const opColumns = [
    { key: 'id', header: '操作 ID' },
    { key: 'node_id', header: '节点 ID' },
    { key: 'action_type', header: '操作类型', render: (r: Record<string, unknown>) => <Badge type="info">{String(r.action_type ?? '—')}</Badge> },
    { key: 'params', header: '参数', className: 'mono', render: (r: Record<string, unknown>) => String(r.params ?? '—') },
    {
      key: 'enabled', header: '状态',
      render: (r: Record<string, unknown>) => <Badge type={r.enabled ? 'ok' : 'warn'}>{r.enabled ? '启用' : '停用'}</Badge>,
    },
  ]

  return (
    <PageContainer
      title={`任务：${task.name}`}
      subtitle={`ID: ${task.id} · ${task.trigger_type}`}
      actions={
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-primary btn-sm" disabled={busy} onClick={handleTrigger}>
            手动触发
          </button>
          <button className="btn btn-ghost btn-sm" onClick={() => navigate('/automation')}>
            返回列表
          </button>
        </div>
      }
    >
      <Card title="任务信息" style={{ marginBottom: 20 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">任务 ID</div>
            <div className="mono" style={{ fontSize: 14, color: 'var(--accent-cyan)' }}>{task.id}</div>
          </div>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">状态</div>
            <Badge type={task.enabled ? 'ok' : 'warn'}>{task.enabled ? '启用' : '停用'}</Badge>
          </div>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">触发方式</div>
            <div style={{ fontSize: 14, color: 'var(--ink-300)' }}>{task.trigger_type}</div>
          </div>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">间隔</div>
            <div style={{ fontSize: 14, color: 'var(--ink-300)' }}>
              {task.trigger_interval_secs ? `${task.trigger_interval_secs} 秒` : task.trigger_cron || '—'}
            </div>
          </div>
        </div>
        {task.description && (
          <div style={{ marginTop: 12, padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">描述</div>
            <div style={{ fontSize: 13, color: 'var(--ink-300)' }}>{task.description}</div>
          </div>
        )}
      </Card>

      <Card title="编辑任务配置" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'name', label: '任务名称', defaultValue: task.name, required: true, width: '100%' },
            { name: 'description', label: '描述', defaultValue: task.description || '', width: '100%' },
            {
              name: 'trigger_type', label: '触发方式', type: 'select',
              options: [
                { value: 'interval', label: '定时间隔' },
                { value: 'cron', label: 'Cron 表达式' },
                { value: 'event', label: '事件触发' },
                { value: 'manual', label: '仅手动' },
              ],
              defaultValue: task.trigger_type, width: '100%',
            },
            {
              name: 'trigger_interval_secs', label: '间隔秒数', type: 'number', min: 1,
              defaultValue: task.trigger_interval_secs || 60, width: '100%',
            },
            {
              name: 'enabled', label: '启用任务', type: 'checkbox',
              defaultValue: task.enabled, placeholder: '勾选启用此任务', width: '100%',
            },
          ]}
          onSubmit={handleUpdate}
          submitLabel={busy ? '保存中…' : '保存配置'}
          loading={busy}
        />
      </Card>

      {task.nodes && task.nodes.length > 0 && (
        <Card title="执行节点" style={{ marginBottom: 20 }}>
          <DataTable
            columns={nodeColumns}
            data={task.nodes as unknown as Record<string, unknown>[]}
            emptyMessage="暂无节点"
            rowKey={(n) => String((n as Record<string, unknown>).id)}
          />
        </Card>
      )}

      {task.operations && task.operations.length > 0 && (
        <Card title="操作列表" style={{ marginBottom: 20 }}>
          <DataTable
            columns={opColumns}
            data={task.operations as unknown as Record<string, unknown>[]}
            emptyMessage="暂无操作"
            rowKey={(o) => String((o as Record<string, unknown>).id)}
          />
        </Card>
      )}

      <Card title="执行日志">
        <DataTable
          columns={logColumns}
          data={logs as Record<string, unknown>[]}
          emptyMessage="暂无执行日志"
          rowKey={(l, i) => String((l as Record<string, unknown>).id ?? i)}
        />
      </Card>
    </PageContainer>
  )
}
