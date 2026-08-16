import { useRef, useEffect } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { RuntimeSnapshot, PerfSample } from '../lib/types'

// ── Sparkline bar chart with GSAP grow animation ──────────────
function PerfSparkline({
  samples,
  field,
  color,
  maxVal,
  label,
}: {
  samples: PerfSample[]
  field: 'tps_pct' | 'mspt_ms'
  color: string
  maxVal: number
  label: string
}) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!containerRef.current || !samples.length) return
    import('../lib/anim').then(({ sparklineGrow }) => {
      const vals = samples.map((s) => s[field])
      const max = field === 'tps_pct' ? 100 : Math.max(...vals, maxVal)
      sparklineGrow(containerRef.current!, vals, max)
    })
  }, [samples, field, maxVal])

  if (!samples || samples.length === 0) {
    return <p style={{ color: 'var(--ink-500)', fontSize: 13 }}>尚无采样数据；游戏服启动后约 1 分钟可见。</p>
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
        <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{label}</span>
        <span style={{ fontSize: 12, color, fontWeight: 600 }}>
          最新: {field === 'tps_pct' ? samples[samples.length - 1]?.tps_pct.toFixed(1) + '%' : samples[samples.length - 1]?.mspt_ms.toFixed(1) + 'ms'}
        </span>
      </div>
      <div
        ref={containerRef}
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 1,
          height: 100,
          padding: '6px 4px',
          background: 'var(--bg-700)',
          borderRadius: 6,
          overflow: 'hidden',
        }}
      />
    </div>
  )
}

export default function StatusPerformance() {
  const { data: runtime, loading: rtLoading, error: rtError } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 10000 },
  )
  const { data: samples, loading: perfLoading, error: perfError } = useApi<PerfSample[]>(
    () => PanelAPI.perf(120),
    [],
    { interval: 10000 },
  )

  const loading = rtLoading && !runtime
  const error = rtError || perfError

  if (loading) {
    return (
      <PageContainer title="性能监视" subtitle="TPS · MSPT · 内存 · 实体 · 实时监控">
        <div className="shimmer-bg" style={{ height: 300, borderRadius: 10 }} />
      </PageContainer>
    )
  }

  if (error) {
    return (
      <PageContainer title="性能监视">
        <div className="flash-err">{error}</div>
      </PageContainer>
    )
  }

  const tps = runtime?.tps_approx ?? runtime?.tps ?? 0
  const mspt = runtime?.mspt_ms ?? 0
  const usedMem = Number(runtime?.['used_memory'] ?? runtime?.['heap_used'] ?? 0)
  const maxMem = Number(runtime?.['max_memory'] ?? runtime?.['heap_max'] ?? 0)
  const entityCount = Number(runtime?.['entity_count'] ?? runtime?.['entities'] ?? 0)
  const chunkCount = Number(runtime?.['chunk_count'] ?? runtime?.['loaded_chunks'] ?? 0)

  const memPct = maxMem > 0 ? (usedMem / maxMem) * 100 : 0
  const tpsColor = tps > 18 ? 'emerald' : tps > 15 ? 'amber' : 'rose'
  const msptColor = mspt < 40 ? 'emerald' : mspt < 60 ? 'amber' : 'rose'
  const memColor = memPct < 70 ? 'emerald' : memPct < 90 ? 'amber' : 'rose'

  const stats = [
    { label: 'TPS', value: tps, decimals: 2, color: tpsColor, suffix: '/20' },
    { label: 'MSPT', value: mspt, decimals: 1, color: msptColor, suffix: 'ms' },
    { label: '已用内存', value: usedMem, color: memColor, suffix: maxMem ? `/${maxMem}MB` : 'MB' },
    { label: '内存使用率', value: memPct, decimals: 1, color: memColor, suffix: '%' },
    { label: '实体数量', value: entityCount, color: 'violet' },
    { label: '已加载区块', value: chunkCount, color: 'cyan' },
  ]

  const perfSamples = samples ?? []
  const maxMspt = perfSamples.length > 0 ? Math.max(...perfSamples.map((s) => s.mspt_ms), 50) : 50

  return (
    <PageContainer title="性能监视" subtitle="TPS · MSPT · 内存 · 实体 · 每 10 秒自动刷新">
      {/* Stats */}
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        {stats.map((s) => (
          <StatCard key={s.label} label={s.label} value={s.value} decimals={s.decimals} color={s.color} suffix={s.suffix} />
        ))}
      </div>

      {/* TPS Sparkline */}
      <Card title="TPS 走势（近 120 采样）" style={{ marginBottom: 20 }}>
        {perfLoading && !samples ? (
          <div className="shimmer-bg" style={{ height: 100, borderRadius: 6 }} />
        ) : (
          <PerfSparkline
            samples={perfSamples}
            field="tps_pct"
            color="var(--accent-cyan)"
            maxVal={100}
            label="TPS 百分比（100% = 满 TPS）"
          />
        )}
      </Card>

      {/* MSPT Sparkline */}
      <Card title="MSPT 走势（近 120 采样）" style={{ marginBottom: 20 }}>
        {perfLoading && !samples ? (
          <div className="shimmer-bg" style={{ height: 100, borderRadius: 6 }} />
        ) : (
          <PerfSparkline
            samples={perfSamples}
            field="mspt_ms"
            color="var(--accent-amber)"
            maxVal={maxMspt}
            label="毫秒每 tick（< 50ms = 正常）"
          />
        )}
      </Card>

      {/* Memory bar */}
      {maxMem > 0 && (
        <Card title="内存使用">
          <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              {usedMem} MB / {maxMem} MB
            </span>
            <span style={{ fontSize: 13, fontWeight: 600, color: `var(--${memColor === 'emerald' ? 'ok' : memColor === 'amber' ? 'warn' : 'danger'})` }}>
              {memPct.toFixed(1)}%
            </span>
          </div>
          <div style={{
            height: 20, background: 'var(--bg-700)', borderRadius: 10, overflow: 'hidden',
            border: '1px solid var(--glass-border)',
          }}>
            <div style={{
              height: '100%',
              width: `${Math.min(memPct, 100)}%`,
              background: `linear-gradient(90deg, var(--accent-${memColor === 'emerald' ? 'emerald' : memColor === 'amber' ? 'amber' : 'rose'}), var(--${memColor === 'emerald' ? 'ok' : memColor === 'amber' ? 'warn' : 'danger'}))`,
              borderRadius: 10,
              transition: 'width 0.6s ease',
            }} />
          </div>
        </Card>
      )}
    </PageContainer>
  )
}
