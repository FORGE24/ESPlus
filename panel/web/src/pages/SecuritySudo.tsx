import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { RuntimeSnapshot } from '../lib/types'

export default function SecuritySudo() {
  const { data, loading, error } = useApi(() => PanelAPI.runtime(), [], { interval: 15000 })

  if (loading && !data) {
    return (
      <PageContainer title="sudo 策略" subtitle="超级权限会话与受保护命令配置">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="sudo 策略"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const rt = data as RuntimeSnapshot & Record<string, unknown>
  const sessionMinutes = Number(rt.sudo_session_minutes ?? rt.sudoSessionMinutes ?? 30)
  const maxFailed = Number(rt.sudo_max_failed_attempts ?? rt.sudoMaxFailedAttempts ?? 5)
  const lockMinutes = Number(rt.sudo_lock_minutes ?? rt.sudoLockMinutes ?? 15)
  const auditRetentionDays = Number(rt.audit_retention_days ?? rt.auditRetentionDays ?? 90)
  const protectedCmds: string[] = (rt.sudo_protected_commands ?? rt.sudoProtectedCommands ?? []) as string[]

  const policyItems = [
    { label: 'sudo 会话有效期', value: `${sessionMinutes} 分钟`, desc: '超级权限会话在此时长后自动过期' },
    { label: '最大失败尝试次数', value: `${maxFailed} 次`, desc: '超过此次数后账户将被临时锁定' },
    { label: '锁定时长', value: `${lockMinutes} 分钟`, desc: '触发锁定后的冷却时间' },
    { label: '审计保留天数', value: `${auditRetentionDays} 天`, desc: '安全审计记录的保留周期' },
  ]

  return (
    <PageContainer title="sudo 策略" subtitle="超级权限会话与受保护命令配置（只读）">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        {policyItems.map((item) => (
          <Card key={item.label} title={item.label}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
              <span style={{ fontSize: 28, fontWeight: 700, color: 'var(--accent-cyan)', fontFamily: 'Syne, Inter, sans-serif' }}>
                {item.value}
              </span>
            </div>
            <p style={{ fontSize: 13, color: 'var(--ink-400)' }}>{item.desc}</p>
          </Card>
        ))}
      </div>

      <Card title="受保护命令列表" style={{ marginBottom: 20 }}>
        {protectedCmds.length > 0 ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
            {protectedCmds.map((cmd) => (
              <span
                key={cmd}
                className="mono"
                style={{
                  padding: '6px 14px',
                  background: 'var(--bg-700)',
                  border: '1px solid var(--glass-border)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: 13,
                  color: 'var(--accent-cyan)',
                }}
              >
                /{cmd}
              </span>
            ))}
          </div>
        ) : (
          <p style={{ color: 'var(--ink-500)', fontSize: 13 }}>暂无受保护命令配置</p>
        )}
      </Card>

      <Card title="策略说明">
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="info">提示</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              sudo 策略由服务端配置文件管理，面板仅提供只读展示。如需修改请联系服务器管理员编辑配置文件后重启服务。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="warn">注意</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              受保护命令需要 sudo 会话才能执行，未授权操作将被拦截并记录到安全审计。
            </span>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
