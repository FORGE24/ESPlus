// ═══════════════════════════════════════════════════════════
// ES+ Panel — Auth Context
// ═══════════════════════════════════════════════════════════

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import { PanelAPI } from './api'

interface AuthUser {
  name: string
  role: string
}

interface AuthCtx {
  user: AuthUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<{ mfaRequired: boolean }>
  verifyMfa: (code: string) => Promise<boolean>
  logout: () => Promise<void>
  isAdmin: boolean
  isModerator: boolean
}

const Ctx = createContext<AuthCtx>(null!)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  const checkAuth = useCallback(async () => {
    try {
      const me = await PanelAPI.auth.me()
      setUser(me)
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    checkAuth()
  }, [checkAuth])

  const login = useCallback(async (username: string, password: string) => {
    const res = await PanelAPI.auth.login(username, password)
    if (!res.ok) throw new Error(res.error || '登录失败')
    if (res.mfaRequired) return { mfaRequired: true }
    await checkAuth()
    return { mfaRequired: false }
  }, [checkAuth])

  const verifyMfa = useCallback(async (code: string) => {
    const res = await PanelAPI.auth.mfa(code)
    if (!res.ok) throw new Error(res.error || '验证码错误')
    await checkAuth()
    return true
  }, [checkAuth])

  const logout = useCallback(async () => {
    try { await PanelAPI.auth.logout() } catch { /* ignore */ }
    setUser(null)
    window.location.href = '/login'
  }, [])

  return (
    <Ctx.Provider value={{
      user,
      loading,
      login,
      verifyMfa,
      logout,
      isAdmin: user?.role === 'ADMIN',
      isModerator: user?.role === 'ADMIN' || user?.role === 'MODERATOR',
    }}>
      {children}
    </Ctx.Provider>
  )
}

export function useAuth() {
  return useContext(Ctx)
}
