import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { EntityType } from '../lib/types'

const KILL_KINDS = [
  { value: 'all', label: '全部实体', desc: '清除所有实体（慎用）', danger: true },
  { value: 'hostile', label: '敌对生物', desc: '僵尸、骷髅、苦力怕等', danger: false },
  { value: 'passive', label: '被动生物', desc: '牛、羊、猪等', danger: false },
  { value: 'item', label: '掉落物', desc: '地上的物品实体', danger: false },
  { value: 'projectile', label: '投射物', desc: '箭、雪球等', danger: false },
  { value: 'vehicle', label: '载具', desc: '矿车、船等', danger: false },
  { value: 'xp', label: '经验球', desc: '经验点实体', danger: false },
]

export default function EntitiesCleanup() {
  const { notify } = useToast()
  const [killing, setKilling] = useState(false)
  const [killingType, setKillingType] = useState(false)

  const { data: entityTypes, loading, error, refetch } = useApi<EntityType[]>(
    () => api.get<EntityType[]>('/api/entities'),
    [],
    { interval: 15000 },
  )

  const handleKillKind = async (kind: string) => {
    const kindInfo = KILL_KINDS.find((k) => k.value === kind)
    if (kind === 'all') {
      if (!confirm('确认清除全部实体？此操作不可撤销！')) return
    }
    setKilling(true)
    try {
      await PanelAPI.payload('kill_entities', { payload: kind })
      notify('success', `已清理${kindInfo?.label || kind}`)
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '清理失败')
    } finally {
      setKilling(false)
    }
  }

  const handleKillType = async (data: Record<string, string | number | boolean>) => {
    setKillingType(true)
    try {
      await PanelAPI.payload('kill_entities', { payload: String(data.type) })
      notify('success', `已清理 ${data.type}`)
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '清理失败')
    } finally {
      setKillingType(false)
    }
  }

  const entityColumns = [
    { key: 'type', header: '实体类型', className: 'mono' },
    { key: 'count', header: '数量', render: (e: EntityType) => <Badge type={e.count > 100 ? 'warn' : 'info'}>{e.count}</Badge> },
    { key: 'dimension', header: '所在维度' },
  ]

  return (
    <PageContainer title="一键清理" subtitle="按类型批量清除实体，释放服务器资源">
      <div style={{ marginBottom: 20 }}>
        <Card title="快速清理">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
            {KILL_KINDS.map((kind) => (
              <div
                key={kind.value}
                style={{
                  padding: '14px 16px',
                  background: 'var(--bg-700)',
                  border: '1px solid var(--glass-border)',
                  borderRadius: 'var(--radius-sm)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-100)' }}>{kind.label}</span>
                </div>
                <p style={{ fontSize: 11, color: 'var(--ink-500)' }}>{kind.desc}</p>
                <button
                  className={`btn btn-sm ${kind.danger ? 'btn-danger' : 'btn-primary'}`}
                  onClick={() => handleKillKind(kind.value)}
                  disabled={killing}
                  style={{ marginTop: 4 }}
                >
                  清理
                </button>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <div style={{ marginBottom: 20 }}>
        <Card title="按指定类型清理">
          <FormBuilder
            fields={[
              { name: 'type', label: '实体类型', type: 'text', placeholder: '例如：minecraft:zombie 或 zombie', required: true },
            ]}
            onSubmit={handleKillType}
            submitLabel="清理指定类型"
            loading={killingType}
            layout="stack"
          />
        </Card>
      </div>

      <Card title="当前实体类型列表">
        {loading && !entityTypes ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={entityColumns}
            data={(entityTypes || []).slice().sort((a, b) => b.count - a.count)}
            emptyMessage="暂无实体数据"
            rowKey={(e, i) => `${e.type}-${i}`}
          />
        )}
      </Card>
    </PageContainer>
  )
}
