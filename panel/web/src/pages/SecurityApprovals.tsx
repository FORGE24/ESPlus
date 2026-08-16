import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Approval } from '../lib/types'

export default function SecurityApprovals() {
  const { notify } = useToast()
  const [filter, setFilter] = useState<string>('PENDING')
  const { data, loading, error, refetch } = useApi(() => PanelAPI.governance.approvals(filter), [filter], { interval: 15000 })
  const [busy, setBusy] = useState<number | null>(null)

  const handleApprove = async (id: number) => {
    setBusy(id)
    try {
      await PanelAPI.governance.approve(id)
      notify('success', `已批准审批 #${id}`)
      refetch()
    } catch (e) {
      notify('error', `批准失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(null)
    }
  }

  const handleReject = async (id: number) => {
    setBusy(id)
    try {
      await PanelAPI.governance.reject(id)
      notify('success', `已拒绝审批 #${id}`)
      refetch()
    } catch (e) {
      notify('error', `拒绝失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(null)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="操作审批" subtitle="高危操作审批与决策">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="操作审批"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const approvals = data as Approval[]
  const pendingCount = approvals.filter((a) => a.status === 'PENDING').length
  const approvedCount = approvals.filter((a) => a.status === 'APPROVED').length
  const rejectedCount = approvals.filter((a) => a.status === 'REJECTED').length

  const columns = [
    { key: 'id', header: 'ID' },
    { key: 'action', header: '操作' },
    { key: 'payload', header: '参数', className: 'mono', render: (a: any) => String(a.payload ?? '—') },
    {
      key: 'status', header: '状态',
      render: (a: any) => {
        const map: Record<string, 'ok' | 'warn' | 'danger' | 'info'> = {
          PENDING: 'warn', APPROVED: 'ok', REJECTED: 'danger', EXECUTED: 'info',
        }
        return <Badge type={map[a.status] || 'info'}>{a.status}</Badge>
      },
    },
    { key: 'requested_by', header: '发起人' },
    { key: 'created_at', header: '发起时间', className: 'mono' },
    { key: 'decided_by', header: '决策人', render: (a: any) => String(a.decided_by ?? '—') },
    {
      key: 'actions', header: '操作',
      render: (a: any) =>
        a.status === 'PENDING' ? (
          <div style={{ display: 'flex', gap: 6 }}>
            <button className="btn btn-primary btn-sm" disabled={busy === a.id} onClick={() => handleApprove(a.id)}>
              {busy === a.id ? '处理中…' : '批准'}
            </button>
            <button className="btn btn-danger btn-sm" disabled={busy === a.id} onClick={() => handleReject(a.id)}>
              拒绝
            </button>
          </div>
        ) : (
          <span style={{ color: 'var(--ink-500)', fontSize: 12 }}>已处理</span>
        ),
    },
  ]

  const filterBtns = [
    { val: 'PENDING', label: '待审批' },
    { val: '', label: '全部' },
    { val: 'APPROVED', label: '已批准' },
    { val: 'REJECTED', label: '已拒绝' },
  ]

  return (
    <PageContainer title="操作审批" subtitle="高危操作审批与决策">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="待审批" value={pendingCount} color={pendingCount > 0 ? 'amber' : 'emerald'} />
        <StatCard label="已批准" value={approvedCount} color="emerald" />
        <StatCard label="已拒绝" value={rejectedCount} color="rose" />
      </div>

      <Card
        title="审批列表"
        actions={
          <div style={{ display: 'flex', gap: 6 }}>
            {filterBtns.map((b) => (
              <button
                key={b.val}
                className={`btn btn-sm ${filter === b.val ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setFilter(b.val)}
              >
                {b.label}
              </button>
            ))}
          </div>
        }
        style={{ marginBottom: 20 }}
      >
        <DataTable
          columns={columns}
          data={approvals as unknown as Record<string, unknown>[]}
          emptyMessage="暂无审批记录"
          rowKey={(a) => (a as unknown as Approval).id}
        />
      </Card>
    </PageContainer>
  )
}
