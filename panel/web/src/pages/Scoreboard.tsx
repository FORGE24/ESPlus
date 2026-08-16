import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { ScoreboardObjective } from '../lib/types'

export default function Scoreboard() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(
    () => api.get<Record<string, unknown>>('/api/governance/scoreboard'),
    [],
    { interval: 15000 },
  )
  const [busy, setBusy] = useState(false)

  const objectives: ScoreboardObjective[] = (() => {
    if (!data) return []
    const arr = data.objectives ?? data.scoreboardObjectives ?? data.list ?? []
    return (arr as unknown[]) as ScoreboardObjective[]
  })()

  const handleAdd = async (formData: Record<string, string | number | boolean>) => {
    const name = String(formData.name || '')
    const criteria = String(formData.criteria || 'dummy')
    const displayName = String(formData.displayName || '')
    if (!name) {
      notify('error', '请输入目标名称')
      return
    }
    setBusy(true)
    try {
      await PanelAPI.payload('scoreboard_add', {
        payload: `${name}|${criteria}|${displayName}`,
      })
      notify('success', `记分板目标 "${name}" 已添加`)
      refetch()
    } catch (e) {
      notify('error', `添加失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleRemove = async (name: string) => {
    if (!window.confirm(`确认删除记分板目标 "${name}"？相关分数将被清除。`)) return
    setBusy(true)
    try {
      await PanelAPI.payload('scoreboard_remove', { payload: name })
      notify('success', `记分板目标 "${name}" 已删除`)
      refetch()
    } catch (e) {
      notify('error', `删除失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleSetDisplay = async (formData: Record<string, string | number | boolean>) => {
    const slot = String(formData.slot || '')
    const name = String(formData.name || '')
    setBusy(true)
    try {
      await PanelAPI.payload('scoreboard_display', { payload: `${slot}|${name}` })
      notify('success', `已设置显示位 ${slot} 为 "${name}"`)
      refetch()
    } catch (e) {
      notify('error', `设置失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="记分板目标" subtitle="记分板目标管理 · 添加 · 删除 · 显示">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="记分板目标"><div className="flash-err">{error}</div></PageContainer>

  const columns = [
    { key: 'name', header: '目标名称', className: 'mono' },
    { key: 'criteria', header: '判定条件', render: (o: any) => <Badge type="info">{o.criteria}</Badge> },
    { key: 'display_name', header: '显示名称', render: (o: any) => String(o.display_name ?? o.name) },
    {
      key: 'actions', header: '操作',
      render: (o: any) => (
        <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => handleRemove(o.name)}>
          删除
        </button>
      ),
    },
  ]

  return (
    <PageContainer title="记分板目标" subtitle="记分板目标管理 · 添加 · 删除 · 显示">
      <Card title="添加记分板目标" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'name', label: '目标名称', placeholder: '如: kills, deaths', required: true, width: '100%' },
            {
              name: 'criteria', label: '判定条件', type: 'select',
              options: [
                { value: 'dummy', label: 'dummy（手动设置）' },
                { value: 'deathCount', label: 'deathCount（死亡次数）' },
                { value: 'playerKillCount', label: 'playerKillCount（击杀玩家数）' },
                { value: 'totalKillCount', label: 'totalKillCount（总击杀数）' },
                { value: 'health', label: 'health（生命值）' },
                { value: 'experience', label: 'experience（经验值）' },
                { value: 'food', label: 'food（饥饿值）' },
                { value: 'air', label: 'air（氧气值）' },
                { value: 'armor', label: 'armor（护甲值）' },
              ],
              defaultValue: 'dummy', width: '100%',
            },
            { name: 'displayName', label: '显示名称（可选）', placeholder: '在侧边栏显示的名称', width: '100%' },
          ]}
          onSubmit={handleAdd}
          submitLabel="添加目标"
          loading={busy}
        />
      </Card>

      <Card title="设置显示位" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            {
              name: 'slot', label: '显示位置', type: 'select',
              options: [
                { value: 'sidebar', label: 'sidebar（侧边栏）' },
                { value: 'list', label: 'list（Tab 列表）' },
                { value: 'belowName', label: 'belowName（名称下方）' },
              ],
              defaultValue: 'sidebar', width: '100%',
            },
            {
              name: 'name', label: '目标名称', type: 'select',
              options: objectives.map((o) => ({ value: o.name, label: o.display_name || o.name })),
              required: true, width: '100%',
            },
          ]}
          onSubmit={handleSetDisplay}
          submitLabel="设置显示"
          loading={busy}
        />
      </Card>

      <Card title="记分板目标列表">
        <DataTable
          columns={columns}
          data={objectives as unknown as Record<string, unknown>[]}
          emptyMessage="暂无记分板目标"
          rowKey={(o) => (o as unknown as ScoreboardObjective).name}
        />
      </Card>
    </PageContainer>
  )
}
