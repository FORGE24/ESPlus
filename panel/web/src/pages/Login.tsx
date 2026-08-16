import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'

export default function Login() {
  const { login, verifyMfa } = useAuth()
  const { notify } = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const isMfaPage = location.pathname === '/login/mfa'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [mfaRequired, setMfaRequired] = useState(isMfaPage)

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const result = await login(username, password)
      if (result.mfaRequired) {
        setMfaRequired(true)
        navigate('/login/mfa')
        notify('info', '请输入 TOTP 验证码')
      } else {
        notify('success', '登录成功')
        navigate('/')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  const handleMfa = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      await verifyMfa(code)
      notify('success', '验证成功')
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : '验证码错误')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
      background: 'var(--bg-900)',
      backgroundImage: 'radial-gradient(800px 400px at 50% 0%, rgba(34, 211, 238, 0.08) 0%, transparent 60%)',
    }}>
      <div style={{ width: 'min(440px, 100%)', animation: 'slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1)' }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <h1 style={{
            fontFamily: 'Syne, Inter, sans-serif',
            fontSize: '2.5rem',
            fontWeight: 800,
            letterSpacing: '-0.03em',
            margin: 0,
          }}>
            <span className="text-gradient">ES+</span>
          </h1>
          <p style={{ color: 'var(--ink-400)', fontSize: 14, marginTop: 8 }}>
            Minecraft 安全套件 · 管理控制台
          </p>
        </div>

        <div className="glass-card" style={{ padding: 32 }}>
          {!mfaRequired ? (
            <>
              <h2 style={{ fontSize: 18, fontWeight: 600, color: 'var(--ink-100)', marginBottom: 6 }}>登录</h2>
              <p style={{ color: 'var(--ink-400)', fontSize: 13, marginBottom: 24 }}>
                请使用管理员 / 版主 / 只读账号登录
              </p>
              {error && <div className="flash-err" style={{ marginBottom: 16 }}>{error}</div>}
              <form onSubmit={handleLogin} style={{ display: 'grid', gap: 16 }}>
                <div>
                  <label className="label">用户名</label>
                  <input
                    type="text"
                    className="input"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="username"
                    required
                    autoFocus
                  />
                </div>
                <div>
                  <label className="label">密码</label>
                  <input
                    type="password"
                    className="input"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    required
                  />
                </div>
                <button type="submit" className="btn btn-primary" disabled={loading} style={{ width: '100%', marginTop: 8, padding: '10px' }}>
                  {loading ? '登录中…' : '登录'}
                </button>
              </form>
            </>
          ) : (
            <>
              <h2 style={{ fontSize: 18, fontWeight: 600, color: 'var(--ink-100)', marginBottom: 6 }}>双因素验证</h2>
              <p style={{ color: 'var(--ink-400)', fontSize: 13, marginBottom: 24 }}>
                请输入身份验证器 App 中的 6 位验证码
              </p>
              {error && <div className="flash-err" style={{ marginBottom: 16 }}>{error}</div>}
              <form onSubmit={handleMfa} style={{ display: 'grid', gap: 16 }}>
                <div>
                  <label className="label">TOTP 验证码</label>
                  <input
                    type="text"
                    className="input"
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    placeholder="000000"
                    maxLength={6}
                    required
                    autoFocus
                    style={{ textAlign: 'center', fontSize: 24, letterSpacing: '0.5em', fontFamily: 'JetBrains Mono, monospace' }}
                  />
                </div>
                <button type="submit" className="btn btn-primary" disabled={loading} style={{ width: '100%', marginTop: 8, padding: '10px' }}>
                  {loading ? '验证中…' : '验证'}
                </button>
              </form>
            </>
          )}
        </div>

        <p style={{ textAlign: 'center', color: 'var(--ink-500)', fontSize: 12, marginTop: 24 }}>
          ES+ Security Suite · NeoForge 1.21.1
        </p>
      </div>
    </div>
  )
}
