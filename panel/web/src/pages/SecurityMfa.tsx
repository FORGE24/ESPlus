import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'

export default function SecurityMfa() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.mfa.status(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)
  const [enrollData, setEnrollData] = useState<Record<string, unknown> | null>(null)

  const handleEnroll = async () => {
    setBusy(true)
    try {
      const result = await PanelAPI.mfa.enroll()
      setEnrollData(result)
      notify('info', '请使用验证器 App 扫描二维码并输入验证码确认')
    } catch (e) {
      notify('error', `启用失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleConfirm = async (formData: Record<string, string | number | boolean>) => {
    const code = String(formData.code || '')
    setBusy(true)
    try {
      await PanelAPI.mfa.confirm(code)
      notify('success', 'TOTP 双因素认证已启用')
      setEnrollData(null)
      refetch()
    } catch (e) {
      notify('error', `验证失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleDisable = async () => {
    if (!window.confirm('确认禁用 TOTP 双因素认证？禁用后账户安全性将降低。')) return
    setBusy(true)
    try {
      await PanelAPI.mfa.disable()
      notify('success', 'TOTP 双因素认证已禁用')
      refetch()
    } catch (e) {
      notify('error', `禁用失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !data) {
    return (
      <PageContainer title="面板 TOTP" subtitle="双因素认证管理（基于时间的一次性密码）">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="面板 TOTP"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const enabled = Boolean(data.enabled)

  const qrUrl = enrollData ? String(enrollData.qrUrl ?? enrollData.qr_url ?? enrollData.qrcode ?? '') : ''
  const secret = enrollData ? String(enrollData.secret ?? enrollData.totpSecret ?? enrollData.totp_secret ?? '') : ''
  const otpauth = enrollData ? String(enrollData.otpauth ?? enrollData.provisioningUri ?? '') : ''

  return (
    <PageContainer title="面板 TOTP" subtitle="双因素认证管理（基于时间的一次性密码）">
      <Card title="当前状态" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Badge type={enabled ? 'ok' : 'danger'}>{enabled ? '已启用' : '未启用'}</Badge>
          <span style={{ fontSize: 14, color: 'var(--ink-300)' }}>
            {enabled
              ? '双因素认证已启用，登录时需要输入 TOTP 验证码。'
              : '双因素认证未启用，建议立即启用以提高账户安全性。'}
          </span>
        </div>
        <div style={{ marginTop: 16, display: 'flex', gap: 10 }}>
          {!enabled && !enrollData && (
            <button className="btn btn-primary" disabled={busy} onClick={handleEnroll}>
              {busy ? '处理中…' : '开始启用 TOTP'}
            </button>
          )}
          {enabled && (
            <button className="btn btn-danger" disabled={busy} onClick={handleDisable}>
              {busy ? '处理中…' : '禁用 TOTP'}
            </button>
          )}
        </div>
      </Card>

      {enrollData && (
        <Card title="绑定 TOTP 验证器" style={{ marginBottom: 20 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '200px 1fr', gap: 24, alignItems: 'start' }}>
            <div style={{ textAlign: 'center' }}>
              {qrUrl ? (
                <img
                  src={qrUrl}
                  alt="TOTP 二维码"
                  style={{ width: 200, height: 200, borderRadius: 'var(--radius-sm)', background: '#fff', padding: 8 }}
                />
              ) : (
                <div style={{
                  width: 200, height: 200, borderRadius: 'var(--radius-sm)',
                  background: 'var(--bg-700)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: 'var(--ink-500)', fontSize: 13,
                }}>
                  二维码加载中…
                </div>
              )}
              <p style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: 8 }}>
                使用 Google Authenticator / Authy 等扫描
              </p>
            </div>
            <div>
              <div style={{ marginBottom: 16 }}>
                <div className="label">密钥（手动输入）</div>
                <div className="mono" style={{
                  fontSize: 14, color: 'var(--accent-cyan)', wordBreak: 'break-all',
                  padding: '10px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)',
                  letterSpacing: '0.05em',
                }}>
                  {secret || '—'}
                </div>
              </div>
              {otpauth && (
                <div style={{ marginBottom: 16 }}>
                  <div className="label">otpauth URI</div>
                  <div className="mono" style={{
                    fontSize: 11, color: 'var(--ink-400)', wordBreak: 'break-all',
                    padding: '10px 14px', background: 'var(--bg-700)', borderRadius: 'var(--radius-sm)',
                  }}>
                    {otpauth}
                  </div>
                </div>
              )}
              <FormBuilder
                fields={[
                  { name: 'code', label: '验证码', placeholder: '输入 6 位验证码', required: true, width: '100%' },
                ]}
                onSubmit={handleConfirm}
                submitLabel="确认绑定"
                loading={busy}
                layout="stack"
              />
            </div>
          </div>
        </Card>
      )}

      <Card title="安全提示">
        <div style={{ display: 'grid', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="info">提示</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              启用 TOTP 后，每次登录面板除了输入用户名密码外，还需输入验证器 App 中的 6 位动态验证码。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="warn">注意</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              请妥善保管密钥，丢失验证器将无法登录面板。建议在绑定后备份密钥。
            </span>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
