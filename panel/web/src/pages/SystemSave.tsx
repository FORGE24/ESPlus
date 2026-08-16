import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useToast } from '../lib/toast'
import { PanelAPI } from '../lib/api'

export default function SystemSave() {
  const { notify } = useToast()
  const [busy, setBusy] = useState<string | null>(null)

  const handleAction = async (action: string, label: string, confirmMsg?: string) => {
    if (confirmMsg && !window.confirm(confirmMsg)) return
    setBusy(action)
    try {
      await PanelAPI.payload(action, {})
      notify('success', `${label} 操作已执行`)
    } catch (e) {
      notify('error', `${label} 失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(null)
    }
  }

  const actions = [
    {
      action: 'save_all',
      label: '保存所有世界',
      desc: '立即将所有已加载区块写入磁盘，确保数据安全。',
      confirm: '确认立即保存所有世界数据？',
      btnClass: 'btn-primary',
      badgeType: 'ok' as const,
    },
    {
      action: 'save_on',
      label: '启用自动保存',
      desc: '开启服务器自动保存功能（默认每 6000 tick 自动保存）。',
      confirm: '确认启用自动保存？',
      btnClass: 'btn-primary',
      badgeType: 'info' as const,
    },
    {
      action: 'save_off',
      label: '禁用自动保存',
      desc: '关闭服务器自动保存功能。适用于进行大规模操作前减少磁盘 I/O。',
      confirm: '确认禁用自动保存？禁用期间请手动保存以避免数据丢失！',
      btnClass: 'btn-danger',
      badgeType: 'warn' as const,
    },
  ]

  return (
    <PageContainer title="保存与流畅" subtitle="世界数据保存控制 · 手动 / 自动">
      <div style={{ display: 'grid', gap: 20 }}>
        {actions.map((a) => (
          <Card key={a.action} title={a.label} actions={<Badge type={a.badgeType}>{a.action}</Badge>}>
            <p style={{ fontSize: 14, color: 'var(--ink-300)', marginBottom: 16, lineHeight: 1.6 }}>
              {a.desc}
            </p>
            <button
              className={`btn ${a.btnClass}`}
              disabled={busy !== null}
              onClick={() => handleAction(a.action, a.label, a.confirm)}
            >
              {busy === a.action ? '执行中…' : `执行 ${a.label}`}
            </button>
          </Card>
        ))}
      </div>

      <Card title="使用说明" style={{ marginTop: 20 }}>
        <div style={{ display: 'grid', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="info">提示</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              建议在进行重要操作前先执行"保存所有世界"，确保数据安全。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="warn">注意</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              禁用自动保存后，如果服务器意外崩溃可能导致数据丢失，请谨慎操作。
            </span>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
