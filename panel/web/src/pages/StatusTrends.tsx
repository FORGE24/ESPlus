import { useRef, useEffect, useMemo } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { PerfSample } from '../lib/types'

// ── Bar chart with GSAP grow animation ────────────────────────
function TrendChart({
  buckets,
  label,
  color,
  unit,
}: {
  buckets: { label: string; value: number; count: number }[]
  label: string
  color: string
  unit: string
}) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!containerRef.current || !buckets.length) return
    import('../lib/anim').then(({ sparklineGrow }) => {
      const maxVal = Math.max(...buckets.map((b) => b.value), 1)
      sparklineGrow(containerRef.current!, buckets.map((b) => b.value), maxVal)
    })
  }, [buckets])

  if (!buckets || buckets.length === 0) {
    return <p style={{ color: 'var(--ink-500)', fontSize: 13 }}>尚无足够数据生成趋势图。</p>
  }

  const avg = buckets.reduce((sum, b) => sum + b.value, 0) / buckets.length
  const maxVal = Math.max(...buckets.map((b) => b.value), 1)

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{label}</span>
        <span style={{ fontSize: 12, color, fontWeight: 600 }}>
          平均: {avg.toFixed(1)}{unit}
        </span>
      </div>
      <div
        ref={containerRef}
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 2,
          height: 140,
          padding: '8px 4px',
          background: 'var(--bg-700)',
          borderRadius: 6,
          overflow: 'hidden',
        }}
      />
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6, fontSize: 10, color: 'var(--ink-500)' }}>
        <span>{buckets[0]?.label}</span>
        <span>{buckets[buckets.length - 1]?.label}</span>
      </div>
    </div>
  )
}

// Group samples by hour
function groupByHour(samples: PerfSample[]): { label: string; value: number; count: number }[] {
  const map = new Map<string, { sum: number; count: number }>()
  for (const s of samples) {
    const d = new Date(s.ts)
    const key = `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:00`
    const existing = map.get(key) || { sum: 0, count: 0 }
    existing.sum += s.tps
    existing.count += 1
    map.set(key, existing)
  }
  return Array.from(map.entries()).map(([label, v]) => ({
    label,
    value: v.count > 0 ? v.sum / v.count : 0,
    count: v.count,
  }))
}

// Group samples by day
function groupByDay(samples: PerfSample[]): { label: string; value: number; count: number }[] {
  const map = new Map<string, { sum: number; count: number }>()
  for (const s of samples) {
    const d = new Date(s.ts)
    const key = `${d.getMonth() + 1}/${d.getDate()}`
    const existing = map.get(key) || { sum: 0, count: 0 }
    existing.sum += s.tps
    existing.count += 1
    map.set(key, existing)
  }
  return Array.from(map.entries()).map(([label, v]) => ({
    label,
    value: v.count > 0 ? v.sum / v.count : 0,
    count: v.count,
  }))
}

// Group MSPT by hour
function groupMsptByHour(samples: PerfSample[]): { label: string; value: number; count: number }[] {
  const map = new Map<string, { sum: number; count: number }>()
  for (const s of samples) {
    const d = new Date(s.ts)
    const key = `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:00`
    const existing = map.get(key) || { sum: 0, count: 0 }
    existing.sum += s.mspt_ms
    existing.count += 1
    map.set(key, existing)
  }
  return Array.from(map.entries()).map(([label, v]) => ({
    label,
    value: v.count > 0 ? v.sum / v.count : 0,
    count: v.count,
  }))
}

export default function StatusTrends() {
  const { data: samples, loading, error } = useApi<PerfSample[]>(
    () => PanelAPI.perf(1000),
    [],
    { interval: 60000 },
  )

  const perfSamples = samples ?? []

  const hourlyTps = useMemo(() => groupByHour(perfSamples), [perfSamples])
  const dailyTps = useMemo(() => groupByDay(perfSamples), [perfSamples])
  const hourlyMspt = useMemo(() => groupMsptByHour(perfSamples), [perfSamples])

  const avgTps = perfSamples.length > 0
    ? perfSamples.reduce((sum, s) => sum + s.tps, 0) / perfSamples.length
    : 0
  const avgMspt = perfSamples.length > 0
    ? perfSamples.reduce((sum, s) => sum + s.mspt_ms, 0) / perfSamples.length
    : 0
  const minTps = perfSamples.length > 0 ? Math.min(...perfSamples.map((s) => s.tps)) : 0
  const maxMspt = perfSamples.length > 0 ? Math.max(...perfSamples.map((s) => s.mspt_ms)) : 0

  if (loading && !samples) {
    return (
      <PageContainer title="长期趋势" subtitle="TPS / MSPT 历史走势 · 按小时 / 按天聚合">
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer title="长期趋势">
        <div className="flash-err">{error}</div>
      </PageContainer>
    )
  }

  return (
    <PageContainer title="长期趋势" subtitle="TPS / MSPT 历史走势 · 按小时 / 按天聚合 · 每 60 秒刷新">
      {/* Stats */}
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="采样数" value={perfSamples.length} color="cyan" />
        <StatCard label="平均 TPS" value={avgTps} decimals={2} color={avgTps > 18 ? 'emerald' : avgTps > 15 ? 'amber' : 'rose'} suffix="/20" />
        <StatCard label="平均 MSPT" value={avgMspt} decimals={1} color="amber" suffix="ms" />
        <StatCard label="最低 TPS" value={minTps} decimals={2} color="rose" />
        <StatCard label="最高 MSPT" value={maxMspt} decimals={1} color="rose" suffix="ms" />
      </div>

      {/* 24h TPS trend */}
      <Card title="24 小时 TPS 走势（按小时聚合）" style={{ marginBottom: 20 }}>
        {loading ? (
          <div className="shimmer-bg" style={{ height: 140, borderRadius: 6 }} />
        ) : (
          <TrendChart
            buckets={hourlyTps}
            label="每小时平均 TPS"
            color="var(--accent-cyan)"
            unit="/20"
          />
        )}
      </Card>

      {/* 24h MSPT trend */}
      <Card title="24 小时 MSPT 走势（按小时聚合）" style={{ marginBottom: 20 }}>
        {loading ? (
          <div className="shimmer-bg" style={{ height: 140, borderRadius: 6 }} />
        ) : (
          <TrendChart
            buckets={hourlyMspt}
            label="每小时平均 MSPT"
            color="var(--accent-amber)"
            unit="ms"
          />
        )}
      </Card>

      {/* 7-day TPS trend */}
      <Card title="7 天 TPS 走势（按天聚合）">
        {loading ? (
          <div className="shimmer-bg" style={{ height: 140, borderRadius: 6 }} />
        ) : (
          <TrendChart
            buckets={dailyTps}
            label="每天平均 TPS"
            color="var(--accent-violet)"
            unit="/20"
          />
        )}
      </Card>
    </PageContainer>
  )
}
