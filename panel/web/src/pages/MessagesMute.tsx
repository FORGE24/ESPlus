import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Mute, OnlinePlayer } from '../lib/types'

export default function MessagesMute() {
  const { notify } = useToast()
  const [submitting, setSubmitting] = useState(false)

  const { data: mutes, loading, error, refetch } = useApi<Mute[]>(
    () => api.get<Mute[]>('/api/messages/mutes'),
    [],
    { interval: 15000 },
  )

  const { data: players } = useApi<OnlinePlayer[]>(
    () => api.get<OnlinePlayer[]>('/api/players/online'),
    [],
    { interval: 15000 },
  )

  const handleMute = async (data: Record<string, string | number | boolean>) => {
    const uuid = String(data.uuid)
    const player = players?.find((p) => p.uuid === uuid)
    setSubmitting(true)
    try {
      await api.post('/api/messages/mute', {
        player: player?.name || '',
        uuid,
        minutes: Number(data.minutes || 30),
        reason: String(data.reason || ''),
      })
      notify('success', `已禁言 ${player?.name || '玩家'}`)
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '禁言失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleUnmute = async (m: Mute) => {
    try {
      await api.post('/api/messages/unmute', { player: m.player, uuid: m.uuid })
      notify('success', `已解除 ${m.player} 的禁言`)
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '解除失败')
    }
  }

  const columns = [
    { key: 'player', header: '玩家' },
    { key: 'uuid', header: 'UUID', className: 'mono' },
    { key: 'reason', header: '原因', render: (m: Mute) => m.reason || '-' },
    { key: 'expires_at', header: '到期时间', className: 'mono', render: (m: Mute) => m.expires_at || '永久' },
    {
      key: 'actions',
      header: '操作',
      render: (m: Mute) => (
        <button className="btn btn-danger btn-sm" onClick={() => handleUnmute(m)}>
          解除禁言
        </button>
      ),
    },
  ]

  return (
    <PageContainer title="禁言管理" subtitle="管理玩家聊天禁言">
      <div style={{ marginBottom: 20 }}>
        <Card title="禁言玩家">
          <FormBuilder
            fields={[
              {
                name: 'uuid',
                label: '选择玩家',
                type: 'select',
                required: true,
                options: (players || []).map((p) => ({ value: p.uuid, label: p.name })),
              },
              { name: 'minutes', label: '禁言时长（分钟）', type: 'number', defaultValue: 30, min: 1, placeholder: '0 表示永久' },
              { name: 'reason', label: '原因', type: 'text', placeholder: '可选禁言原因' },
            ]}
            onSubmit={handleMute}
            submitLabel="禁言"
            loading={submitting}
            layout="stack"
          />
        </Card>
      </div>

      <Card title="当前禁言列表">
        {loading && !mutes ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={columns}
            data={mutes || []}
            emptyMessage="当前无禁言记录"
            rowKey={(m) => m.key}
          />
        )}
      </Card>
    </PageContainer>
  )
}
