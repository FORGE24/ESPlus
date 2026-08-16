import { useRef, useEffect, useMemo } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Float, Icosahedron, MeshDistortMaterial } from '@react-three/drei'
import * as THREE from 'three'
import { PageContainer, StatCard, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import type { DashboardData } from '../lib/types'

// ── 3D Scene: floating crystal cluster ───────────────────────
function CrystalCluster() {
  const groupRef = useRef<THREE.Group>(null)

  useFrame(({ clock }) => {
    if (groupRef.current) {
      groupRef.current.rotation.y = clock.getElapsedTime() * 0.15
    }
  })

  const crystals = useMemo(() => {
    const arr = []
    for (let i = 0; i < 7; i++) {
      const angle = (i / 7) * Math.PI * 2
      const radius = 1.8
      arr.push({
        position: [
          Math.cos(angle) * radius,
          Math.sin(i * 1.3) * 0.4,
          Math.sin(angle) * radius,
        ] as [number, number, number],
        scale: 0.4 + Math.random() * 0.3,
        color: ['#22d3ee', '#34d399', '#a78bfa', '#fbbf24'][i % 4],
      })
    }
    return arr
  }, [])

  return (
    <group ref={groupRef}>
      <Float speed={2} rotationIntensity={0.5} floatIntensity={0.8}>
        <Icosahedron args={[1, 1]}>
          <MeshDistortMaterial
            color="#22d3ee"
            emissive="#0891b2"
            emissiveIntensity={0.3}
            distort={0.3}
            speed={2}
            metalness={0.8}
            roughness={0.2}
          />
        </Icosahedron>
      </Float>
      {crystals.map((c, i) => (
        <Float key={i} speed={1 + i * 0.2} rotationIntensity={0.3} floatIntensity={0.5}>
          <mesh position={c.position} scale={c.scale}>
            <octahedronGeometry args={[1, 0]} />
            <meshStandardMaterial
              color={c.color}
              emissive={c.color}
              emissiveIntensity={0.4}
              metalness={0.7}
              roughness={0.3}
              transparent
              opacity={0.85}
            />
          </mesh>
        </Float>
      ))}
    </group>
  )
}

function Scene3D() {
  return (
    <div style={{ position: 'absolute', inset: 0, opacity: 0.5, pointerEvents: 'none' }}>
      <Canvas camera={{ position: [0, 0, 8], fov: 50 }}>
        <ambientLight intensity={0.3} />
        <pointLight position={[10, 10, 10]} intensity={1} color="#22d3ee" />
        <pointLight position={[-10, -10, -5]} intensity={0.5} color="#a78bfa" />
        <CrystalCluster />
        <OrbitControls enabled={false} />
      </Canvas>
    </div>
  )
}

// ── TPS Sparkline (GSAP-animated bars) ───────────────────────
function TpsSparkline({ samples }: { samples: { tps: number; tps_pct: number; mspt_ms: number }[] }) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!containerRef.current || !samples.length) return
    import('../lib/anim').then(({ sparklineGrow }) => {
      const maxVal = Math.max(...samples.map((s) => s.tps_pct), 100)
      sparklineGrow(containerRef.current!, samples.map((s) => s.tps_pct), maxVal)
    })
  }, [samples])

  if (!samples || samples.length === 0) {
    return <p style={{ color: 'var(--ink-500)', fontSize: 13 }}>尚无采样；游戏服启动后约 1 分钟可见。</p>
  }

  return (
    <div ref={containerRef} style={{ display: 'flex', alignItems: 'flex-end', gap: 1, height: 80, padding: '6px 4px', background: 'var(--bg-700)', borderRadius: 6, overflow: 'hidden' }} />
  )
}

export default function Dashboard() {
  const { data, loading, error } = useApi<DashboardData>(() => PanelAPI.dashboard(), [], { interval: 15000 })

  if (loading && !data) {
    return <PageContainer title="仪表盘" subtitle="全局行为侦测 · 物品来源链 · 异常告警 · 事发还原"><div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} /></PageContainer>
  }
  if (error) return <PageContainer title="仪表盘"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const tps = data.runtime?.tps_approx ?? data.runtime?.tps
  const stats = [
    { label: 'TPS 近似', value: tps || 0, decimals: 2, color: tps && tps > 18 ? 'emerald' : tps && tps > 15 ? 'amber' : 'rose', suffix: '/20' },
    { label: '在线玩家', value: data.onlineCount, color: 'cyan' },
    { label: '待处理动作', value: data.pendingActions, color: 'amber' },
    { label: '24h 失败动作', value: data.failedActions, color: 'rose' },
    { label: '24h 事件', value: data.events24h, color: 'violet' },
    { label: '未确认告警', value: data.alertsOpen, color: data.alertsOpen > 0 ? 'rose' : 'emerald' },
    { label: '物品溯源', value: data.traces, color: 'emerald' },
    { label: '24h 安全审计', value: data.audit24h, color: 'cyan' },
  ]

  const eventColumns = [
    { key: 'ts', header: '时间' },
    { key: 'category', header: '类别' },
    { key: 'action', header: '动作' },
    { key: 'actor_name', header: '玩家' },
    { key: 'item_id', header: '物品' },
    { key: 'detail', header: '详情' },
  ]

  const alertColumns = [
    { key: 'ts', header: '时间' },
    { key: 'severity', header: '级别', render: (a: any) => <span className={`sev-${a.severity}`}>{a.severity}</span> },
    { key: 'title', header: '标题' },
    { key: 'actor_name', header: '玩家' },
    { key: 'message', header: '内容' },
  ]

  const auditColumns = [
    { key: 'ts', header: '时间' },
    { key: 'uuid', header: 'UUID', className: 'mono' },
    { key: 'action', header: '动作' },
    { key: 'detail', header: '详情' },
    { key: 'success', header: '结果', render: (l: any) => <Badge type={l.success === 1 ? 'ok' : 'danger'}>{l.success === 1 ? 'OK' : 'FAIL'}</Badge> },
  ]

  return (
    <div style={{ position: 'relative' }}>
      <Scene3D />
      <div style={{ position: 'relative', zIndex: 1 }}>
        <PageContainer title="设备 / 服务总览" subtitle="全局行为侦测 · 物品来源链 · 异常告警 · 事发还原">
          {/* Stats */}
          <div className="stat-grid" style={{ marginBottom: 20 }}>
            {stats.map((s) => (
              <StatCard key={s.label} label={s.label} value={s.value} decimals={s.decimals} color={s.color} suffix={s.suffix} />
            ))}
          </div>

          {/* TPS Chart */}
          <Card title="TPS 走势（近 1 分钟）" style={{ marginBottom: 20 }}>
            <TpsSparkline samples={data.tpsSamples || []} />
          </Card>

          {/* Two-column layout */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
            {/* Recent Alerts */}
            <Card title="最近告警">
              <DataTable
                columns={alertColumns}
                data={data.recentAlerts || []}
                emptyMessage="暂无告警"
                rowKey={(a) => a.alert_id}
              />
            </Card>

            {/* Recent Audit */}
            <Card title="最近安全审计">
              <DataTable
                columns={auditColumns}
                data={data.recentAudit || []}
                emptyMessage="暂无审计记录"
                rowKey={(l) => l.id}
              />
            </Card>
          </div>

          {/* Recent Events */}
          <Card title="最近事件">
            <DataTable
              columns={eventColumns}
              data={data.recentEvents || []}
              emptyMessage="暂无事件"
              rowKey={(e) => e.event_id}
            />
          </Card>

          {/* Upcoming schedules */}
          {data.schedules && data.schedules.length > 0 && (
            <Card title="即将执行的定时广播" style={{ marginTop: 20 }}>
              <DataTable
                columns={[
                  { key: 'id', header: 'ID' },
                  { key: 'note', header: '备注', render: (s: any) => s.note || s.payload },
                  { key: 'interval_seconds', header: '间隔' },
                  { key: 'next_run_at', header: '下次', className: 'mono' },
                  { key: 'enabled', header: '状态', render: (s: any) => <Badge type={s.enabled === 1 ? 'ok' : 'warn'}>{s.enabled === 1 ? '启用' : '停用'}</Badge> },
                ]}
                data={data.schedules}
                rowKey={(s) => s.id}
              />
            </Card>
          )}
        </PageContainer>
      </div>
    </div>
  )
}
