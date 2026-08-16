// ═══════════════════════════════════════════════════════════
// ES+ Panel — Toast notification system
// ═══════════════════════════════════════════════════════════

import { createContext, useContext, useState, useCallback, ReactNode } from 'react'
import { toastIn } from './anim'

type ToastType = 'success' | 'error' | 'info'
interface Toast { id: number; type: ToastType; message: string }

const Ctx = createContext<{ notify: (type: ToastType, message: string) => void }>(null!)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const notify = useCallback((type: ToastType, message: string) => {
    const id = Date.now() + Math.random()
    setToasts((prev) => [...prev, { id, type, message }])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4000)
  }, [])

  return (
    <Ctx.Provider value={{ notify }}>
      {children}
      <div style={{ position: 'fixed', top: 20, right: 20, zIndex: 9999, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} />
        ))}
      </div>
    </Ctx.Provider>
  )
}

function ToastItem({ toast }: { toast: Toast }) {
  const ref = useCallback((el: HTMLDivElement | null) => {
    if (el) toastIn(el)
  }, [])

  const colors: Record<ToastType, string> = {
    success: 'rgba(34, 197, 94, 0.15); border-color: rgba(34, 197, 94, 0.4); color: #22c55e',
    error: 'rgba(239, 68, 68, 0.15); border-color: rgba(239, 68, 68, 0.4); color: #ef4444',
    info: 'rgba(34, 211, 238, 0.15); border-color: rgba(34, 211, 238, 0.4); color: #22d3ee',
  }

  return (
    <div
      ref={ref}
      style={{
        padding: '12px 20px',
        background: colors[toast.type].split(';')[0],
        border: `1px solid ${colors[toast.type].split('border-color:')[1]?.split(';')[0]?.trim() || 'transparent'}`,
        color: colors[toast.type].split('color:')[1]?.trim() || '#fff',
        borderRadius: 8,
        fontSize: 13,
        fontWeight: 500,
        backdropFilter: 'blur(12px)',
        boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
        maxWidth: 360,
      }}
    >
      {toast.message}
    </div>
  )
}

export function useToast() {
  return useContext(Ctx)
}
