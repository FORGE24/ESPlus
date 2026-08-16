import { ReactNode, useRef, useEffect, CSSProperties } from 'react'
import { staggerReveal } from '../../lib/anim'

interface CardProps {
  title?: string
  children: ReactNode
  actions?: ReactNode
  className?: string
  noPadding?: boolean
  style?: CSSProperties
}

export function Card({ title, children, actions, className = '', noPadding, style }: CardProps) {
  return (
    <div className={`glass-card ${className}`} data-reveal style={style}>
      {title && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '14px 18px',
          borderBottom: '1px solid var(--glass-border)',
        }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-100)', margin: 0 }}>{title}</h3>
          {actions}
        </div>
      )}
      <div style={{ padding: noPadding ? 0 : '18px' }}>{children}</div>
    </div>
  )
}

interface StatCardProps {
  label: string
  value: number | string
  decimals?: number
  icon?: string
  color?: string
  suffix?: string
}

export function StatCard({ label, value, decimals, color = 'cyan', suffix }: StatCardProps) {
  const valueRef = useRef<HTMLDivElement>(null)
  const numValue = typeof value === 'number' ? value : parseFloat(value)

  useEffect(() => {
    if (valueRef.current && !isNaN(numValue)) {
      import('../../lib/anim').then(({ animateCount }) => {
        animateCount(valueRef.current!, numValue, { decimals: decimals || 0 })
      })
    }
  }, [numValue, decimals])

  const colorMap: Record<string, string> = {
    cyan: 'var(--accent-cyan)',
    emerald: 'var(--accent-emerald)',
    amber: 'var(--accent-amber)',
    violet: 'var(--accent-violet)',
    rose: 'var(--accent-rose)',
  }

  return (
    <div className="stat-card" data-reveal>
      <div style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--ink-400)' }}>
        {label}
      </div>
      <div ref={valueRef} style={{
        fontSize: 28,
        fontWeight: 700,
        marginTop: 8,
        color: colorMap[color] || 'var(--accent-cyan)',
        fontFamily: 'Syne, Inter, sans-serif',
      }}>
        {isNaN(numValue) ? value : 0}
        {suffix && <span style={{ fontSize: 14, marginLeft: 4, opacity: 0.7 }}>{suffix}</span>}
      </div>
    </div>
  )
}

interface PageContainerProps {
  title: string
  subtitle?: string
  children: ReactNode
  actions?: ReactNode
}

export function PageContainer({ title, subtitle, children, actions }: PageContainerProps) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (ref.current) {
      import('../../lib/anim').then(({ pageEnter, staggerReveal }) => {
        pageEnter(ref.current!)
        staggerReveal(ref.current!)
      })
    }
  }, [])

  return (
    <div ref={ref} style={{ animation: 'fadeIn 0.3s ease-out' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h1 className="page-title">{title}</h1>
          {subtitle && <p className="page-subtitle">{subtitle}</p>}
        </div>
        {actions}
      </div>
      {children}
    </div>
  )
}

export function EmptyState({ message = '暂无数据' }: { message?: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--ink-500)' }}>
      <p style={{ fontSize: 14 }}>{message}</p>
    </div>
  )
}
