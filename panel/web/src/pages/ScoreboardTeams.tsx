import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Team } from '../lib/types'

export default function ScoreboardTeams() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(
    () => api.get<Record<string, unknown>>('/api/governance/scoreboard/teams'),
    [],
    { interval: 15000 },
  )
  const [busy, setBusy] = useState(false)

  const teams: Team[] = (() => {
    if (!data) return []
    const arr = data.teams ?? data.list ?? []
    return (arr as unknown[]) as Team[]
  })()

  const handleCreate = async (formData: Record<string, string | number | boolean>) => {
    const name = String(formData.name || '')
    if (!name) {
      notify('error', '请输入队伍名称')
      return
    }
    setBusy(true)
    try {
      await PanelAPI.payload('scoreboard_team_add', {
        payload: `${name}|${String(formData.displayName || '')}|${String(formData.color || 'white')}`,
      })
      notify('success', `队伍 "${name}" 已创建`)
      refetch()
    } catch (e) {
      notify('error', `创建失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleRemove = async (name: string) => {
    if (!window.confirm(`确认删除队伍 "${name}"？`)) return
    setBusy(true)
    try {
      await PanelAPI.payload('scoreboard_team_remove', { payload: name })
      notify('success', `队伍 "${name}" 已删除`)
      refetch()
    } catch (e) {
      notify('error', `删除失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleJoin = async (formData: Record<string, string | number | boolean>) => {
    const team = String(formData.team || '')
    const player = String(formData.player || '')
    if (!team || !player) {
      notify('error', '请选择队伍并输入玩家名')
      return
    }
    setBusy(true)
    try {
      await PanelAPI.payload('scoreboard_team_join', { payload: `${team}|${player}` })
      notify('success', `玩家 ${player} 已加入队伍 ${team}`)
      refetch()
    } catch (e) {
      notify('error', `加入失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleLeave = async (formData: Record<string, string | number | boolean>) => {
    const player = String(formData.player || '')
    if (!player) {
      notify('error', '请输入玩家名')
      return
    }
    setBusy(true)
    try {
      await PanelAPI.payload('scoreboard_team_leave', { payload: player })
      notify('success', `玩家 ${player} 已离开队伍`)
      refetch()
    } catch (e) {
      notify('error', `离开失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="队伍管理" subtitle="记分板队伍 · 创建 · 删除 · 加入 · 离开">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="队伍管理"><div className="flash-err">{error}</div></PageContainer>

  const colorOptions = [
    'white', 'yellow', 'gold', 'aqua', 'red', 'green',
    'blue', 'light_purple', 'dark_purple', 'dark_red', 'dark_aqua',
    'dark_blue', 'dark_green', 'dark_gray', 'gray', 'black',
  ].map((c) => ({ value: c, label: c }))

  const columns = [
    { key: 'name', header: '队伍名称', className: 'mono' },
    { key: 'display_name', header: '显示名称', render: (t: any) => String(t.display_name ?? t.name) },
    {
      key: 'color', header: '颜色',
      render: (t: any) => (
        <span style={{ color: `var(--accent-${t.color === 'white' ? 'ink' : t.color === 'red' ? 'rose' : t.color === 'green' ? 'emerald' : 'cyan'})` }}>
          {t.color}
        </span>
      ),
    },
    {
      key: 'friendly_fire', header: '友伤',
      render: (t: any) => <Badge type={t.friendly_fire ? 'danger' : 'ok'}>{t.friendly_fire ? '允许' : '禁止'}</Badge>,
    },
    {
      key: 'members', header: '成员数',
      render: (t: any) => String(t.members?.length ?? 0),
    },
    {
      key: 'member_list', header: '成员列表',
      render: (t: any) => (t.members && t.members.length > 0 ? t.members.join(', ') : '—'),
    },
    {
      key: 'actions', header: '操作',
      render: (t: any) => (
        <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => handleRemove(t.name)}>
          删除队伍
        </button>
      ),
    },
  ]

  return (
    <PageContainer title="队伍管理" subtitle="记分板队伍 · 创建 · 删除 · 加入 · 离开">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="创建队伍">
          <FormBuilder
            fields={[
              { name: 'name', label: '队伍名称', placeholder: '如: red_team', required: true, width: '100%' },
              { name: 'displayName', label: '显示名称（可选）', placeholder: '在游戏中显示的名称', width: '100%' },
              { name: 'color', label: '颜色', type: 'select', options: colorOptions, defaultValue: 'white', width: '100%' },
            ]}
            onSubmit={handleCreate}
            submitLabel="创建队伍"
            loading={busy}
            layout="stack"
          />
        </Card>

        <Card title="加入 / 离开队伍">
          <FormBuilder
            fields={[
              {
                name: 'team', label: '队伍', type: 'select',
                options: teams.map((t) => ({ value: t.name, label: t.display_name || t.name })),
                width: '100%',
              },
              { name: 'player', label: '玩家名', placeholder: '输入玩家名', required: true, width: '100%' },
            ]}
            onSubmit={handleJoin}
            submitLabel="加入队伍"
            loading={busy}
            layout="stack"
            actions={
              <button
                type="button"
                className="btn btn-ghost"
                disabled={busy}
                onClick={() => {
                  const form = document.querySelector('form')
                  const input = form?.querySelector('input[name="player"]') as HTMLInputElement
                  if (input?.value) handleLeave({ player: input.value })
                }}
              >
                离开队伍
              </button>
            }
          />
        </Card>
      </div>

      <Card title="队伍列表">
        <DataTable
          columns={columns}
          data={teams as unknown as Record<string, unknown>[]}
          emptyMessage="暂无队伍"
          rowKey={(t) => (t as unknown as Team).name}
        />
      </Card>
    </PageContainer>
  )
}
