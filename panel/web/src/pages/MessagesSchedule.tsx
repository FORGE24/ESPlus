import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Schedule } from '../lib/types'

export default function MessagesSchedule() {
  const { notify } = useToast()
  const [submitting, setSubmitting] = useState(false)

  const { data: schedules, loading, error, refetch } = useApi<Schedule[]>(
    () => PanelAPI.schedules(),
    [],
    { interval: 15000 },
  )

  const handleCreate = async (data: Record<string, string | number | boolean>) => {
    setSubmitting(true)
    try {
      await PanelAPI.createSchedule({
        message: String(data.message),
        prefix: String(data.prefix || '[公告]'),
        times: Number(data.times || 1),
        delaySeconds: Number(data.delaySeconds || 0),
        intervalSeconds: Number(data.intervalSeconds || 60),
        note: String(data.note || ''),
      })
      notify('success', '定时广播已创建')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '创建失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggle = async (s: Schedule) => {
    const enabled = s.enabled !== 1
    try {
      await PanelAPI.toggleSchedule(s.id, enabled)
      notify('success', enabled ? '已启用' : '已停用')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '操作失败')
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确认删除此定时广播？')) return
    try {
      await PanelAPI.deleteSchedule(id)
      notify('success', '已删除')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '删除失败')
    }
  }

  const columns = [
    { key: 'id', header: 'ID', width: '60px' },
    { key: 'note', header: '备注', render: (s: Schedule) => s.note || s.payload || '-' },
    { key: 'interval_seconds', header: '间隔（秒）' },
    { key: 'next_run_at', header: '下次执行', className: 'mono' },
    {
      key: 'enabled',
      header: '状态',
      render: (s: Schedule) => <Badge type={s.enabled === 1 ? 'ok' : 'warn'}>{s.enabled === 1 ? '启用' : '停用'}</Badge>,
    },
    {
      key: 'actions',
      header: '操作',
      render: (s: Schedule) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-ghost btn-sm" onClick={() => handleToggle(s)}>
            {s.enabled === 1 ? '停用' : '启用'}
          </button>
          <button className="btn btn-danger btn-sm" onClick={() => handleDelete(s.id)}>
            删除
          </button>
        </div>
      ),
    },
  ]

  return (
    <PageContainer title="定时广播" subtitle="按间隔自动重复发送广播消息">
      <div style={{ marginBottom: 20 }}>
        <Card title="创建定时广播">
          <FormBuilder
            fields={[
              { name: 'message', label: '消息内容', type: 'textarea', placeholder: '输入广播消息…', required: true },
              { name: 'prefix', label: '前缀', type: 'text', defaultValue: '[公告]' },
              { name: 'times', label: '每次重复次数', type: 'number', defaultValue: 1, min: 1, max: 10 },
              { name: 'delaySeconds', label: '首次延迟（秒）', type: 'number', defaultValue: 0, min: 0 },
              { name: 'intervalSeconds', label: '间隔（秒）', type: 'number', defaultValue: 60, min: 1 },
              { name: 'note', label: '备注', type: 'text', placeholder: '可选备注说明' },
            ]}
            onSubmit={handleCreate}
            submitLabel="创建"
            loading={submitting}
          />
        </Card>
      </div>

      <Card title="已有定时任务">
        {loading && !schedules ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={columns}
            data={schedules || []}
            emptyMessage="暂无定时广播任务"
            rowKey={(s) => s.id}
          />
        )}
      </Card>
    </PageContainer>
  )
}
