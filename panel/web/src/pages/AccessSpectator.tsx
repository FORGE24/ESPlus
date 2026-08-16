import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot } from '../lib/types'

export default function AccessSpectator() {
  const { notify } = useToast()
  const { data, loading, error, refetch } = useApi(() => PanelAPI.runtime(), [], { interval: 15000 })
  const [busy, setBusy] = useState(false)

  if (loading && !data) {
    return (
      <PageContainer title="旁观者策略" subtitle="旁观者生成区块策略管理">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="旁观者策略"><div className="flash-err">{error}</div></PageContainer>
  if (!data) return null

  const rt = data as RuntimeSnapshot & Record<string, unknown>
  const current = Boolean(rt.spectatorsGenerateChunks ?? rt.spectators_generate_chunks ?? false)

  const handleToggle = async (formData: Record<string, string | number | boolean>) => {
    const enabled = Boolean(formData.enabled)
    setBusy(true)
    try {
      await PanelAPI.payload('gamerule_set', {
        rule: 'spectatorsGenerateChunks',
        value: String(enabled),
      })
      notify('success', `旁观者生成区块已${enabled ? '启用' : '禁用'}`)
      refetch()
    } catch (e) {
      notify('error', `操作失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <PageContainer title="旁观者策略" subtitle="旁观者生成区块策略管理">
      <Card title="当前状态" style={{ marginBottom: 20 }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '20px 24px',
          background: 'var(--bg-700)',
          borderRadius: 'var(--radius-sm)',
          border: '1px solid var(--glass-border)',
        }}>
          <div style={{
            width: 12, height: 12, borderRadius: '50%',
            background: current ? 'var(--accent-emerald)' : 'var(--ink-500)',
            boxShadow: current ? '0 0 12px var(--accent-emerald)' : 'none',
          }} />
          <div>
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-100)' }}>
              spectatorsGenerateChunks
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-400)', marginTop: 4 }}>
              {current
                ? '旁观者模式玩家会触发区块生成'
                : '旁观者模式玩家不会触发区块生成（减少服务器负载）'}
            </div>
          </div>
          <div style={{ marginLeft: 'auto' }}>
            <Badge type={current ? 'ok' : 'info'}>{current ? '已启用' : '已禁用'}</Badge>
          </div>
        </div>
      </Card>

      <Card title="切换策略" style={{ marginBottom: 20 }}>
        <FormBuilder
          fields={[
            {
              name: 'enabled',
              label: '启用旁观者生成区块',
              type: 'checkbox',
              defaultValue: current,
              placeholder: '勾选启用 spectatorsGenerateChunks',
              width: '100%',
            },
          ]}
          onSubmit={handleToggle}
          submitLabel={busy ? '处理中…' : '应用更改'}
          loading={busy}
          layout="stack"
        />
      </Card>

      <Card title="策略说明">
        <div style={{ display: 'grid', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="info">启用</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              旁观者移动时会加载新区块，适合需要旁观探索的场景，但会增加服务器内存和 CPU 负载。
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Badge type="warn">禁用</Badge>
            <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>
              旁观者不会触发区块生成，仅能查看已加载区域，可有效降低服务器资源消耗。
            </span>
          </div>
        </div>
      </Card>
    </PageContainer>
  )
}
