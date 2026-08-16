export function FullScreenSpinner() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
      <div style={{ textAlign: 'center' }}>
        <div className="shimmer-bg" style={{ width: 48, height: 48, borderRadius: 12, margin: '0 auto 16px' }} />
        <p style={{ color: 'var(--ink-400)', fontSize: 13 }}>加载中…</p>
      </div>
    </div>
  )
}

export function Spinner({ size = 20 }: { size?: number }) {
  return (
    <div
      className="shimmer-bg"
      style={{ width: size, height: size, borderRadius: '50%', display: 'inline-block' }}
    />
  )
}
