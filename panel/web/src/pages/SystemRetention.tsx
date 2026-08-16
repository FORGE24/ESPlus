import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

export default function SystemRetention() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.runtime(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  if (loading && !data) {
    return (
      <PageContainer title="数据保留" subtitle="数据保留策略与清理管理">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="数据保留"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const rt = data as RuntimeSnapshot & Record<string, unknown>
  const retentionDays = Number(rt.retention_days ?? rt.retentionDays ?? 90)
  const auditRetentionDays = Number(rt.audit_retention_days ?? rt.auditRetentionDays ?? 90)
  const eventRetentionDays = Number(rt.event_retention_days ?? rt.eventRetentionDays ?? 30)
  const movementRetentionDays = Number(rt.movement_retention_days ?? rt.movementRetentionDays ?? 14)

  const handleCleanup = async (formData: Record<string, string | number | boolean>) => {
    const days = Number(formData.days)
    if (!days || days < 1) {
      notify('error', '请输入有效的保留天数')
      return
    }
    if (!window.confirm(`确认清理 ${days} 天前的数据？此操作不可撤销。`)) return
    setBusy(true)
    try {
      await PanelAPI.payload('retention_cleanup', { days })
      notify('success', `已清理 ${days} 天前的数据`)
      refetch()
    } catch (e) {
      notify('error', `清理失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const retentionItems = [
    { label: '总保留天数', value: retentionDays, desc: '所有数据的默认保留周期' },
    { label: '审计日志保留', value: auditRetentionDays, desc: '安全审计记录的保留天数' },
    { label: '事件数据保留', value: eventRetentionDays, desc: '游戏事件的保留天数' },
    { label: '移动轨迹保留', value: movementRetentionDays, desc: '玩家移动数据的保留天数' },
  ]

  return (
    <PageContainer title="数据保留" subtitle="数据保留策略与清理管理">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        {retentionItems.map((item) => (
          <StatCard
            key={item.label}
            label={item.label}
            value={item.value}
            suffix="天"
            color="cyan"
          />
        ))}
      </div>

      <Card title="当前保留策略" style={{ marginBottom: 20 }}>
        <div style={{ display: 'grid', gap: 12 }}>
          {retentionItems.map((item) => (
            <div
              key={item.label}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '12px 16px',
                background: 'var(--bg-700)',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--glass-border)',
              }}
            >
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-100)' }}>{item.label}</div>
                <div style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: 2 }}>{item.desc}</div>
              </div>
              <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--accent-cyan)', fontFamily: 'Syne, Inter, sans-serif' }}>
                {item.value} 天
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card title="手动清理数据">
        <div className="flash-err" style={{ marginBottom: 16 }}>
          手动清理将删除指定天数之前的所有过期数据（审计日志、事件、移动轨迹等），此操作不可撤销。
        </div>
        <FormBuilder
          fields={[
            { name: 'days', label: '保留天数', type: 'number', placeholder: '如: 30', min: 1, max: 365, required: true, width: '100%' },
          ]}
          onSubmit={handleCleanup}
          submitLabel={busy ? '清理中…' : '执行清理'}
          loading={busy}
          layout="stack"
        />
      </Card>
    </PageContainer>
  )
}
