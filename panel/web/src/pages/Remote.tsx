import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { RuntimeSnapshot } from '../lib/types'

export default function Remote() {
  const { data, loading, error } = useApi(() => PanelAPI.runtime(), [], { interval: 15000 })

  if (loading && !data) {
    return (
      <PageContainer title="远程运维 / SSH" subtitle="SSH 连接信息与远程管理指南">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="远程运维 / SSH"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const rt = data as RuntimeSnapshot & Record<string, unknown>
  const panelBind = String(rt.panel_bind ?? rt.panelBind ?? '0.0.0.0')
  const panelPort = String(rt.panel_port ?? rt.panelPort ?? rt.port ?? '—')
  const sshEnabled = Boolean(rt.ssh_enabled ?? rt.sshEnabled ?? true)
  const sshPort = String(rt.ssh_port ?? rt.sshPort ?? '22')
  const sshUser = String(rt.ssh_user ?? rt.sshUser ?? 'minecraft')
  const serverHost = String(rt.server_host ?? rt.serverHost ?? rt.server_name ?? 'localhost')
  const securityReady = Boolean(rt.security_ready ?? rt.securityReady ?? true)
  const javaVersion = String(rt.java_version ?? rt.javaVersion ?? '—')
  const osInfo = String(rt.os_info ?? rt.osInfo ?? rt.os ?? '—')

  return (
    <PageContainer title="远程运维 / SSH" subtitle="SSH 连接信息与远程管理指南">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="面板连接信息">
          <div style={{ display: 'grid', gap: 12 }}>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">面板绑定地址</div>
              <div className="mono" style={{ fontSize: 14, color: 'var(--accent-cyan)' }}>{panelBind}</div>
            </div>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">面板端口</div>
              <div className="mono" style={{ fontSize: 14, color: 'var(--accent-cyan)' }}>{panelPort}</div>
            </div>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">安全套件状态</div>
              <Badge type={securityReady ? 'ok' : 'danger'}>{securityReady ? '安全就绪' : '未就绪'}</Badge>
            </div>
          </div>
        </Card>

        <Card title="SSH 连接信息">
          <div style={{ display: 'grid', gap: 12 }}>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">SSH 状态</div>
              <Badge type={sshEnabled ? 'ok' : 'danger'}>{sshEnabled ? '已启用' : '已禁用'}</Badge>
            </div>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">SSH 端口</div>
              <div className="mono" style={{ fontSize: 14, color: 'var(--accent-cyan)' }}>{sshPort}</div>
            </div>
            <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
              <div className="label">SSH 用户</div>
              <div className="mono" style={{ fontSize: 14, color: 'var(--accent-cyan)' }}>{sshUser}</div>
            </div>
          </div>
        </Card>
      </div>

      <Card title="SSH 连接指南" style={{ marginBottom: 20 }}>
        <div style={{
          padding: '16px 20px',
          background: 'var(--bg-900)',
          borderRadius: 'var(--radius-sm)',
          border: '1px solid var(--glass-border)',
          fontFamily: 'JetBrains Mono, Consolas, monospace',
          fontSize: 13,
          lineHeight: 1.8,
          color: 'var(--ink-300)',
        }}>
          <div><span style={{ color: 'var(--ink-500)' }}># 连接到服务器</span></div>
          <div><span style={{ color: 'var(--accent-emerald)' }}>ssh</span> <span style={{ color: 'var(--accent-cyan)' }}>{sshUser}@{serverHost}</span> <span style={{ color: 'var(--accent-amber)' }}>-p</span> <span style={{ color: 'var(--accent-violet)' }}>{sshPort}</span></div>
          <br />
          <div><span style={{ color: 'var(--ink-500)' }}># 查看服务器进程</span></div>
          <div><span style={{ color: 'var(--accent-emerald)' }}>ps aux</span> | <span style={{ color: 'var(--accent-emerald)' }}>grep</span> <span style={{ color: 'var(--accent-cyan)' }}>java</span></div>
          <br />
          <div><span style={{ color: 'var(--ink-500)' }}># 查看实时日志</span></div>
          <div><span style={{ color: 'var(--accent-emerald)' }}>tail</span> <span style={{ color: 'var(--accent-amber)' }}>-f</span> logs/latest.log</div>
          <br />
          <div><span style={{ color: 'var(--ink-500)' }}># 重启服务器（systemd）</span></div>
          <div><span style={{ color: 'var(--accent-emerald)' }}>sudo systemctl</span> restart minecraft</div>
        </div>
      </Card>

      <Card title="系统环境">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">Java 版本</div>
            <div className="mono" style={{ fontSize: 13, color: 'var(--ink-300)' }}>{javaVersion}</div>
          </div>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">操作系统</div>
            <div className="mono" style={{ fontSize: 13, color: 'var(--ink-300)' }}>{osInfo}</div>
          </div>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">服务器名称</div>
            <div className="mono" style={{ fontSize: 13, color: 'var(--ink-300)' }}>{serverHost}</div>
          </div>
          <div style={{ padding: '12px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)' }}>
            <div className="label">服务器 ID</div>
            <div className="mono" style={{ fontSize: 13, color: 'var(--ink-300)' }}>{String(rt.server_id ?? rt.serverId ?? '—')}</div>
          </div>
        </div>
      </Card>

      <Card title="安全提示" style={{ marginTop: 20 }}>
        <div style={{ display: 'grid', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="warn">注意</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              SSH 连接信息由运行时配置提供，请勿在公共网络环境下使用默认密码登录。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="info">建议</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              建议使用密钥认证替代密码认证，并限制 SSH 端口访问来源 IP。
            </span>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
