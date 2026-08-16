import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { OnlinePlayer } from '../lib/types'

export default function WorldSpawn() {
  const { notify } = useToast()
  const [settingWorld, setSettingWorld] = useState(false)
  const [settingPlayer, setSettingPlayer] = useState(false)

  const { data: players } = useApi<OnlinePlayer[]>(
    () => PanelAPI.onlinePlayers(),
    [],
    { interval: 15000 },
  )

  const handleSetWorldSpawn = async (data: Record<string, string | number | boolean>) => {
    setSettingWorld(true)
    try {
      await PanelAPI.payload('set_worldspawn', {
        x: Number(data.x || 0),
        y: Number(data.y || 64),
        z: Number(data.z || 0),
        angle: Number(data.angle || 0),
      })
      notify('success', '世界出生点已设置')
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '设置失败')
    } finally {
      setSettingWorld(false)
    }
  }

  const handleSetPlayerSpawn = async (data: Record<string, string | number | boolean>) => {
    const uuid = String(data.uuid)
    const player = players?.find((p) => p.uuid === uuid)
    setSettingPlayer(true)
    try {
      await PanelAPI.payload('set_spawnpoint', {
        player: player?.name || '',
        uuid,
        x: Number(data.x || 0),
        y: Number(data.y || 64),
        z: Number(data.z || 0),
        angle: Number(data.angle || 0),
      })
      notify('success', `已设置 ${player?.name || '玩家'} 的出生点`)
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '设置失败')
    } finally {
      setSettingPlayer(false)
    }
  }

  return (
    <PageContainer title="出生点设置" subtitle="设置世界出生点与玩家个人出生点">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <Card title="设置世界出生点">
          <FormBuilder
            fields={[
              { name: 'x', label: 'X 坐标', type: 'number', defaultValue: 0, required: true },
              { name: 'y', label: 'Y 坐标', type: 'number', defaultValue: 64, required: true },
              { name: 'z', label: 'Z 坐标', type: 'number', defaultValue: 0, required: true },
              { name: 'angle', label: '朝向角度', type: 'number', defaultValue: 0, min: 0, max: 360, placeholder: '0-360 度' },
            ]}
            onSubmit={handleSetWorldSpawn}
            submitLabel="设置世界出生点"
            loading={settingWorld}
            layout="stack"
          />
          <div style={{ marginTop: 16 }}>
            <p style={{ fontSize: 12, color: 'var(--ink-500)' }}>
              世界出生点是新玩家首次加入时出生的位置。朝向角度 0 = 南，90 = 西，180 = 北，270 = 东。
            </p>
          </div>
        </Card>

        <Card title="设置玩家出生点">
          <FormBuilder
            fields={[
              {
                name: 'uuid',
                label: '选择玩家',
                type: 'select',
                required: true,
                options: (players || []).map((p) => ({ value: p.uuid, label: p.name })),
              },
              { name: 'x', label: 'X 坐标', type: 'number', defaultValue: 0, required: true },
              { name: 'y', label: 'Y 坐标', type: 'number', defaultValue: 64, required: true },
              { name: 'z', label: 'Z 坐标', type: 'number', defaultValue: 0, required: true },
              { name: 'angle', label: '朝向角度', type: 'number', defaultValue: 0, min: 0, max: 360 },
            ]}
            onSubmit={handleSetPlayerSpawn}
            submitLabel="设置玩家出生点"
            loading={settingPlayer}
            layout="stack"
          />
          <div style={{ marginTop: 16 }}>
            <p style={{ fontSize: 12, color: 'var(--ink-500)' }}>
              玩家出生点会覆盖世界出生点。该玩家死亡后将在指定坐标重生。
            </p>
          </div>
        </Card>
      </div>
    </PageContainer>
  )
}
