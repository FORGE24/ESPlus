import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { ChatFilterWord } from '../lib/types'

export default function MessagesFilter() {
  const { notify } = useToast()
  const [submitting, setSubmitting] = useState(false)

  const { data: words, loading, error, refetch } = useApi<ChatFilterWord[]>(
    () => api.get<ChatFilterWord[]>('/api/governance/chat-filter'),
    [],
    { interval: 15000 },
  )

  const handleAdd = async (data: Record<string, string | number | boolean>) => {
    const word = String(data.word).trim()
    if (!word) return
    setSubmitting(true)
    try {
      await api.post('/api/messages/filter/add', { word })
      notify('success', '过滤词已添加')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '添加失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleToggle = async (w: ChatFilterWord) => {
    try {
      await api.post('/api/messages/filter/toggle', { id: w.id })
      notify('success', '已切换状态')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '操作失败')
    }
  }

  const handleDelete = async (w: ChatFilterWord) => {
    if (!confirm(`确认删除过滤词 "${w.word}"？`)) return
    try {
      await api.post('/api/messages/filter/delete', { id: w.id })
      notify('success', '已删除')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '删除失败')
    }
  }

  const columns = [
    { key: 'word', header: '过滤词' },
    {
      key: 'enabled',
      header: '状态',
      render: (w: ChatFilterWord) => <Badge type={w.enabled === 1 ? 'ok' : 'warn'}>{w.enabled === 1 ? '启用' : '停用'}</Badge>,
    },
    {
      key: 'actions',
      header: '操作',
      render: (w: ChatFilterWord) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-ghost btn-sm" onClick={() => handleToggle(w)}>
            {w.enabled === 1 ? '停用' : '启用'}
          </button>
          <button className="btn btn-danger btn-sm" onClick={() => handleDelete(w)}>
            删除
          </button>
        </div>
      ),
    },
  ]

  return (
    <PageContainer title="聊天过滤" subtitle="管理聊天屏蔽词列表">
      <div style={{ marginBottom: 20 }}>
        <Card title="添加过滤词">
          <FormBuilder
            fields={[
              { name: 'word', label: '过滤词', type: 'text', placeholder: '输入要屏蔽的词语', required: true },
            ]}
            onSubmit={handleAdd}
            submitLabel="添加"
            loading={submitting}
            layout="stack"
          />
        </Card>
      </div>

      <Card title="过滤词列表">
        {loading && !words ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={columns}
            data={words || []}
            emptyMessage="暂无过滤词"
            rowKey={(w) => w.id}
          />
        )}
      </Card>
    </PageContainer>
  )
}
