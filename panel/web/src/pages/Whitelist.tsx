import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

interface WhitelistEntry {
  [k: string]: unknown
  uuid?: string
  name?: string
}

export default function Whitelist() {
  const { notify } = useToast()
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [toggling, setToggling] = useState(false)

  const { data: runtime } = useApi<RuntimeSnapshot>(
    () => PanelAPI.runtime(),
    [],
    { interval: 15000 },
  )

  const { data: entries, loading, error, refetch } = useApi<WhitelistEntry[]>(
    () => api.get<WhitelistEntry[]>('/api/whitelist'),
    [],
    { interval: 15000 },
  )

  const whitelistOn = Boolean(runtime?.whitelist_on)

  const handleToggle = async () => {
    setToggling(true)
    try {
      await api.post('/api/whitelist/toggle', { enabled: !whitelistOn })
      notify('success', !whitelistOn ? '白名单已开启' : '白名单已关闭')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '操作失败')
    } finally {
      setToggling(false)
    }
  }

  const handleAdd = async (data: Record<string, string | number | boolean>) => {
    setAdding(true)
    try {
      await api.post('/api/whitelist/add', { player: String(data.player) })
      notify('success', '已添加到白名单')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '添加失败')
    } finally {
      setAdding(false)
    }
  }

  const handleRemove = async (data: Record<string, string | number | boolean>) => {
    setRemoving(true)
    try {
      await api.post('/api/whitelist/remove', { player: String(data.player) })
      notify('success', '已从白名单移除')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '移除失败')
    } finally {
      setRemoving(false)
    }
  }

  const columns = [
    { key: 'name', header: '玩家名', render: (e: WhitelistEntry) => e.name || '-' },
    { key: 'uuid', header: 'UUID', className: 'mono' },
  ]

  return (
    <PageContainer title="白名单管理" subtitle="控制服务器访问白名单">
      <div style={{ marginBottom: 20 }}>
        <Card title="白名单状态">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <p style={{ fontSize: 14, color: 'var(--ink-300)' }}>当前状态：</p>
              <div style={{ marginTop: 8 }}>
                <Badge type={whitelistOn ? 'ok' : 'warn'}>
                  {whitelistOn ? '已开启' : '已关闭'}
                </Badge>
              </div>
              <p style={{ fontSize: 12, color: 'var(--ink-500)', marginTop: 8 }}>
                {whitelistOn
                  ? '仅白名单内玩家可加入服务器'
                  : '所有玩家均可加入服务器'}
              </p>
            </div>
            <button
              className={`btn ${whitelistOn ? 'btn-danger' : 'btn-primary'}`}
              onClick={handleToggle}
              disabled={toggling}
            >
              {toggling ? '处理中…' : whitelistOn ? '关闭白名单' : '开启白名单'}
            </button>
          </div>
        </Card>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="添加到白名单">
          <FormBuilder
            fields={[
              { name: 'player', label: '玩家名', type: 'text', placeholder: '输入玩家名', required: true },
            ]}
            onSubmit={handleAdd}
            submitLabel="添加"
            loading={adding}
            layout="stack"
          />
        </Card>

        <Card title="从白名单移除">
          <FormBuilder
            fields={[
              { name: 'player', label: '玩家名', type: 'text', placeholder: '输入玩家名', required: true },
            ]}
            onSubmit={handleRemove}
            submitLabel="移除"
            loading={removing}
            layout="stack"
          />
        </Card>
      </div>

      <Card title="白名单列表">
        {loading && !entries ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <DataTable
            columns={columns}
            data={entries || []}
            emptyMessage="白名单为空"
            rowKey={(e, i) => e.uuid || String(i)}
          />
        )}
      </Card>
    </PageContainer>
  )
}
