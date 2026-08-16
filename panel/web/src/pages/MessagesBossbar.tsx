import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Bossbar } from '../lib/types'

const COLORS = [
  { value: 'pink', label: '粉色' },
  { value: 'blue', label: '蓝色' },
  { value: 'red', label: '红色' },
  { value: 'green', label: '绿色' },
  { value: 'yellow', label: '黄色' },
  { value: 'purple', label: '紫色' },
  { value: 'white', label: '白色' },
]

export default function MessagesBossbar() {
  const { notify } = useToast()
  const [creating, setCreating] = useState(false)
  const [updating, setUpdating] = useState(false)
  const [removing, setRemoving] = useState(false)

  const { data: bars, loading, error, refetch } = useApi<Bossbar[]>(
    () => api.get<Bossbar[]>('/api/messages/bossbars'),
    [],
    { interval: 15000 },
  )

  const handleCreate = async (data: Record<string, string | number | boolean>) => {
    setCreating(true)
    try {
      await PanelAPI.payload('bossbar_create', {
        id: String(data.id),
        name: String(data.name),
        color: String(data.color),
        max: Number(data.max || 100),
      })
      notify('success', 'Boss 血条已创建')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '创建失败')
    } finally {
      setCreating(false)
    }
  }

  const handleUpdate = async (data: Record<string, string | number | boolean>) => {
    setUpdating(true)
    try {
      await PanelAPI.payload('bossbar_update', {
        id: String(data.id),
        name: String(data.name || ''),
        color: String(data.color || ''),
        value: Number(data.value || 0),
        max: Number(data.max || 100),
        visible: Boolean(data.visible),
      })
      notify('success', 'Boss 血条已更新')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '更新失败')
    } finally {
      setUpdating(false)
    }
  }

  const handleRemove = async (data: Record<string, string | number | boolean>) => {
    setRemoving(true)
    try {
      await PanelAPI.payload('bossbar_remove', { id: String(data.id) })
      notify('success', 'Boss 血条已移除')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '移除失败')
    } finally {
      setRemoving(false)
    }
  }

  const columns = [
    { key: 'id', header: 'ID', className: 'mono' },
    { key: 'name', header: '名称' },
    { key: 'color', header: '颜色', render: (b: Bossbar) => <Badge type="violet">{b.color}</Badge> },
    { key: 'value', header: '当前值' },
    { key: 'max', header: '最大值' },
    { key: 'visible', header: '可见性', render: (b: Bossbar) => <Badge type={b.visible ? 'ok' : 'warn'}>{b.visible ? '显示' : '隐藏'}</Badge> },
  ]

  return (
    <PageContainer title="Boss 血条管理" subtitle="创建、更新和移除全服 Boss 血条">
      <div style={{ marginBottom: 20 }}>
        <Card title="当前血条列表">
          {loading && !bars ? (
            <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
          ) : error ? (
            <div className="flash-err">{error}</div>
          ) : (
            <DataTable
              columns={columns}
              data={bars || []}
              emptyMessage="当前无 Boss 血条"
              rowKey={(b) => b.id}
            />
          )}
        </Card>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <Card title="创建血条">
          <FormBuilder
            fields={[
              { name: 'id', label: '血条 ID', type: 'text', placeholder: '例如：dragon_bar', required: true },
              { name: 'name', label: '显示名称', type: 'text', placeholder: '例如：末影龙', required: true },
              { name: 'color', label: '颜色', type: 'select', defaultValue: 'red', options: COLORS, required: true },
              { name: 'max', label: '最大值', type: 'number', defaultValue: 100, min: 1 },
            ]}
            onSubmit={handleCreate}
            submitLabel="创建"
            loading={creating}
            layout="stack"
          />
        </Card>

        <div style={{ display: 'grid', gap: 20 }}>
          <Card title="更新血条">
            <FormBuilder
              fields={[
              { name: 'id', label: '血条 ID', type: 'text', placeholder: '要更新的 ID', required: true },
              { name: 'name', label: '显示名称', type: 'text', placeholder: '新名称' },
              {
                name: 'color',
                label: '颜色',
                type: 'select',
                options: COLORS,
              },
              { name: 'value', label: '当前值', type: 'number', min: 0 },
              { name: 'max', label: '最大值', type: 'number', min: 1 },
              { name: 'visible', label: '可见', type: 'checkbox', defaultValue: true, placeholder: '显示血条' },
            ]}
              onSubmit={handleUpdate}
              submitLabel="更新"
              loading={updating}
              layout="stack"
            />
          </Card>

          <Card title="移除血条">
            <FormBuilder
              fields={[
                { name: 'id', label: '血条 ID', type: 'text', placeholder: '要移除的 ID', required: true },
              ]}
              onSubmit={handleRemove}
              submitLabel="移除"
              loading={removing}
              layout="stack"
            />
          </Card>
        </div>
      </div>
    </PageContainer>
  )
}
