import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'

interface BanRecord {
  [k: string]: unknown
  name?: string
  uuid?: string
  ip?: string
  reason?: string
  expires?: string
  source?: string
}

export default function Bans() {
  const { notify } = useToast()
  const [unbanning, setUnbanning] = useState(false)
  const [ipBanning, setIpBanning] = useState(false)
  const [ipPardoning, setIpPardoning] = useState(false)

  const { data: bans, loading, error, refetch } = useApi<BanRecord[]>(
    () => api.get<BanRecord[]>('/api/bans'),
    [],
    { interval: 15000 },
  )

  const handleUnban = async (data: Record<string, string | number | boolean>) => {
    setUnbanning(true)
    try {
      await PanelAPI.unban(String(data.player || ''), String(data.uuid || ''))
      notify('success', '已解封')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '解封失败')
    } finally {
      setUnbanning(false)
    }
  }

  const handleIpBan = async (data: Record<string, string | number | boolean>) => {
    setIpBanning(true)
    try {
      await api.post('/api/bans/ip', {
        ip: String(data.ip),
        reason: String(data.reason || ''),
      })
      notify('success', 'IP 封禁已添加')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : 'IP 封禁失败')
    } finally {
      setIpBanning(false)
    }
  }

  const handleIpPardon = async (data: Record<string, string | number | boolean>) => {
    setIpPardoning(true)
    try {
      await api.post('/api/bans/ip-pardon', { ip: String(data.ip) })
      notify('success', 'IP 已解封')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : 'IP 解封失败')
    } finally {
      setIpPardoning(false)
    }
  }

  const columns = [
    { key: 'name', header: '玩家', render: (b: BanRecord) => b.name || b.ip || '-' },
    { key: 'uuid', header: 'UUID / IP', className: 'mono', render: (b: BanRecord) => b.uuid || b.ip || '-' },
    { key: 'reason', header: '原因', render: (b: BanRecord) => b.reason || '-' },
    { key: 'source', header: '来源', render: (b: BanRecord) => <Badge type="info">{b.source || 'manual'}</Badge> },
    { key: 'expires', header: '到期', className: 'mono', render: (b: BanRecord) => b.expires || '永久' },
  ]

  return (
    <PageContainer title="封禁名单" subtitle="管理玩家封禁与 IP 封禁">
      <div style={{ marginBottom: 20 }}>
        <Card title="封禁列表">
          {loading && !bans ? (
            <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
          ) : error ? (
            <div className="flash-err">{error}</div>
          ) : (
            <DataTable
              columns={columns}
              data={bans || []}
              emptyMessage="当前无封禁记录"
              rowKey={(b, i) => `${b.uuid || b.ip || i}`}
            />
          )}
        </Card>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <Card title="解封玩家">
          <FormBuilder
            fields={[
              { name: 'player', label: '玩家名', type: 'text', placeholder: '输入玩家名' },
              { name: 'uuid', label: 'UUID', type: 'text', placeholder: '输入 UUID（可选）' },
            ]}
            onSubmit={handleUnban}
            submitLabel="解封"
            loading={unbanning}
            layout="stack"
          />
        </Card>

        <Card title="IP 封禁">
          <FormBuilder
            fields={[
              { name: 'ip', label: 'IP 地址', type: 'text', placeholder: '例如：192.168.1.1', required: true },
              { name: 'reason', label: '原因', type: 'text', placeholder: '封禁原因' },
            ]}
            onSubmit={handleIpBan}
            submitLabel="封禁 IP"
            loading={ipBanning}
            layout="stack"
          />
        </Card>
      </div>

      <div style={{ marginTop: 20 }}>
        <Card title="IP 解封">
          <FormBuilder
            fields={[
              { name: 'ip', label: 'IP 地址', type: 'text', placeholder: '输入要解封的 IP', required: true },
            ]}
            onSubmit={handleIpPardon}
            submitLabel="解封 IP"
            loading={ipPardoning}
            layout="stack"
          />
        </Card>
      </div>
    </PageContainer>
  )
}
