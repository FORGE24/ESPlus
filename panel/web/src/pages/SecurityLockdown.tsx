import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

export default function SecurityLockdown() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.runtime(), [], { interval: 10000 })
  const [busy, setBusy] = useState(false)
  const [confirmText, setConfirmText] = useState('')

  if (loading && !data) {
    return (
      <PageContainer title="紧急严打" subtitle="紧急锁定服务器 · 阻止所有玩家操作">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="紧急严打"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const rt = data as RuntimeSnapshot & Record<string, unknown>
  const lockdownActive = Boolean(rt.lockdown ?? rt.lockdown_active ?? rt.lockdownActive ?? false)

  const handleLockdown = async (on: boolean) => {
    if (on) {
      if (confirmText !== 'LOCKDOWN') {
        notify('error', '请输入 LOCKDOWN 以确认启用紧急严打')
        return
      }
    } else {
      if (!window.confirm('确认解除紧急严打状态？')) return
    }
    setBusy(true)
    try {
      await PanelAPI.payload(on ? 'lockdown_on' : 'lockdown_off', {})
      notify('success', on ? '紧急严打已启用' : '紧急严打已解除')
      setConfirmText('')
      refetch()
    } catch (e) {
      notify('error', `操作失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <PageContainer title="紧急严打" subtitle="紧急锁定服务器 · 阻止所有玩家操作">
      <Card title="当前状态" style={{ marginBottom: 20 }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '20px 24px',
          background: lockdownActive ? 'rgba(239, 68, 68, 0.1)' : 'rgba(34, 197, 94, 0.1)',
          border: `1px solid ${lockdownActive ? 'rgba(239, 68, 68, 0.3)' : 'rgba(34, 197, 94, 0.3)'}`,
          borderRadius: 'var(--radius-sm)',
        }}>
          <div style={{
            width: 12, height: 12, borderRadius: '50%',
            background: lockdownActive ? 'var(--danger)' : 'var(--ok)',
            boxShadow: `0 0 12px ${lockdownActive ? 'var(--danger)' : 'var(--ok)'}`,
          }} />
          <div>
            <div style={{ fontSize: 18, fontWeight: 700, color: lockdownActive ? 'var(--danger)' : 'var(--ok)' }}>
              {lockdownActive ? '严打模式已启用' : '服务器正常运行'}
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-400)', marginTop: 4 }}>
              {lockdownActive
                ? '所有玩家操作已被阻止，仅管理员可执行命令。'
                : '服务器运行正常，未启用严打模式。'}
            </div>
          </div>
        </div>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <Card title="启用紧急严打">
          <div className="flash-err" style={{ marginBottom: 16 }}>
            启用后将阻止所有玩家操作，仅管理员可执行命令。
          </div>
          <div style={{ marginBottom: 16 }}>
            <label className="label">输入 LOCKDOWN 确认</label>
            <input
              type="text"
              className="input"
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              placeholder="LOCKDOWN"
              style={{ fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.1em' }}
            />
          </div>
          <button
            className="btn btn-danger"
            disabled={busy || lockdownActive || confirmText !== 'LOCKDOWN'}
            onClick={() => handleLockdown(true)}
          >
            {busy ? '处理中…' : '启用严打'}
          </button>
        </Card>

        <Card title="解除紧急严打">
          <div style={{ marginBottom: 16 }}>
            <Badge type={lockdownActive ? 'danger' : 'ok'}>
              {lockdownActive ? '严打进行中' : '未启用严打'}
            </Badge>
          </div>
          <p style={{ fontSize: 13, color: 'var(--ink-400)', marginBottom: 16 }}>
            解除严打后，玩家操作将恢复正常。
          </p>
          <button
            className="btn btn-primary"
            disabled={busy || !lockdownActive}
            onClick={() => handleLockdown(false)}
          >
            {busy ? '处理中…' : '解除严打'}
          </button>
        </Card>
      </div>

      <Card title="严打模式说明" style={{ marginTop: 20 }}>
        <div style={{ display: 'grid', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="danger">高危</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              严打模式下所有玩家命令将被拦截，包括移动、交互、聊天等。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="warn">注意</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              适用于检测到大规模作弊或攻击时使用，请在问题排除后及时解除。
            </span>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
