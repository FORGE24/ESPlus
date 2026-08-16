import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { GameEvent } from '../lib/types'

const CATEGORY_COLORS: Record<string, 'ok' | 'info' | 'warn' | 'danger' | 'violet'> = {
  ITEM: 'info',
  PLAYER: 'ok',
  MOVEMENT: 'violet',
  COMBAT: 'danger',
  CHAT: 'warn',
  BLOCK: 'info',
  ENTITY: 'violet',
}

interface SearchParams {
  q?: string
  category?: string
  actor?: string
  traceId?: string
}

export default function Search() {
  const { notify } = useToast()
  const [params, setParams] = useState<SearchParams>({})
  const [searched, setSearched] = useState(false)

  const { data, loading, error } = useApi(
    () => searched
      ? PanelAPI.search(params.q, params.category, params.actor, params.traceId)
      : Promise.resolve([]),
    [params.q, params.category, params.actor, params.traceId, searched],
  )

  const results = data ?? []

  function handleSearch(formData: Record<string, string | number | boolean>) {
    const newParams: SearchParams = {
      q: String(formData.q || '').trim() || undefined,
      category: String(formData.category || '').trim() || undefined,
      actor: String(formData.actor || '').trim() || undefined,
      traceId: String(formData.traceId || '').trim() || undefined,
    }
    if (!newParams.q && !newParams.category && !newParams.actor && !newParams.traceId) {
      notify('error', '请至少填写一个搜索条件')
      return
    }
    setParams(newParams)
    setSearched(true)
  }

  const columns = [
    { key: 'ts', header: '时间', className: 'mono' as const },
    {
      key: 'category', header: '类别', render: (e: any) => (
        <Badge type={CATEGORY_COLORS[e.category] || 'info'}>{e.category}</Badge>
      ),
    },
    {
      key: 'action', header: '动作', render: (e: any) => (
        <span style={{ fontWeight: 500, color: 'var(--ink-100)' }}>{e.action}</span>
      ),
    },
    {
      key: 'actor_name', header: '玩家', render: (e: any) => (
        <span style={{ color: 'var(--accent-cyan)' }}>{e.actor_name || '—'}</span>
      ),
    },
    {
      key: 'item_id', header: '物品', render: (e: any) => (
        <span className="mono" style={{ fontSize: 12, color: 'var(--accent-amber)' }}>
          {e.item_id || '—'}
        </span>
      ),
    },
    {
      key: 'detail', header: '详情', render: (e: any) => (
        <span style={{ fontSize: 12, color: 'var(--ink-300)', maxWidth: 300, display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {e.detail || '—'}
        </span>
      ),
    },
    {
      key: 'links', header: '链接', render: (e: any) => (
        <div style={{ display: 'flex', gap: 6 }}>
          {e.trace_id && (
            <Link
              to={`/trace/${e.trace_id}`}
              className="btn btn-ghost btn-sm"
              style={{ textDecoration: 'none' }}
            >
              溯源链
            </Link>
          )}
          {e.event_id && (
            <Link
              to={`/incident/${e.event_id}`}
              className="btn btn-ghost btn-sm"
              style={{ textDecoration: 'none' }}
            >
              事件链
            </Link>
          )}
        </div>
      ),
    },
  ]

  return (
    <PageContainer title="全局搜索" subtitle="按关键词 / 类别 / 玩家 / 溯源 ID 搜索游戏事件">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="搜索结果" value={results.length} color="cyan" />
        <StatCard label="含溯源" value={results.filter((e) => e.trace_id).length} color="emerald" />
        <StatCard label="含事件链" value={results.filter((e) => e.event_id).length} color="violet" />
      </div>

      <Card title="搜索条件" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'q', label: '关键词', type: 'text', placeholder: '搜索事件详情…' },
            { name: 'category', label: '类别', type: 'select', options: [
              { value: '', label: '全部' },
              { value: 'ITEM', label: '物品' },
              { value: 'PLAYER', label: '玩家' },
              { value: 'MOVEMENT', label: '移动' },
              { value: 'COMBAT', label: '战斗' },
              { value: 'CHAT', label: '聊天' },
              { value: 'BLOCK', label: '方块' },
              { value: 'ENTITY', label: '实体' },
            ]},
            { name: 'actor', label: '玩家名', type: 'text', placeholder: '玩家名或 UUID' },
            { name: 'traceId', label: '溯源 ID', type: 'text', placeholder: '物品溯源 ID' },
          ]}
          onSubmit={handleSearch}
          submitLabel="搜索"
          actions={
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setParams({})
                setSearched(false)
                notify('info', '已清除搜索')
              }}
            >
              清除
            </button>
          }
        />
      </Card>

      {loading && searched ? (
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      ) : error ? (
        <div className="flash-err">{error}</div>
      ) : (
        <Card title={`搜索结果（${results.length} 条）`}>
          <DataTable
            columns={columns}
            data={results as unknown as Record<string, unknown>[]}
            emptyMessage={searched ? '未找到匹配的事件' : '请输入搜索条件后点击搜索'}
            rowKey={(e) => (e as unknown as GameEvent).event_id}
          />
        </Card>
      )}
    </PageContainer>
  )
}
