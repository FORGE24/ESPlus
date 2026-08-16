import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'

export default function SecurityModSources() {
  const { data, loading, error } = useApi(() => PanelAPI.governance.modSources(), [], { interval: 30000 })

  if (loading && !data) {
    return (
      <PageContainer title="Mod 来源" subtitle="已加载 Mod 来源聚合与验证">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="Mod 来源"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const mods = (data as unknown[]) as Record<string, unknown>[]
  const totalMods = mods.length
  const verified = mods.filter((m) => Boolean(m.verified ?? m.trusted ?? m.signed)).length
  const unverified = totalMods - verified

  const columns = [
    { key: 'mod_id', header: 'Mod ID', className: 'mono', render: (r: Record<string, unknown>) => String(r.mod_id ?? r.modId ?? r.id ?? '—') },
    { key: 'name', header: '名称', render: (r: Record<string, unknown>) => String(r.name ?? r.display_name ?? '—') },
    { key: 'version', header: '版本', render: (r: Record<string, unknown>) => String(r.version ?? '—') },
    { key: 'source', header: '来源', render: (r: Record<string, unknown>) => String(r.source ?? r.origin ?? '—') },
    {
      key: 'verified', header: '验证状态',
      render: (r: Record<string, unknown>) => {
        const v = Boolean(r.verified ?? r.trusted ?? r.signed)
        return <Badge type={v ? 'ok' : 'warn'}>{v ? '已验证' : '未验证'}</Badge>
      },
    },
    { key: 'file', header: '文件', className: 'mono', render: (r: Record<string, unknown>) => String(r.file ?? r.file_path ?? r.jar ?? '—') },
    { key: 'hash', header: '哈希', className: 'mono', render: (r: Record<string, unknown>) => {
      const h = String(r.hash ?? r.sha256 ?? r.checksum ?? '')
      return h ? h.substring(0, 16) + '…' : '—'
    }},
    { key: 'size', header: '大小', render: (r: Record<string, unknown>) => {
      const s = Number(r.size ?? r.file_size ?? 0)
      return s > 0 ? `${(s / 1024 / 1024).toFixed(2)} MB` : '—'
    }},
  ]

  return (
    <PageContainer title="Mod 来源" subtitle="已加载 Mod 来源聚合与验证">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="Mod 总数" value={totalMods} color="cyan" />
        <StatCard label="已验证" value={verified} color="emerald" />
        <StatCard label="未验证" value={unverified} color={unverified > 0 ? 'amber' : 'emerald'} />
      </div>

      <Card title="Mod 列表">
        <DataTable
          columns={columns}
          data={mods}
          emptyMessage="暂无 Mod 数据"
          rowKey={(r, i) => String(r.mod_id ?? r.id ?? i)}
        />
      </Card>
    </PageContainer>
  )
}
