import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'

const FIELD_LABELS: Record<string, string> = {
  name: '玩家名',
  display_name: '显示名',
  uuid: 'UUID',
  health: '生命值',
  food: '饥饿值',
  saturation: '饱和度',
  level: '经验等级',
  exp: '经验值',
  gamemode: '游戏模式',
  dimension: '所在维度',
  x: 'X 坐标',
  y: 'Y 坐标',
  z: 'Z 坐标',
  ping: '网络延迟',
  ip: 'IP 地址',
  first_joined: '首次加入',
  last_seen: '最后在线',
  world: '世界',
  location: '位置',
  op: 'OP 状态',
  whitelisted: '白名单',
  banned: '封禁状态',
  balance: '余额',
  adv_count: '成就数',
  playtime: '游戏时长',
  kills: '击杀',
  deaths: '死亡',
}

const SKIP_KEYS = new Set(['inventory', 'ender_chest', 'effects', 'permissions', 'stats_detail'])

function formatValue(key: string, val: unknown): string {
  if (val === null || val === undefined) return '—'
  if (typeof val === 'boolean') return val ? '是' : '否'
  if (typeof val === 'number') {
    if (key === 'health') return `${val.toFixed(1)} ❤`
    if (key === 'ping') return `${val} ms`
    if (key === 'x' || key === 'y' || key === 'z') return String(Math.round(val))
    return String(val)
  }
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function valueColor(key: string, val: unknown): string {
  if (key === 'health' && typeof val === 'number') {
    if (val > 15) return 'var(--ok)'
    if (val > 8) return 'var(--warn)'
    return 'var(--danger)'
  }
  if (key === 'ping' && typeof val === 'number') {
    if (val < 50) return 'var(--ok)'
    if (val < 150) return 'var(--warn)'
    return 'var(--danger)'
  }
  if (key === 'op' || key === 'banned') {
    return val ? 'var(--danger)' : 'var(--ok)'
  }
  return 'var(--ink-100)'
}

export default function PlayerProfile() {
  const { notify } = useToast()
  const [query, setQuery] = useState('')
  const [searched, setSearched] = useState(false)

  const { data, loading, error } = useApi(
    () => searched && query ? PanelAPI.playerProfile(query) : Promise.resolve(null),
    [query, searched],
  )

  const handleSearch = (formData: Record<string, string | number | boolean>) => {
    const q = String(formData.query || '').trim()
    if (!q) {
      notify('error', '请输入玩家名或 UUID')
      return
    }
    setQuery(q)
    setSearched(true)
  }

  const profileEntries = data
    ? Object.entries(data).filter(([k]) => !SKIP_KEYS.has(k))
    : []

  return (
    <PageContainer title="玩家档案" subtitle="查询玩家详细信息 · 属性 · 位置 · 状态">
      <Card title="搜索玩家" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            { name: 'query', label: '玩家名 / UUID', type: 'text', placeholder: '输入玩家名或 UUID…', required: true },
          ]}
          onSubmit={handleSearch}
          submitLabel="查询"
          layout="stack"
        />
      </Card>

      {loading && searched ? (
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      ) : error ? (
        <div className="flash-err">{error}</div>
      ) : data && profileEntries.length > 0 ? (
        <>
          {/* Highlight card */}
          <Card style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
              <div style={{
                width: 56, height: 56, borderRadius: '50%',
                background: 'linear-gradient(135deg, var(--accent-cyan), var(--accent-violet))',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 24, fontWeight: 700, color: 'var(--bg-900)', flexShrink: 0,
              }}>
                {String(data.name || data.uuid || '?').charAt(0).toUpperCase()}
              </div>
              <div>
                <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--ink-100)' }}>
                  {String(data.name || '未知')}
                </div>
                <div className="mono" style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: 4 }}>
                  {String(data.uuid || '')}
                </div>
              </div>
              <div style={{ marginLeft: 'auto', display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {data.gamemode ? (
                  <span className="badge badge-info">{String(data.gamemode)}</span>
                ) : null}
                {data.health !== undefined && (
                  <span className="badge badge-danger">{Number(data.health).toFixed(1)} ❤</span>
                )}
                {data.ping !== undefined && (
                  <span className="badge badge-ok">{String(data.ping)} ms</span>
                )}
              </div>
            </div>
          </Card>

          {/* Key-value grid */}
          <Card title="详细属性">
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
              gap: 12,
            }}>
              {profileEntries.map(([key, val]) => (
                <div key={key} style={{
                  padding: '12px 16px',
                  background: 'var(--bg-700)',
                  borderRadius: 6,
                  border: '1px solid var(--glass-border)',
                }}>
                  <div style={{
                    fontSize: 11, fontWeight: 600, color: 'var(--ink-400)',
                    textTransform: 'uppercase', letterSpacing: '0.05em',
                  }}>
                    {FIELD_LABELS[key] || key}
                  </div>
                  <div style={{
                    marginTop: 4,
                    color: valueColor(key, val),
                    fontFamily: key === 'uuid' || key === 'ip' ? 'JetBrains Mono, monospace' : 'inherit',
                    fontSize: key === 'uuid' ? 12 : 14,
                    wordBreak: 'break-all',
                  }}>
                    {formatValue(key, val)}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </>
      ) : searched && !loading ? (
        <Card>
          <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--ink-500)' }}>
            <p style={{ fontSize: 14 }}>未找到匹配的玩家档案</p>
            <p style={{ fontSize: 12, marginTop: 8 }}>请检查玩家名或 UUID 是否正确</p>
          </div>
        </Card>
      ) : null}
    </PageContainer>
  )
}
