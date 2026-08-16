import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useToast } from '../lib/toast'
import { PanelAPI } from '../lib/api'

export default function SystemReload() {
  const { notify } = useToast()
  const [confirmText, setConfirmText] = useState('')
  const [busy, setBusy] = useState(false)

  const handleReload = async () => {
    if (confirmText !== 'RELOAD') {
      notify('error', '请输入 RELOAD 以确认操作')
      return
    }
    setBusy(true)
    try {
      await PanelAPI.payload('reload', {})
      notify('success', '服务器重载已触发')
      setConfirmText('')
    } catch (e) {
      notify('error', `重载失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <PageContainer title="重载" subtitle="重新加载服务器配置与数据包">
      <Card title="操作确认" style={{ marginBottom: 20 }}>
        <div className="flash-err" style={{ marginBottom: 20 }}>
          重载操作将重新加载服务器配置文件、 advancements、functions 和 loot tables。
          此操作可能导致短暂卡顿，但不会断开玩家连接。
        </div>

        <div style={{ marginBottom: 20 }}>
          <label className="label">输入 RELOAD 确认操作</label>
          <input
            type="text"
            className="input"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder="RELOAD"
            style={{
              fontFamily: 'JetBrains Mono, monospace',
              letterSpacing: '0.15em',
              fontSize: 18,
              textAlign: 'center',
              maxWidth: 300,
            }}
          />
        </div>

        <button
          className="btn btn-primary"
          disabled={busy || confirmText !== 'RELOAD'}
          onClick={handleReload}
          style={{ fontSize: 15, padding: '10px 32px' }}
        >
          {busy ? '重载中…' : '执行重载'}
        </button>
      </Card>

      <Card title="重载说明">
        <div style={{ display: 'grid', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="info">说明</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              重载会重新读取配置文件，但某些核心配置（如端口、世界生成）需要重启服务器才能生效。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="warn">注意</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              大型服务器重载可能需要数秒时间，期间可能出现短暂卡顿。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="ok">安全</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              重载不会断开已有玩家连接，也不影响在线游戏。
            </span>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
