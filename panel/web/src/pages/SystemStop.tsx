import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useToast } from '../lib/toast'
import { PanelAPI } from '../lib/api'

export default function SystemStop() {
  const { notify } = useToast()
  const [confirmText1, setConfirmText1] = useState('')
  const [confirmText2, setConfirmText2] = useState('')
  const [busy, setBusy] = useState(false)

  const canStop = confirmText1 === 'STOP' && confirmText2 === 'STOP'

  const handleStop = async () => {
    if (!canStop) {
      notify('error', '请在两个输入框中都输入 STOP 以确认操作')
      return
    }
    if (!window.confirm('最后一次确认：确定要停止服务器吗？所有在线玩家将被断开连接。')) return
    setBusy(true)
    try {
      await PanelAPI.payload('stop_server', {})
      notify('success', '服务器停止指令已发送')
      setConfirmText1('')
      setConfirmText2('')
    } catch (e) {
      notify('error', `停止失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <PageContainer title="停服" subtitle="停止服务器（高危操作）">
      <Card title="危险操作确认" style={{ marginBottom: 20 }}>
        <div style={{
          padding: '16px 20px',
          background: 'rgba(239, 68, 68, 0.1)',
          border: '1px solid rgba(239, 68, 68, 0.3)',
          borderRadius: 'var(--radius-sm)',
          marginBottom: 24,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <Badge type="danger">高危</Badge>
            <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--danger)' }}>
              停止服务器将断开所有玩家连接
            </span>
          </div>
          <p style={{ fontSize: 13, color: 'var(--ink-300)', lineHeight: 1.6 }}>
            此操作将向服务器发送停止指令，所有在线玩家将被踢出，服务器进程将被终止。
            请确保已提前保存世界数据并通知在线玩家。
          </p>
        </div>

        <div style={{ display: 'grid', gap: 20, maxWidth: 400 }}>
          <div>
            <label className="label">第一次确认：输入 STOP</label>
            <input
              type="text"
              className="input"
              value={confirmText1}
              onChange={(e) => setConfirmText1(e.target.value)}
              placeholder="STOP"
              style={{
                fontFamily: 'JetBrains Mono, monospace',
                letterSpacing: '0.15em',
                fontSize: 18,
                textAlign: 'center',
                borderColor: confirmText1 === 'STOP' ? 'var(--ok)' : 'var(--glass-border)',
              }}
            />
          </div>
          <div>
            <label className="label">第二次确认：再次输入 STOP</label>
            <input
              type="text"
              className="input"
              value={confirmText2}
              onChange={(e) => setConfirmText2(e.target.value)}
              placeholder="STOP"
              style={{
                fontFamily: 'JetBrains Mono, monospace',
                letterSpacing: '0.15em',
                fontSize: 18,
                textAlign: 'center',
                borderColor: confirmText2 === 'STOP' ? 'var(--ok)' : 'var(--glass-border)',
              }}
            />
          </div>
        </div>

        <div style={{ marginTop: 24, display: 'flex', alignItems: 'center', gap: 16 }}>
          <button
            className="btn btn-danger"
            disabled={busy || !canStop}
            onClick={handleStop}
            style={{ fontSize: 16, padding: '12px 40px' }}
          >
            {busy ? '停止中…' : '停止服务器'}
          </button>
          {canStop && (
            <Badge type="danger">双确认已通过</Badge>
          )}
        </div>
      </Card>

      <Card title="停服前检查清单">
        <div style={{ display: 'grid', gap: 10 }}>
          {[
            '已通知所有在线玩家服务器即将关闭',
            '已执行"保存所有世界"操作',
            '已确认无正在进行的批量操作',
            '已确认维护窗口时间',
            '已安排重启计划（如需要）',
          ].map((item, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{
                width: 20, height: 20, borderRadius: '50%',
                border: '2px solid var(--ink-500)', flexShrink: 0,
              }} />
              <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>{item}</span>
            </div>
          ))}
        </div>
      </Card>
    </PageContainer>
  )
}
