import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { OnlinePlayer } from '../lib/types'

export default function ItemsGive() {
  const { notify } = useToast()
  const [submitting, setSubmitting] = useState(false)

  const { data: players, loading, error } = useApi<OnlinePlayer[]>(
    () => PanelAPI.onlinePlayers(),
    [],
    { interval: 15000 },
  )

  const handleGive = async (data: Record<string, string | number | boolean>) => {
    const uuid = String(data.uuid)
    const player = players?.find((p) => p.uuid === uuid)
    setSubmitting(true)
    try {
      await PanelAPI.payload('give_item', {
        player: player?.name || '',
        uuid,
        payload: String(data.item),
        count: Number(data.count || 1),
        reason: String(data.reason || ''),
      })
      notify('success', `已向 ${player?.name || '玩家'} 发放物品`)
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '发放失败')
    } finally {
      setSubmitting(false)
    }
  }

  const playerColumns = [
    { key: 'name', header: '玩家' },
    { key: 'gamemode', header: '模式', render: (p: OnlinePlayer) => <Badge type="info">{p.gamemode || '-'}</Badge> },
    { key: 'dimension', header: '维度' },
    { key: 'level', header: '等级' },
    { key: 'health', header: '生命值' },
  ]

  return (
    <PageContainer title="给予物品" subtitle="向在线玩家发放物品">
      <div style={{ marginBottom: 20 }}>
        <Card title="发放物品">
          <FormBuilder
            fields={[
              {
                name: 'uuid',
                label: '选择玩家',
                type: 'select',
                required: true,
                options: (players || []).map((p) => ({ value: p.uuid, label: p.name })),
              },
              { name: 'item', label: '物品 ID', type: 'text', placeholder: '例如：minecraft:diamond 或 diamond', required: true },
              { name: 'count', label: '数量', type: 'number', defaultValue: 1, min: 1, max: 64 },
              { name: 'reason', label: '原因', type: 'text', placeholder: '可选发放原因（审计记录）' },
            ]}
            onSubmit={handleGive}
            submitLabel="发放"
            loading={submitting}
          />
        </Card>
      </div>

      <Card title="在线玩家列表">
        {loading && !players ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={playerColumns}
            data={players || []}
            emptyMessage="当前无在线玩家"
            rowKey={(p) => p.uuid}
          />
        )}
      </Card>
    </PageContainer>
  )
}
