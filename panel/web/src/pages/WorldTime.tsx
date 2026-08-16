import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

const TIME_PRESETS = [
  { value: '1000', label: '白天（1000）' },
  { value: '6000', label: '正午（6000）' },
  { value: '13000', label: '夜晚（13000）' },
  { value: '18000', label: '午夜（18000）' },
  { value: '0', label: '日出（0）' },
  { value: '23000', label: '日落前（23000）' },
]

const WEATHER_OPTIONS = [
  { value: 'clear', label: '晴天' },
  { value: 'rain', label: '雨天' },
  { value: 'thunder', label: '雷暴' },
]

export default function WorldTime() {
  const { notify } = useToast()
  const [settingTime, setSettingTime] = useState(false)
  const [settingWeather, setSettingWeather] = useState(false)
  const [toggling, setToggling] = useState<string | null>(null)

  const { data: runtime, loading, error, refetch } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 10000 },
  )

  const handleSetTime = async (data: Record<string, string | number | boolean>) => {
    setSettingTime(true)
    try {
      await PanelAPI.payload('set_time', { payload: Number(data.time) })
      notify('success', '时间已设置')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '设置失败')
    } finally {
      setSettingTime(false)
    }
  }

  const handleSetWeather = async (data: Record<string, string | number | boolean>) => {
    setSettingWeather(true)
    try {
      await PanelAPI.payload('set_weather', { payload: String(data.weather) })
      notify('success', '天气已设置')
      refetch()
    } finally {
      setSettingWeather(false)
    }
  }

  const handleToggleRule = async (ruleId: string, current: boolean) => {
    setToggling(ruleId)
    try {
      await PanelAPI.payload('gamerule_set', { ruleId, value: String(!current) })
      notify('success', `${ruleId} 已${!current ? '开启' : '关闭'}`)
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '操作失败')
    } finally {
      setToggling(null)
    }
  }

  const worldTime = Number(runtime?.world_time ?? 0)
  const weather = String(runtime?.weather ?? 'unknown')
  const doDaylight = runtime?.doDaylightCycle as boolean | undefined
  const doWeather = runtime?.doWeatherCycle as boolean | undefined

  if (loading && !runtime) {
    return (
      <PageContainer title="时间与天气" subtitle="控制世界时间、天气与昼夜循环">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="时间与天气"><div className="flash-err">{error}</div></PageContainer>

  return (
    <PageContainer title="时间与天气" subtitle="控制世界时间、天气与昼夜循环">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="世界时间" value={worldTime} color="amber" />
        <StatCard label="当前天气" value={weather === 'clear' ? '晴天' : weather === 'rain' ? '雨天' : weather === 'thunder' ? '雷暴' : weather} color="cyan" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="设置时间">
          <FormBuilder
            fields={[
              { name: 'time', label: '时间', type: 'select', defaultValue: '1000', options: TIME_PRESETS, required: true },
            ]}
            onSubmit={handleSetTime}
            submitLabel="设置时间"
            loading={settingTime}
            layout="stack"
          />
        </Card>

        <Card title="设置天气">
          <FormBuilder
            fields={[
              { name: 'weather', label: '天气', type: 'select', defaultValue: 'clear', options: WEATHER_OPTIONS, required: true },
            ]}
            onSubmit={handleSetWeather}
            submitLabel="设置天气"
            loading={settingWeather}
            layout="stack"
          />
        </Card>
      </div>

      <Card title="昼夜 / 天气循环">
        <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flex: 1, minWidth: 280 }}>
            <div style={{ flex: 1 }}>
              <p style={{ fontSize: 13, color: 'var(--ink-200)', fontWeight: 600 }}>doDaylightCycle</p>
              <p style={{ fontSize: 12, color: 'var(--ink-500)' }}>是否启用昼夜交替循环</p>
            </div>
            <button
              className={`btn btn-sm ${doDaylight === false ? 'btn-ghost' : 'btn-primary'}`}
              disabled={toggling === 'doDaylightCycle'}
              onClick={() => handleToggleRule('doDaylightCycle', doDaylight !== false)}
            >
              {doDaylight === false ? '已关闭' : '已开启'}
            </button>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flex: 1, minWidth: 280 }}>
            <div style={{ flex: 1 }}>
              <p style={{ fontSize: 13, color: 'var(--ink-200)', fontWeight: 600 }}>doWeatherCycle</p>
              <p style={{ fontSize: 12, color: 'var(--ink-500)' }}>是否启用天气变化循环</p>
            </div>
            <button
              className={`btn btn-sm ${doWeather === false ? 'btn-ghost' : 'btn-primary'}`}
              disabled={toggling === 'doWeatherCycle'}
              onClick={() => handleToggleRule('doWeatherCycle', doWeather !== false)}
            >
              {doWeather === false ? '已关闭' : '已开启'}
            </button>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
