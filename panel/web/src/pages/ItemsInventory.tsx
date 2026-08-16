import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'

interface InventorySlot {
  [k: string]: unknown
  slot?: number
  item?: string
  type?: string
  name?: string
  count?: number
  enchantments?: string
  durability?: number
}

export default function ItemsInventory() {
  const { notify } = useToast()
  const [q, setQ] = useState('')
  const [section, setSection] = useState('inventory')
  const [searched, setSearched] = useState('')

  const { data: slots, loading, error } = useApi<InventorySlot[]>(
    async () => {
      if (!searched.trim()) return []
      return (await PanelAPI.playerInventory(searched, section)) as InventorySlot[]
    },
    [searched, section],
  )

  const handleSearch = () => {
    setSearched(q.trim())
  }

  const columns = [
    { key: 'slot', header: '槽位', width: '80px' },
    { key: 'name', header: '物品名称', render: (s: InventorySlot) => s.name || s.item || '-' },
    { key: 'type', header: '物品 ID', className: 'mono', render: (s: InventorySlot) => s.type || s.item || '-' },
    { key: 'count', header: '数量' },
    {
      key: 'enchantments',
      header: '附魔',
      render: (s: InventorySlot) =>
        s.enchantments ? <Badge type="violet">{s.enchantments}</Badge> : <span style={{ color: 'var(--ink-500)' }}>-</span>,
    },
  ]

  return (
    <PageContainer title="背包 / 末影箱查看" subtitle="查看在线玩家背包与末影箱物品">
      <div style={{ marginBottom: 20 }}>
        <Card title="查询条件">
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
            <div style={{ flex: 1, minWidth: 200 }}>
              <label className="label">玩家名 / UUID</label>
              <input
                className="input"
                placeholder="输入玩家名或 UUID"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <div style={{ minWidth: 160 }}>
              <label className="label">区域</label>
              <select className="select" value={section} onChange={(e) => setSection(e.target.value)}>
                <option value="inventory">背包（Inventory）</option>
                <option value="enderchest">末影箱（EnderChest）</option>
              </select>
            </div>
            <button className="btn btn-primary" onClick={handleSearch} disabled={loading}>
              {loading ? '查询中…' : '查询'}
            </button>
          </div>
        </Card>
      </div>

      <Card title={`物品列表${searched ? ` — ${searched}` : ''}`}>
        {!searched ? (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--ink-500)' }}>
            <p style={{ fontSize: 14 }}>请输入玩家名或 UUID 后点击查询</p>
          </div>
        ) : loading ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={columns}
            data={slots || []}
            emptyMessage="背包为空或未找到该玩家"
            rowKey={(s, i) => `${s.slot ?? i}`}
          />
        )}
      </Card>
    </PageContainer>
  )
}
