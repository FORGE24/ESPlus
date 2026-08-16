import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

const DIFFICULTY_OPTIONS = [
  { value: 'peaceful', label: '和平' },
  { value: 'easy', label: '简单' },
  { value: 'normal', label: '普通' },
  { value: 'hard', label: '困难' },
]

const GAMEMODE_OPTIONS = [
  { value: 'survival', label: '生存' },
  { value: 'creative', label: '创造' },
  { value: 'adventure', label: '冒险' },
  { value: 'spectator', label: '旁观' },
]

export default function WorldDifficulty() {
  const { notify } = useToast()
  const [settingDiff, setSettingDiff] = useState(false)
  const [settingGm, setSettingGm] = useState(false)

  const { data: runtime, loading, error, refetch } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 15000 },
  )

  const handleSetDifficulty = async (data: Record<string, string | number | boolean>) => {
    setSettingDiff(true)
    try {
      await PanelAPI.payload('set_difficulty', { payload: String(data.difficulty) })
      notify('success', '难度已设置')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '设置失败')
    } finally {
      setSettingDiff(false)
    }
  }

  const handleSetGamemode = async (data: Record<string, string | number | boolean>) => {
    setSettingGm(true)
    try {
      await PanelAPI.payload('set_default_gamemode', { payload: String(data.gamemode) })
      notify('success', '默认游戏模式已设置')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '设置失败')
    } finally {
      setSettingGm(false)
    }
  }

  if (loading && !runtime) {
    return (
      <PageContainer title="难度与模式" subtitle="设置服务器难度与默认游戏模式">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="难度与模式"><div className="flash-err">{error}</div></PageContainer>

  const difficulty = String(runtime?.difficulty ?? 'unknown')
  const gamemode = String(runtime?.gamemode ?? 'unknown')

  const diffLabel = DIFFICULTY_OPTIONS.find((d) => d.value === difficulty)?.label || difficulty
  const gmLabel = GAMEMODE_OPTIONS.find((g) => g.value === gamemode)?.label || gamemode

  return (
    <PageContainer title="难度与模式" subtitle="设置服务器难度与默认游戏模式">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="当前难度" value={diffLabel} color="rose" />
        <StatCard label="默认游戏模式" value={gmLabel} color="violet" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <Card title="设置难度">
          <FormBuilder
            fields={[
              {
                name: 'difficulty',
                label: '难度',
                type: 'select',
                defaultValue: difficulty,
                options: DIFFICULTY_OPTIONS,
                required: true,
              },
            ]}
            onSubmit={handleSetDifficulty}
            submitLabel="设置难度"
            loading={settingDiff}
            layout="stack"
          />
          <div style={{ marginTop: 16 }}>
            <p style={{ fontSize: 12, color: 'var(--ink-500)' }}>
              和平：无敌对生物生成；困难：僵尸可破门，凋灵伤害更高。
            </p>
          </div>
        </Card>

        <Card title="设置默认游戏模式">
          <FormBuilder
            fields={[
              {
                name: 'gamemode',
                label: '游戏模式',
                type: 'select',
                defaultValue: gamemode,
                options: GAMEMODE_OPTIONS,
                required: true,
              },
            ]}
            onSubmit={handleSetGamemode}
            submitLabel="设置模式"
            loading={settingGm}
            layout="stack"
          />
          <div style={{ marginTop: 16 }}>
            <p style={{ fontSize: 12, color: 'var(--ink-500)' }}>
              新玩家加入时的默认游戏模式。已在线玩家不受影响。
            </p>
          </div>
        </Card>
      </div>
    </PageContainer>
  )
}
