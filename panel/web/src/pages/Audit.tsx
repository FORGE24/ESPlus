import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { AuditLog } from '../lib/types'

export default function Audit() {
  const { notify } = useToast()
  const [filters, setFilters] = useState<{ action?: string; uuid?: string; success?: boolean }>({})

  const { data, loading, error, refetch } = useApi(
    () => PanelAPI.audit(filters.action, filters.uuid, filters.success),
    [filters.action, filters.uuid, filters.success],
    { interval: 30000 },
  )

  const logs = data ?? []

  const stats = {
    total: logs.length,
    success: logs.filter((l) => l.success === 1).length,
    failed: logs.filter((l) => l.success === 0).length,
  }

  function handleFilter(formData: Record<string, string | number | boolean>) {
    const successStr = String(formData.success || '')
    setFilters({
      action: String(formData.action || '').trim() || undefined,
      uuid: String(formData.uuid || '').trim() || undefined,
      success: successStr === '1' ? true : successStr === '0' ? false : undefined,
    })
  }

  function handleExport() {
    PanelAPI.auditExport(filters.action, filters.uuid, filters.success)
    notify('info', '正在导出审计日志…')
  }

  const columns = [
    { key: 'ts', header: '时间', className: 'mono' as const },
    {
      key: 'uuid', header: 'UUID', className: 'mono' as const,
      render: (l: any) => (
        <span style={{ fontSize: 11, color: 'var(--ink-400)' }}>{l.uuid || '—'}</span>
      ),
    },
    {
      key: 'action', header: '动作', render: (l: any) => (
        <span style={{
          padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 500,
          background: 'rgba(167,139,250,0.1)', color: 'var(--accent-violet)',
        }}>
          {l.action}
        </span>
      ),
    },
    {
      key: 'detail', header: '详情', render: (l: any) => (
        <span style={{ fontSize: 12, color: 'var(--ink-300)' }}>{l.detail || '—'}</span>
      ),
    },
    {
      key: 'success', header: '结果', render: (l: any) => (
        <Badge type={l.success === 1 ? 'ok' : 'danger'}>
          {l.success === 1 ? '成功' : '失败'}
        </Badge>
      ),
    },
  ]

  return (
    <PageContainer
      title="安全审计"
      subtitle="操作审计日志 · 可按动作 / UUID / 结果筛选 · 支持导出"
      actions={
        <button className="btn btn-primary" onClick={handleExport}>
          导出 CSV
        </button>
      }
    >
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="审计记录" value={stats.total} color="cyan" />
        <StatCard label="成功" value={stats.success} color="emerald" />
        <StatCard label="失败" value={stats.failed} color="rose" />
      </div>

      {/* Filter form */}
      <Card title="筛选条件" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'action', label: '动作类型', type: 'text', placeholder: '如 kick / ban / gamemode…' },
            { name: 'uuid', label: 'UUID', type: 'text', placeholder: '玩家 UUID' },
            {
              name: 'success', label: '结果', type: 'select', options: [
                { value: '', label: '全部' },
                { value: '1', label: '成功' },
                { value: '0', label: '失败' },
              ],
            },
          ]}
          onSubmit={handleFilter}
          submitLabel="筛选"
          actions={
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setFilters({})
                notify('info', '已清除筛选')
              }}
            >
              重置
            </button>
          }
        />
      </Card>

      {loading && !data ? (
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      ) : error ? (
        <div className="flash-err">{error}</div>
      ) : (
        <Card title={`审计日志（${logs.length} 条）`}>
          <DataTable
            columns={columns}
            data={logs as unknown as Record<string, unknown>[]}
            emptyMessage="暂无审计记录"
            rowKey={(l) => (l as unknown as AuditLog).id}
          />
        </Card>
      )}
    </PageContainer>
  )
}
