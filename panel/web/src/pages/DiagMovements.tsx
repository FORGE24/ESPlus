import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { MovementSample } from '../lib/types'

export default function DiagMovements() {
  const { notify } = useToast()
  const [player, setPlayer] = useState('')
  const [activePlayer, setActivePlayer] = useState('')
  const [busy, setBusy] = useState(false)

  const { data, loading, error } = useApi(
    () => activePlayer
      ? api.get<MovementSample[]>(`/api/players/movements?q=${encodeURIComponent(activePlayer)}`)
      : Promise.resolve([] as MovementSample[]),
    [activePlayer],
    { interval: 15000 },
  )

  const handleSearch = async (formData: Record<string, string | number | boolean>) => {
    const p = String(formData.player || '').trim()
    if (!p) {
      notify('error', '请输入玩家名或 UUID')
      return
    }
    setBusy(true)
    setActivePlayer(p)
    setPlayer(p)
    setBusy(false)
  }

  if (loading && activePlayer && !data) {
    return (
      <PageContainer title="移动轨迹" subtitle="玩家移动轨迹查询与追踪">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="移动轨迹"><div className="flash-err">{error}</div></PageContainer>

  const movements: MovementSample[] = data || []

  const columns = [
    { key: 'ts', header: '时间', className: 'mono' },
    {
      key: 'dimension', header: '维度',
      render: (m: any) => {
        const dimMap: Record<string, string> = {
          'minecraft:overworld': '主世界',
          'minecraft:the_nether': '下界',
          'minecraft:the_end': '末地',
        }
        return <Badge type="info">{dimMap[m.dimension] || m.dimension}</Badge>
      },
    },
    { key: 'x', header: 'X', className: 'mono', render: (m: any) => m.x.toFixed(1) },
    { key: 'y', header: 'Y', className: 'mono', render: (m: any) => m.y.toFixed(1) },
    { key: 'z', header: 'Z', className: 'mono', render: (m: any) => m.z.toFixed(1) },
  ]

  return (
    <PageContainer title="移动轨迹" subtitle="玩家移动轨迹查询与追踪">
      <Card title="查询玩家移动轨迹" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'player', label: '玩家名 / UUID', placeholder: '输入玩家名或 UUID', required: true, defaultValue: player, width: '100%' },
          ]}
          onSubmit={handleSearch}
          submitLabel={busy ? '查询中…' : '查询轨迹'}
          loading={busy}
          layout="stack"
        />
      </Card>

      {activePlayer && (
        <>
          <div className="stat-grid" style={{ marginBottom: 20 }}>
            <StatCard label="查询玩家" value={activePlayer} color="cyan" />
            <StatCard label="轨迹点数" value={movements.length} color="violet" />
            {movements.length > 0 && (
              <StatCard label="起始时间" value={movements[0].ts} color="emerald" />
            )}
            {movements.length > 0 && (
              <StatCard label="结束时间" value={movements[movements.length - 1].ts} color="amber" />
            )}
          </div>

          <Card title="移动轨迹记录">
            <DataTable
              columns={columns}
              data={movements as unknown as Record<string, unknown>[]}
              emptyMessage={`未找到玩家 ${activePlayer} 的移动记录`}
              rowKey={(m, i) => `${(m as unknown as MovementSample).ts}-${i}`}
            />
          </Card>

          {movements.length > 1 && (
            <Card title="轨迹摘要" style={{ marginTop: 20 }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
                <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
                  <div className="label">X 范围</div>
                  <div className="mono" style={{ fontSize: 13, color: 'var(--accent-cyan)' }}>
                    {Math.min(...movements.map((m) => m.x)).toFixed(1)} ~ {Math.max(...movements.map((m) => m.x)).toFixed(1)}
                  </div>
                </div>
                <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
                  <div className="label">Y 范围</div>
                  <div className="mono" style={{ fontSize: 13, color: 'var(--accent-cyan)' }}>
                    {Math.min(...movements.map((m) => m.y)).toFixed(1)} ~ {Math.max(...movements.map((m) => m.y)).toFixed(1)}
                  </div>
                </div>
                <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
                  <div className="label">Z 范围</div>
                  <div className="mono" style={{ fontSize: 13, color: 'var(--accent-cyan)' }}>
                    {Math.min(...movements.map((m) => m.z)).toFixed(1)} ~ {Math.max(...movements.map((m) => m.z)).toFixed(1)}
                  </div>
                </div>
              </div>
            </Card>
          )}
        </>
      )}

      {!activePlayer && (
        <Card title="使用说明">
          <p style={{ fontSize: 14, color: 'var(--ink-400)', lineHeight: 1.6 }}>
            输入玩家名或 UUID 以查询该玩家的移动轨迹记录。系统会返回保留期内的所有移动数据点，
            包括时间戳、所在维度和 XYZ 坐标。
          </p>
        </Card>
      )}
    </PageContainer>
  )
}
