import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { OnlinePlayer } from '../lib/types'

export default function ItemsClear() {
  const { notify } = useToast()
  const [submitting, setSubmitting] = useState(false)

  const { data: players, loading, error } = useApi<OnlinePlayer[]>(
    () => PanelAPI.onlinePlayers(),
    [],
    { interval: 15000 },
  )

  const handleClear = async (data: Record<string, string | number | boolean>) => {
    const uuid = String(data.uuid)
    const player = players?.find((p) => p.uuid === uuid)
    const clearEnder = data.clearEnder === true
    setSubmitting(true)
    try {
      const action = clearEnder ? 'clear_enderchest' : 'clear_inventory'
      await PanelAPI.payload(action, {
        player: player?.name || '',
        uuid,
      })
      notify('success', `已清空 ${player?.name || '玩家'} 的${clearEnder ? '末影箱' : '背包'}`)
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '清空失败')
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
    <PageContainer title="清空背包" subtitle="清空在线玩家的背包或末影箱">
      <div style={{ marginBottom: 20 }}>
        <Card title="清空操作">
          <FormBuilder
            fields={[
              {
                name: 'uuid',
                label: '选择玩家',
                type: 'select',
                required: true,
                options: (players || []).map((p) => ({ value: p.uuid, label: p.name })),
              },
              {
                name: 'clearEnder',
                label: '清空末影箱',
                type: 'checkbox',
                defaultValue: false,
                placeholder: '勾选清空末影箱，不勾选清空背包',
              },
            ]}
            onSubmit={handleClear}
            submitLabel="执行清空"
            loading={submitting}
            layout="stack"
          />
          <div className="flash-err" style={{ marginTop: 16 }}>
            注意：此操作不可撤销，清空后物品无法恢复。
          </div>
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
