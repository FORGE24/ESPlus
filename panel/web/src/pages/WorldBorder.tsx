import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

export default function WorldBorder() {
  const { notify } = useToast()
  const [submitting, setSubmitting] = useState(false)

  const { data: runtime, loading, error, refetch } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 15000 },
  )

  const handleSetBorder = async (data: Record<string, string | number | boolean>) => {
    setSubmitting(true)
    try {
      await PanelAPI.payload('set_worldborder', {
        size: Number(data.size || 60000000),
        centerX: Number(data.centerX || 0),
        centerZ: Number(data.centerZ || 0),
        warning: Number(data.warning || 5),
        damage: Number(data.damage || 0.2),
      })
      notify('success', '世界边界已设置')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '设置失败')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading && !runtime) {
    return (
      <PageContainer title="世界边界" subtitle="配置世界边界尺寸、中心与警告">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="世界边界"><div className="flash-err">{error}</div></PageContainer>

  const borderSize = Number(runtime?.world_border_size ?? 0)

  return (
    <PageContainer title="世界边界" subtitle="配置世界边界尺寸、中心与警告">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="当前边界大小" value={borderSize} color="cyan" suffix=" 格" />
        <StatCard label="边界半径" value={borderSize / 2} decimals={0} color="emerald" suffix=" 格" />
      </div>

      <Card title="设置世界边界">
        <FormBuilder
          fields={[
            { name: 'size', label: '边界大小（格）', type: 'number', defaultValue: borderSize || 60000000, min: 1, required: true, placeholder: '例如：60000000' },
            { name: 'centerX', label: '中心 X 坐标', type: 'number', defaultValue: 0 },
            { name: 'centerZ', label: '中心 Z 坐标', type: 'number', defaultValue: 0 },
            { name: 'warning', label: '警告距离（格）', type: 'number', defaultValue: 5, min: 0, placeholder: '接近边界时屏幕变红' },
            { name: 'damage', label: '边界伤害', type: 'number', defaultValue: 0.2, min: 0, placeholder: '越过边界时每秒伤害' },
          ]}
          onSubmit={handleSetBorder}
          submitLabel="应用设置"
          loading={submitting}
        />
        <div style={{ marginTop: 16 }}>
          <p style={{ fontSize: 12, color: 'var(--ink-500)' }}>
            边界大小为正方形边长。警告距离决定玩家接近边界多少格时屏幕开始变红。边界伤害决定越过边界时每秒受到的伤害值。
          </p>
        </div>
      </Card>
    </PageContainer>
  )
}
