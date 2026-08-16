import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

export default function StatusConnection() {
  const { notify } = useToast()
  const { data: runtime, loading, error, refetch } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 30000 },
  )
  const [savingMotd, setSavingMotd] = useState(false)

  if (loading && !runtime) {
    return (
      <PageContainer title="连接与端口" subtitle="服务器绑定地址 · 端口 · SSH 提示 · MOTD 编辑">
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer title="连接与端口">
        <div className="flash-err">{error}</div>
      </PageContainer>
    )
  }

  if (!runtime) return null

  const bindAddress = String(runtime['bind_address'] ?? runtime['server_ip'] ?? '0.0.0.0')
  const port = Number(runtime['server_port'] ?? runtime['port'] ?? 25565)
  const queryPort = Number(runtime['query_port'] ?? port)
  const rconPort = Number(runtime['rcon_port'] ?? 25575)
  const rconEnabled = Boolean(runtime['rcon_enabled'] ?? false)
  const sshHost = String(runtime['ssh_host'] ?? '')
  const sshPort = Number(runtime['ssh_port'] ?? 22)
  const sshUser = String(runtime['ssh_user'] ?? '')

  const connectionItems = [
    { label: '绑定地址', value: bindAddress, mono: true },
    { label: '游戏端口', value: String(port), mono: true, color: 'var(--accent-cyan)' },
    { label: 'Query 端口', value: String(queryPort), mono: true },
    { label: 'RCON 端口', value: String(rconPort), mono: true, color: rconEnabled ? 'var(--accent-emerald)' : 'var(--ink-500)' },
    { label: 'RCON 状态', value: rconEnabled ? '已启用' : '未启用', color: rconEnabled ? 'var(--ok)' : 'var(--ink-500)' },
    { label: '连接地址', value: `${bindAddress === '0.0.0.0' ? 'localhost' : bindAddress}:${port}`, mono: true, color: 'var(--accent-emerald)' },
  ]

  const sshItems = [
    { label: 'SSH 主机', value: sshHost || '未配置', mono: !!sshHost, color: sshHost ? 'var(--accent-cyan)' : 'var(--ink-500)' },
    { label: 'SSH 端口', value: sshPort ? String(sshPort) : '—', mono: true },
    { label: 'SSH 用户', value: sshUser || '未配置', mono: !!sshUser, color: sshUser ? 'var(--accent-cyan)' : 'var(--ink-500)' },
  ]

  async function handleMotdSubmit(formData: Record<string, string | number | boolean>) {
    const motd = String(formData.motd || '').trim()
    if (!motd) {
      notify('error', 'MOTD 不能为空')
      return
    }
    setSavingMotd(true)
    try {
      await PanelAPI.payload('set_motd', { motd })
      notify('success', 'MOTD 已更新，正在应用…')
      refetch()
    } catch (e) {
      notify('error', `更新失败: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setSavingMotd(false)
    }
  }

  return (
    <PageContainer title="连接与端口" subtitle="服务器绑定地址 · 端口信息 · SSH 远程提示 · MOTD 编辑">
      {/* Connection info */}
      <Card title="连接信息" style={{ marginBottom: 20 }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
          gap: 12,
        }}>
          {connectionItems.map((item) => (
            <div key={item.label} style={{
              padding: '14px 16px',
              background: 'var(--bg-700)',
              borderRadius: 6,
              border: '1px solid var(--glass-border)',
            }}>
              <div style={{
                fontSize: 11, fontWeight: 600, color: 'var(--ink-400)',
                textTransform: 'uppercase', letterSpacing: '0.05em',
              }}>
                {item.label}
              </div>
              <div style={{
                fontSize: 15, marginTop: 6,
                color: item.color || 'var(--ink-100)',
                fontFamily: item.mono ? 'JetBrains Mono, monospace' : 'inherit',
                fontWeight: 500,
              }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* SSH hint */}
      <Card title="SSH 远程提示" style={{ marginBottom: 20 }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
          gap: 12,
          marginBottom: 16,
        }}>
          {sshItems.map((item) => (
            <div key={item.label} style={{
              padding: '14px 16px',
              background: 'var(--bg-700)',
              borderRadius: 6,
              border: '1px solid var(--glass-border)',
            }}>
              <div style={{
                fontSize: 11, fontWeight: 600, color: 'var(--ink-400)',
                textTransform: 'uppercase', letterSpacing: '0.05em',
              }}>
                {item.label}
              </div>
              <div style={{
                fontSize: 15, marginTop: 6,
                color: item.color || 'var(--ink-100)',
                fontFamily: item.mono ? 'JetBrains Mono, monospace' : 'inherit',
                fontWeight: 500,
              }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>
        {sshHost && (
          <div style={{
            padding: '12px 16px',
            background: 'rgba(34,211,238,0.08)',
            border: '1px solid rgba(34,211,238,0.2)',
            borderRadius: 6,
            fontFamily: 'JetBrains Mono, monospace',
            fontSize: 13,
            color: 'var(--accent-cyan)',
          }}>
            $ ssh {sshUser || 'user'}@{sshHost}{sshPort && sshPort !== 22 ? ` -p ${sshPort}` : ''}
          </div>
        )}
        {!sshHost && (
          <div style={{
            padding: '12px 16px',
            background: 'var(--bg-700)',
            borderRadius: 6,
            fontSize: 13,
            color: 'var(--ink-400)',
          }}>
            SSH 连接信息未配置。请在服务器配置文件中设置 SSH 相关参数以启用远程运维。
          </div>
        )}
      </Card>

      {/* MOTD edit form */}
      <Card title="编辑 MOTD（服务器描述）">
        <FormBuilder
          fields={[
            {
              name: 'motd',
              label: 'MOTD 内容',
              type: 'textarea',
              defaultValue: String(runtime.motd ?? ''),
              placeholder: '输入服务器 MOTD…',
              required: true,
            },
          ]}
          onSubmit={handleMotdSubmit}
          loading={savingMotd}
          submitLabel="保存 MOTD"
          layout="stack"
        />
      </Card>
    </PageContainer>
  )
}
