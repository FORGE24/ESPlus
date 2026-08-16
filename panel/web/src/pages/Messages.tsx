import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { OnlinePlayer } from '../lib/types'

export default function Messages() {
  const { notify } = useToast()
  const [broadcasting, setBroadcasting] = useState(false)
  const [telling, setTelling] = useState(false)

  const { data: players, loading, error } = useApi<OnlinePlayer[]>(
    () => PanelAPI.onlinePlayers(),
    [],
    { interval: 15000 },
  )

  const handleBroadcast = async (data: Record<string, string | number | boolean>) => {
    setBroadcasting(true)
    try {
      await PanelAPI.broadcast(
        String(data.message),
        String(data.prefix || '[公告]'),
        Number(data.times || 1),
      )
      notify('success', '广播已发送')
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '广播失败')
    } finally {
      setBroadcasting(false)
    }
  }

  const handleTell = async (data: Record<string, string | number | boolean>) => {
    const uuid = String(data.uuid)
    const player = players?.find((p) => p.uuid === uuid)
    setTelling(true)
    try {
      await PanelAPI.tell(player?.name, uuid, String(data.message))
      notify('success', `已向 ${player?.name || '玩家'} 发送私信`)
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '私信发送失败')
    } finally {
      setTelling(false)
    }
  }

  const playerColumns = [
    { key: 'name', header: '玩家' },
    { key: 'gamemode', header: '模式', render: (p: OnlinePlayer) => <Badge type="info">{p.gamemode || '-'}</Badge> },
    { key: 'dimension', header: '维度' },
    { key: 'health', header: '生命值' },
    { key: 'ping', header: '延迟', render: (p: OnlinePlayer) => `${p.ping || 0} ms` },
    { key: 'ip', header: 'IP', className: 'mono' },
  ]

  return (
    <PageContainer title="广播 / 私信" subtitle="全服公告与定向消息发送">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="全服广播">
          <FormBuilder
            fields={[
              { name: 'message', label: '消息内容', type: 'textarea', placeholder: '输入广播消息内容…', required: true },
              { name: 'prefix', label: '前缀', type: 'text', defaultValue: '[公告]', placeholder: '[公告]' },
              { name: 'times', label: '重复次数', type: 'number', defaultValue: 1, min: 1, max: 10 },
            ]}
            onSubmit={handleBroadcast}
            submitLabel="发送广播"
            loading={broadcasting}
            layout="stack"
          />
        </Card>

        <Card title="私信玩家">
          <FormBuilder
            fields={[
              {
                name: 'uuid',
                label: '选择玩家',
                type: 'select',
                required: true,
                options: (players || []).map((p) => ({ value: p.uuid, label: p.name })),
              },
              { name: 'message', label: '私信内容', type: 'textarea', placeholder: '输入私信内容…', required: true },
            ]}
            onSubmit={handleTell}
            submitLabel="发送私信"
            loading={telling}
            layout="stack"
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
