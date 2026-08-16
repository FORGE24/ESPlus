import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { OnlinePlayer } from '../lib/types'

type ModalState = { type: string; player: OnlinePlayer } | null

interface FormField {
  name: string
  label: string
  type?: 'text' | 'password' | 'number' | 'select' | 'textarea' | 'checkbox'
  placeholder?: string
  options?: { value: string; label: string }[]
  defaultValue?: string | number | boolean
  required?: boolean
  min?: number
  max?: number
  width?: string
}

const GM_COLORS: Record<string, 'ok' | 'info' | 'warn' | 'violet'> = {
  SURVIVAL: 'ok',
  CREATIVE: 'info',
  ADVENTURE: 'warn',
  SPECTATOR: 'violet',
}

const DIM_LABELS: Record<string, string> = {
  'minecraft:overworld': '主世界',
  'minecraft:the_nether': '下界',
  'minecraft:the_end': '末地',
}

function healthColor(hp?: number) {
  if (hp === undefined) return 'var(--ink-500)'
  if (hp > 15) return 'var(--ok)'
  if (hp > 8) return 'var(--warn)'
  return 'var(--danger)'
}

export default function Players() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.playersPage(), [], { interval: 10000 })
  const [modal, setModal] = useState<ModalState>(null)
  const [busy, setBusy] = useState(false)

  const online = data?.online ?? []

  // ── Quick actions (no form needed) ──────────────────────────
  async function quickAction(type: 'heal' | 'feed' | 'clear' | 'extinguish', p: OnlinePlayer) {
    const labels = { heal: '治疗', feed: '喂食', clear: '清空背包', extinguish: '灭火' }
    setBusy(true)
    try {
      await PanelAPI[type](p.name, p.uuid)
      notify('success', `${labels[type]} → ${p.name} 已执行`)
      refetch()
    } catch (e) {
      notify('error', `${labels[type]}失败: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  // ── Modal form submission ───────────────────────────────────
  async function handleModalSubmit(formData: Record<string, string | number | boolean>) {
    if (!modal) return
    const { player, type } = modal
    setBusy(true)
    try {
      const reason = String(formData.reason || '')
      switch (type) {
        case 'kick':
          await PanelAPI.kick(player.name, player.uuid, reason)
          notify('success', `已踢出 ${player.name}`)
          break
        case 'ban':
          await PanelAPI.ban(player.name, player.uuid, reason)
          notify('success', `已封禁 ${player.name}`)
          break
        case 'tempBan':
          await PanelAPI.tempBan(player.name, player.uuid, Number(formData.minutes) || 60, reason)
          notify('success', `已限时封禁 ${player.name}`)
          break
        case 'gamemode':
          await PanelAPI.gamemode(player.name, player.uuid, String(formData.mode))
          notify('success', `已切换 ${player.name} 的游戏模式`)
          break
        case 'teleport':
          await PanelAPI.teleport({
            player: player.name,
            uuid: player.uuid,
            x: Number(formData.x),
            y: Number(formData.y),
            z: Number(formData.z),
            dimension: String(formData.dimension || ''),
          })
          notify('success', `已传送 ${player.name}`)
          break
        case 'spawnpoint':
          await PanelAPI.spawnpoint({
            player: player.name,
            uuid: player.uuid,
            x: Number(formData.x),
            y: Number(formData.y),
            z: Number(formData.z),
          })
          notify('success', `已设置 ${player.name} 的出生点`)
          break
      }
      setModal(null)
      refetch()
    } catch (e) {
      notify('error', `操作失败: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  // ── Modal config ────────────────────────────────────────────
  const modalConfig: Record<string, { title: string; fields: FormField[] }> = {
    kick: { title: '踢出玩家', fields: [{ name: 'reason', label: '原因', type: 'text' as const, placeholder: '请输入踢出原因' }] },
    ban: { title: '封禁玩家', fields: [{ name: 'reason', label: '原因', type: 'text' as const, placeholder: '请输入封禁原因' }] },
    tempBan: { title: '限时封禁', fields: [
      { name: 'minutes', label: '时长（分钟）', type: 'number' as const, defaultValue: 60, min: 1, width: '100%' },
      { name: 'reason', label: '原因', type: 'text' as const, placeholder: '请输入封禁原因' },
    ]},
    gamemode: { title: '切换游戏模式', fields: [
      { name: 'mode', label: '模式', type: 'select' as const, options: [
        { value: 'SURVIVAL', label: '生存' },
        { value: 'CREATIVE', label: '创造' },
        { value: 'ADVENTURE', label: '冒险' },
        { value: 'SPECTATOR', label: '旁观' },
      ]},
    ]},
    teleport: { title: '传送玩家', fields: [
      { name: 'x', label: 'X 坐标', type: 'number' as const, required: true, width: 'calc(50% - 8px)' },
      { name: 'y', label: 'Y 坐标', type: 'number' as const, required: true, width: 'calc(50% - 8px)' },
      { name: 'z', label: 'Z 坐标', type: 'number' as const, required: true, width: 'calc(50% - 8px)' },
      { name: 'dimension', label: '维度', type: 'select' as const, width: 'calc(50% - 8px)', options: [
        { value: '', label: '当前维度' },
        { value: 'minecraft:overworld', label: '主世界' },
        { value: 'minecraft:the_nether', label: '下界' },
        { value: 'minecraft:the_end', label: '末地' },
      ]},
    ]},
    spawnpoint: { title: '设置出生点', fields: [
      { name: 'x', label: 'X 坐标', type: 'number' as const, required: true, width: 'calc(33% - 8px)' },
      { name: 'y', label: 'Y 坐标', type: 'number' as const, required: true, width: 'calc(33% - 8px)' },
      { name: 'z', label: 'Z 坐标', type: 'number' as const, required: true, width: 'calc(33% - 8px)' },
    ]},
  }

  const modalFields = modal ? (modalConfig[modal.type]?.fields ?? []) : []
  const modalTitle = modal ? (modalConfig[modal.type]?.title ?? '') : ''

  const columns = [
    {
      key: 'name', header: '玩家', render: (p: any) => (
        <div>
          <span style={{ fontWeight: 600, color: 'var(--ink-100)' }}>{p.name}</span>
          {p.display_name && p.display_name !== p.name && (
            <span style={{ color: 'var(--ink-400)', fontSize: 12, marginLeft: 6 }}>({p.display_name})</span>
          )}
          <div className="mono" style={{ fontSize: 11, color: 'var(--ink-500)' }}>{p.uuid}</div>
        </div>
      ),
    },
    {
      key: 'health', header: '生命值', render: (p: any) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ color: healthColor(p.health), fontWeight: 600 }}>{p.health !== undefined ? `${p.health.toFixed(1)}❤` : '—'}</span>
          <span style={{ color: 'var(--accent-amber)', fontSize: 12 }}>{p.food !== undefined ? `${p.food}🍗` : ''}</span>
        </div>
      ),
    },
    {
      key: 'gamemode', header: '模式', render: (p: any) => (
        <Badge type={GM_COLORS[p.gamemode] || 'info'}>{p.gamemode || '—'}</Badge>
      ),
    },
    {
      key: 'dimension', header: '维度', render: (p: any) => (
        <span style={{ color: 'var(--accent-violet)', fontSize: 12 }}>{DIM_LABELS[p.dimension] || p.dimension || '—'}</span>
      ),
    },
    {
      key: 'coords', header: '坐标', render: (p: any) => (
        <span className="mono" style={{ fontSize: 12, color: 'var(--ink-300)' }}>
          {p.x !== undefined ? `${Math.round(p.x)}, ${Math.round(p.y)}, ${Math.round(p.z)}` : '—'}
        </span>
      ),
    },
    {
      key: 'ping', header: '延迟', render: (p: any) => {
        const ping = p.ping ?? 0
        const color = ping < 50 ? 'var(--ok)' : ping < 150 ? 'var(--warn)' : 'var(--danger)'
        return <span style={{ color, fontWeight: 600 }}>{ping}ms</span>
      },
    },
    {
      key: 'actions', header: '操作', width: '320px', render: (p: any) => (
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => quickAction('heal', p)}>治疗</button>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => quickAction('feed', p)}>喂食</button>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => quickAction('clear', p)}>清空</button>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => setModal({ type: 'gamemode', player: p })}>模式</button>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => setModal({ type: 'teleport', player: p })}>传送</button>
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => setModal({ type: 'spawnpoint', player: p })}>出生点</button>
          <button className="btn btn-sm" style={{ background: 'rgba(245,158,11,0.15)', color: 'var(--warn)', border: '1px solid rgba(245,158,11,0.3)' }} disabled={busy} onClick={() => setModal({ type: 'kick', player: p })}>踢出</button>
          <button className="btn btn-sm" style={{ background: 'rgba(239,68,68,0.15)', color: 'var(--danger)', border: '1px solid rgba(239,68,68,0.3)' }} disabled={busy} onClick={() => setModal({ type: 'tempBan', player: p })}>限时封禁</button>
          <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => setModal({ type: 'ban', player: p })}>封禁</button>
        </div>
      ),
    },
  ]

  return (
    <PageContainer title="在线玩家" subtitle="实时在线列表 · 管理操作 · 每 10 秒自动刷新">
      {/* Stats */}
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="在线人数" value={online.length} color="cyan" />
        <StatCard label="最大玩家" value={data?.online?.[0] ? '—' : 0} color="violet" />
        <StatCard label="封禁列表" value={(data?.bans as unknown[] | undefined)?.length ?? 0} color="rose" />
        <StatCard label="操作中" value={busy ? 1 : 0} color="amber" />
      </div>

      {loading && !data ? (
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      ) : error ? (
        <div className="flash-err">{error}</div>
      ) : (
        <Card title={`在线玩家（${online.length}）`}>
          <DataTable
            columns={columns}
            data={online as unknown as Record<string, unknown>[]}
            emptyMessage="当前没有玩家在线"
            rowKey={(p) => (p as unknown as OnlinePlayer).uuid}
          />
        </Card>
      )}

      {/* Ban list preview */}
      {data?.bans && (data.bans as unknown[]).length > 0 && (
        <Card title="封禁列表" style={{ marginTop: 20 }}>
          <DataTable
            columns={[
              { key: 'name', header: '玩家' },
              { key: 'uuid', header: 'UUID', className: 'mono' },
              { key: 'reason', header: '原因' },
              { key: 'expires', header: '到期' },
              {
                key: 'actions', header: '操作', render: (b: any) => (
                  <button
                    className="btn btn-ghost btn-sm"
                    disabled={busy}
                    onClick={async () => {
                      setBusy(true)
                      try {
                        await PanelAPI.unban(b.name, b.uuid)
                        notify('success', `已解封 ${b.name}`)
                        refetch()
                      } catch (e) {
                        notify('error', `解封失败: ${e instanceof Error ? e.message : String(e)}`)
                      } finally {
                        setBusy(false)
                      }
                    }}
                  >解封</button>
                ),
              },
            ]}
            data={(data.bans as unknown[]).map((b) => b as Record<string, unknown>)}
            emptyMessage="无封禁记录"
            rowKey={(b) => (b as any).uuid || (b as any).name}
          />
        </Card>
      )}

      {/* Modal */}
      {modal && (
        <div
          onClick={() => setModal(null)}
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
            backdropFilter: 'blur(4px)', display: 'flex',
            alignItems: 'center', justifyContent: 'center', zIndex: 1000,
          }}
        >
          <div
            className="glass-card"
            onClick={(e) => e.stopPropagation()}
            style={{ width: 'min(520px, 92vw)', padding: 28 }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-100)', margin: 0 }}>{modalTitle}</h3>
              <button className="btn btn-ghost btn-sm" onClick={() => setModal(null)}>关闭</button>
            </div>
            <div style={{
              padding: '10px 14px', background: 'var(--bg-700)', borderRadius: 6,
              marginBottom: 20, display: 'flex', alignItems: 'center', gap: 10,
            }}>
              <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>目标玩家:</span>
              <span style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>{modal.player.name}</span>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-500)' }}>{modal.player.uuid}</span>
            </div>
            <FormBuilder
              fields={modalFields}
              onSubmit={handleModalSubmit}
              loading={busy}
              layout="stack"
              submitLabel="确认执行"
              actions={<button type="button" className="btn btn-ghost" onClick={() => setModal(null)}>取消</button>}
            />
          </div>
        </div>
      )}
    </PageContainer>
  )
}
