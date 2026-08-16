import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { PanelAction } from '../lib/types'

const ACTION_TYPES = [
  'kick_player', 'ban_player', 'temp_ban_player', 'unban_player',
  'broadcast', 'tell_player', 'set_player_gamemode',
  'clear_inventory', 'heal_player', 'feed_player',
]

const ACTION_LABELS: Record<string, string> = {
  kick_player: '踢出',
  ban_player: '封禁',
  temp_ban_player: '限时封禁',
  unban_player: '解封',
  broadcast: '广播',
  tell_player: '私信',
  set_player_gamemode: '切换模式',
  clear_inventory: '清空背包',
  heal_player: '治疗',
  feed_player: '喂食',
}

function statusBadge(status: string) {
  const map: Record<string, 'ok' | 'warn' | 'danger' | 'info'> = {
    DONE: 'ok', SUCCESS: 'ok', COMPLETED: 'ok',
    PENDING: 'warn', QUEUED: 'warn', PROCESSING: 'info',
    FAILED: 'danger', ERROR: 'danger', CANCELLED: 'danger',
  }
  return <Badge type={map[status?.toUpperCase()] || 'info'}>{status || '—'}</Badge>
}

export default function PlayerActions() {
  const { data, loading, error } = useApi(
    () => PanelAPI.recentActions(ACTION_TYPES),
    [],
    { interval: 15000 },
  )

  const actions = data ?? []

  const stats = {
    total: actions.length,
    done: actions.filter((a) => a.status?.toUpperCase() === 'DONE' || a.status?.toUpperCase() === 'SUCCESS').length,
    pending: actions.filter((a) => ['PENDING', 'QUEUED', 'PROCESSING'].includes(a.status?.toUpperCase() || '')).length,
    failed: actions.filter((a) => ['FAILED', 'ERROR', 'CANCELLED'].includes(a.status?.toUpperCase() || '')).length,
  }

  const columns = [
    { key: 'created_at', header: '时间', className: 'mono' as const },
    {
      key: 'action', header: '动作', render: (a: any) => (
        <span style={{
          padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 500,
          background: 'rgba(34,211,238,0.1)', color: 'var(--accent-cyan)',
        }}>
          {ACTION_LABELS[a.action] || a.action}
        </span>
      ),
    },
    {
      key: 'target', header: '目标玩家', render: (a: any) => (
        <div>
          {a.target_name && (
            <span style={{ fontWeight: 500, color: 'var(--ink-100)' }}>{a.target_name}</span>
          )}
          {a.target_uuid && (
            <div className="mono" style={{ fontSize: 11, color: 'var(--ink-500)' }}>{a.target_uuid}</div>
          )}
          {!a.target_name && !a.target_uuid && <span style={{ color: 'var(--ink-500)' }}>—</span>}
        </div>
      ),
    },
    {
      key: 'status', header: '状态', render: (a: any) => statusBadge(a.status),
    },
    {
      key: 'result', header: '结果', render: (a: any) => (
        <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{a.result || '—'}</span>
      ),
    },
    {
      key: 'params', header: '参数', render: (a: any) => (
        <span className="mono" style={{ fontSize: 11, color: 'var(--ink-500)', maxWidth: 300, display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {a.params || '—'}
        </span>
      ),
    },
    {
      key: 'processed_at', header: '处理时间', className: 'mono' as const,
      render: (a: any) => <span style={{ color: 'var(--ink-400)' }}>{a.processed_at || '—'}</span>,
    },
  ]

  return (
    <PageContainer title="玩家操作历史" subtitle="最近的玩家管理动作记录 · 每 15 秒自动刷新">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="动作总数" value={stats.total} color="cyan" />
        <StatCard label="已完成" value={stats.done} color="emerald" />
        <StatCard label="待处理" value={stats.pending} color="amber" />
        <StatCard label="失败" value={stats.failed} color="rose" />
      </div>

      {loading && !data ? (
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      ) : error ? (
        <div className="flash-err">{error}</div>
      ) : (
        <Card title="操作记录">
          <DataTable
            columns={columns}
            data={actions as unknown as Record<string, unknown>[]}
            emptyMessage="暂无操作记录"
            rowKey={(a) => (a as unknown as PanelAction).id}
          />
        </Card>
      )}
    </PageContainer>
  )
}
