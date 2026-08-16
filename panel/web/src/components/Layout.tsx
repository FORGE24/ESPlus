import { ReactNode, useState, useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { NAV, NavItem } from '../lib/nav'

// ── Icon component (inline SVG path strings) ──────────────────
const ICONS: Record<string, string> = {
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  server: 'M5 2h14a1 1 0 011 1v18a1 1 0 01-1 1H5a1 1 0 01-1-1V3a1 1 0 011-1zM8 6h8M8 10h8M8 14h4',
  activity: 'M22 12h-4l-3 9L9 3l-3 9H2',
  trending: 'M23 6l-9.5 9.5-5-5L1 18M17 6h6v6',
  wifi: 'M5 12.55a11 11 0 0114 0M8.53 16.11a6 6 0 016.95 0M12 20h.01M2 8.82a15 15 0 0119 0',
  box: 'M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16zM3.27 6.96L12 12l8.73-5.04M12 22V12',
  users: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75',
  user: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z',
  ban: 'M18.36 5.64a9 9 0 11-12.72 0M1 1l22 22',
  check: 'M9 12l2 2 4-4M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  clock: 'M12 22a10 10 0 100-20 10 10 0 000 20zM12 6v6l4 2',
  message: 'M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z',
  calendar: 'M19 4H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2V6a2 2 0 00-2-2zM16 2v4M8 2v4M3 10h18',
  type: 'M4 7V4h16v3M9 20h6M12 4v16',
  bar: 'M2 20h20M5 20V10M10 20V6M15 20v-8M20 20V4',
  filter: 'M22 3H2l8 9.46V19l4 2v-8.54L22 3z',
  mute: 'M11 5L6 9H2v6h4l5 4V5zM23 9l-6 6M17 9l6 6',
  sun: 'M12 17a5 5 0 100-10 5 5 0 000 10zM12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42',
  sword: 'M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6 3 3-6 6-3-3z',
  frame: 'M3 3h18v18H3zM9 3v18M15 3v18M3 9h18M3 15h18',
  home: 'M3 12l9-9 9 9M5 10v10h14V10',
  globe: 'M12 22a10 10 0 100-20 10 10 0 000 20zM2 12h20M12 2a15 15 0 010 20M12 2a15 15 0 000 20',
  sliders: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
  trash: 'M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2',
  gift: 'M20 12v10H4V12M2 7h20v5H2zM12 22V7M12 7H7.5a2.5 2.5 0 010-5C11 2 12 7 12 7zM12 7h4.5a2.5 2.5 0 000-5C13 2 12 7 12 7z',
  backpack: 'M4 10V8a4 4 0 014-4h8a4 4 0 014 4v2M4 10h16v10H4zM9 14h6',
  eraser: 'M20 20H7L3 16l9-9 8 8-4 5M14 14L9 9',
  search: 'M11 19a8 8 0 100-16 8 8 0 000 16zM21 21l-4.35-4.35',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
  key: 'M21 2l-2 2m-7.61 7.61a5.5 5.5 0 11-7.778 7.778 5.5 5.5 0 017.778-7.778zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4',
  id: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z',
  scroll: 'M8 21h12a2 2 0 002-2v-2H10v2a2 2 0 11-4 0V5a2 2 0 10-4 0v3h4M19 17V5a2 2 0 00-2-2H4',
  alert: 'M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0zM12 9v4M12 17h.01',
  gauge: 'M12 14a2 2 0 100-4 2 2 0 000 4zM12 14l4 4M12 22a10 10 0 100-20 10 10 0 000 20z',
  fingerprint: 'M12 11a2 2 0 00-2 2c0 1.5.5 3.5 1 5M16 22l-2-5M2 12a10 10 0 0118-6',
  webhook: 'M18 16.98h-5.99c-1.1 0-1.95.94-2.48 1.9A4 4 0 012 20M9 13a3 3 0 110-6 3 3 0 010 6zM15 3a3 3 0 110 6 3 3 0 010-6z',
  history: 'M3 3v5h5M3.05 13A9 9 0 106 5.3L3 8M12 7v5l4 2',
  camera: 'M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z',
  coins: 'M12 8c-3 0-5 1-5 2v8c0 1 2 2 5 2s5-1 5-2v-8c0-1-2-2-5-2zM7 10c0-1 2-2 5-2s5 1 5 2M7 14c0 1 2 2 5 2s5-1 5-2',
  package: 'M16.5 9.4L7.5 4.21M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16zM3.27 6.96L12 12l8.73-5.04M12 22V12',
  qr: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h3v3h-3z',
  lock: 'M5 11h14v10H5zM8 11V7a4 4 0 018 0v4',
  crosshair: 'M12 22a10 10 0 100-20 10 10 0 000 20zM22 12h-4M6 12H2M12 6V2M12 22v-4',
  crown: 'M2 4l5 6 5-8 5 8 5-6-2 14H4L2 4z',
  eye: 'M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8zM12 15a3 3 0 100-6 3 3 0 000 6z',
  terminal: 'M4 17l6-6-6-6M12 19h8',
  ssh: 'M12 2a5 5 0 00-5 5v3H5a2 2 0 00-2 2v8a2 2 0 002 2h14a2 2 0 002-2v-8a2 2 0 00-2-2h-2V7a5 5 0 00-5-5z',
  save: 'M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2zM17 21v-8H7v8M7 3v5h8',
  archive: 'M21 8v13H3V8M1 3h22v5H1zM10 12h4',
  refresh: 'M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15',
  power: 'M18.36 6.64a9 9 0 11-12.73 0M12 2v10',
  wrench: 'M14.7 6.3a4 4 0 00-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 005.4-5.4l-2.1 2.1-2.5-2.5 2.1-2.1z',
  schedule: 'M12 22a10 10 0 100-20 10 10 0 000 20zM12 6v6l4 2',
  bolt: 'M13 2L3 14h9l-1 8 10-12h-9l1-8z',
  settings: 'M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z',
  'file-text': 'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  route: 'M6 19a3 3 0 100-6 3 3 0 000 6zM18 11a3 3 0 100-6 3 3 0 000 6zM6 13V7a3 3 0 013-3h6M18 11v6a3 3 0 01-3 3h-6',
}

function Icon({ name, size = 16 }: { name: string; size?: number }) {
  const path = ICONS[name] || ICONS.grid
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
      <path d={path} />
    </svg>
  )
}

function NavItemLink({ item, active, onClick }: { item: NavItem; active: boolean; onClick: () => void }) {
  return (
    <a className={`nav-link ${active ? 'active' : ''}`} onClick={onClick} style={{ textDecoration: 'none', cursor: 'pointer' }}>
      <Icon name={item.icon} size={15} />
      <span>{item.label}</span>
    </a>
  )
}

function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { user } = useAuth()

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    return location.pathname.startsWith(path)
  }

  return (
    <>
      {/* Mobile overlay */}
      {open && (
        <div
          onClick={onClose}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 40, display: 'none', '@media (max-width: 768px)': { display: 'block' } } as React.CSSProperties}
          className="md-overlay"
        />
      )}
      <aside
        className="sidebar-panel"
        style={{
          width: 240,
          flexShrink: 0,
          background: 'var(--bg-800)',
          borderRight: '1px solid var(--glass-border)',
          maxHeight: 'calc(100vh - 56px)',
          overflowY: 'auto',
          position: 'sticky',
          top: 56,
          transform: open ? 'translateX(0)' : undefined,
          transition: 'transform 0.3s ease',
        }}
      >
        {NAV.map((section) => {
          const items = section.items.filter((item) => {
            if (item.admin && user?.role !== 'ADMIN') return false
            return true
          })
          if (items.length === 0) return null
          return (
            <div key={section.title}>
              <div className="nav-section-title">{section.title}</div>
              {items.map((item) => (
                <div key={item.path} style={{ padding: '0 8px' }}>
                  <NavItemLink
                    item={item}
                    active={isActive(item.path)}
                    onClick={() => {
                      navigate(item.path)
                      onClose()
                    }}
                  />
                </div>
              ))}
            </div>
          )
        })}
      </aside>
    </>
  )
}

function Topbar({ onMenuClick }: { onMenuClick: () => void }) {
  const { user, logout } = useAuth()
  const [clock, setClock] = useState('--')

  useEffect(() => {
    const tick = () => setClock(new Date().toLocaleString('zh-CN', { hour12: false }))
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <header
      style={{
        height: 56,
        background: 'var(--bg-800)',
        borderBottom: '1px solid var(--glass-border)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 20px',
        position: 'sticky',
        top: 0,
        zIndex: 30,
        backdropFilter: 'blur(12px)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <button
          className="btn btn-ghost btn-sm md-menu-btn"
          onClick={onMenuClick}
          style={{ display: 'none' }}
        >
          <svg width={20} height={20} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M3 12h18M3 6h18M3 18h18" />
          </svg>
        </button>
        <h1 style={{ fontFamily: 'Syne, Inter, sans-serif', fontSize: 18, fontWeight: 700, margin: 0 }}>
          <span className="text-gradient">ES+</span>
          <span style={{ color: 'var(--ink-400)', fontWeight: 400, fontSize: 14, marginLeft: 8 }}>管理控制台</span>
        </h1>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 20, fontSize: 13 }}>
        <span style={{ color: 'var(--ink-400)' }} className="mono">{clock}</span>
        <span style={{ color: 'var(--ink-300)' }}>
          {user?.name}
          <span className={`badge badge-${user?.role === 'ADMIN' ? 'danger' : user?.role === 'MODERATOR' ? 'info' : 'violet'}`} style={{ marginLeft: 8 }}>
            {user?.role}
          </span>
        </span>
        <a onClick={logout} style={{ cursor: 'pointer', color: 'var(--accent-rose)' }}>退出</a>
      </div>
    </header>
  )
}

export function Layout({ children }: { children: ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const contentRef = useRef<HTMLDivElement>(null)
  const location = useLocation()

  // Scroll to top on route change
  useEffect(() => {
    if (contentRef.current) {
      contentRef.current.scrollTop = 0
    }
  }, [location.pathname])

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Topbar onMenuClick={() => setSidebarOpen(true)} />
      <div style={{ display: 'flex', flex: 1, minHeight: 'calc(100vh - 56px)' }}>
        <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
        <main
          ref={contentRef}
          style={{
            flex: 1,
            padding: '24px 28px',
            minWidth: 0,
            overflowY: 'auto',
          }}
        >
          {children}
        </main>
      </div>
    </div>
  )
}
