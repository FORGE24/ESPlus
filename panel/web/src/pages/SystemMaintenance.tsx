import { useState } from 'react'
import { PageContainer, Card, StatCard } from '../components/ui/Card'
import { DataTable, Badge } from '../components/ui/DataTable'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { RuntimeSnapshot, OnlinePlayer } from '../lib/types'

export default function SystemMaintenance() {
  const { notify } = useToast()
  const { data: runtime, loading: rtLoading, error: rtError, refetch: rtRefetch } = useApi(() => PanelAPI.runtime(), [], { interval: 10000 })
  const { data: players, loading: pLoading } = useApi(() => PanelAPI.onlinePlayers(), [], { interval: 10000 })
  const [busy, setBusy] = useState(false)

  if (rtLoading && !runtime) {
    return (
      <PageContainer title="维护模式" subtitle="服务器维护 · 踢出玩家 · 设置超时与 MOTD">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (rtError) return <PageContainer title="维护模式"><div className="flash-err">{rtError}</div></PageContainer>
  if (!runtime) return null

  const rt = runtime as RuntimeSnapshot & Record<string, unknown>
  const onlinePlayers: OnlinePlayer[] = players || []
  const maintenanceActive = Boolean(rt.maintenance ?? rt.maintenance_mode ?? rt.maintenanceActive ?? false)
  const idleTimeout = Number(rt.idle_timeout ?? rt.idleTimeout ?? 0)
  const currentMotd = String(rt.motd ?? '')

  const handleKickAll = async (formData: Record<string, string | number | boolean>) => {
    const reason = String(formData.reason || '服务器维护中，请稍后再试')
    const useWhitelist = Boolean(formData.whitelist)
    setBusy(true)
    try {
      await PanelAPI.payload('maintenance_kick', { reason, whitelist: String(useWhitelist) })
      notify('success', `已踢出所有玩家${useWhitelist ? '（白名单已启用）' : ''}`)
      rtRefetch()
    } catch (e) {
      notify('error', `踢出失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleClearMaintenance = async () => {
    if (!window.confirm('确认清除维护模式？玩家将可以正常连接。')) return
    setBusy(true)
    try {
      await PanelAPI.payload('maintenance_clear', {})
      notify('success', '维护模式已清除')
      rtRefetch()
    } catch (e) {
      notify('error', `清除失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleSetIdleTimeout = async (formData: Record<string, string | number | boolean>) => {
    const minutes = Number(formData.minutes)
    if (isNaN(minutes) || minutes < 0) {
      notify('error', '请输入有效的超时分钟数')
      return
    }
    setBusy(true)
    try {
      await PanelAPI.payload('set_idle_timeout', { minutes })
      notify('success', `空闲超时已设置为 ${minutes} 分钟`)
      rtRefetch()
    } catch (e) {
      notify('error', `设置失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const handleSetMotd = async (formData: Record<string, string | number | boolean>) => {
    const motd = String(formData.motd || '')
    setBusy(true)
    try {
      await PanelAPI.payload('set_motd', { motd })
      notify('success', 'MOTD 已更新')
      rtRefetch()
    } catch (e) {
      notify('error', `设置失败：${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <PageContainer title="维护模式" subtitle="服务器维护 · 踢出玩家 · 设置超时与 MOTD">
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <StatCard label="维护状态" value={maintenanceActive ? '维护中' : '正常'} color={maintenanceActive ? 'rose' : 'emerald'} />
        <StatCard label="在线玩家" value={onlinePlayers.length} color="cyan" />
        <StatCard label="空闲超时" value={idleTimeout} suffix="分钟" color="amber" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="维护踢出所有玩家">
          <div className="flash-err" style={{ marginBottom: 16 }}>
            将踢出所有在线玩家，可选择同时启用白名单。
          </div>
          <FormBuilder
            fields={[
              { name: 'reason', label: '踢出原因', placeholder: '服务器维护中，请稍后再试', defaultValue: '服务器维护中，请稍后再试', width: '100%' },
              { name: 'whitelist', label: '同时启用白名单', type: 'checkbox', placeholder: '踢出后仅允许白名单玩家加入', width: '100%' },
            ]}
            onSubmit={handleKickAll}
            submitLabel={busy ? '处理中…' : '踢出所有玩家'}
            loading={busy}
            layout="stack"
          />
        </Card>

        <Card title="清除维护模式">
          <div style={{ marginBottom: 16 }}>
            <Badge type={maintenanceActive ? 'danger' : 'ok'}>
              {maintenanceActive ? '维护模式进行中' : '未启用维护模式'}
            </Badge>
          </div>
          <p style={{ fontSize: 13, color: 'var(--ink-400)', marginBottom: 16 }}>
            清除维护模式后，玩家可以正常连接服务器。如果之前启用了白名单，请确认是否需要手动关闭。
          </p>
          <button
            className="btn btn-primary"
            disabled={busy || !maintenanceActive}
            onClick={handleClearMaintenance}
          >
            {busy ? '处理中…' : '清除维护模式'}
          </button>
        </Card>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 20 }}>
        <Card title="设置空闲超时">
          <p style={{ fontSize: 13, color: 'var(--ink-400)', marginBottom: 16 }}>
            当前超时：{idleTimeout} 分钟。设为 0 表示禁用空闲超时。
          </p>
          <FormBuilder
            fields={[
              { name: 'minutes', label: '超时分钟数', type: 'number', min: 0, max: 1440, defaultValue: idleTimeout, required: true, width: '100%' },
            ]}
            onSubmit={handleSetIdleTimeout}
            submitLabel={busy ? '处理中…' : '设置超时'}
            loading={busy}
            layout="stack"
          />
        </Card>

        <Card title="设置 MOTD">
          <p style={{ fontSize: 13, color: 'var(--ink-400)', marginBottom: 16 }}>
            当前 MOTD：{currentMotd || '未设置'}
          </p>
          <FormBuilder
            fields={[
              { name: 'motd', label: '服务器 MOTD', type: 'textarea', placeholder: '输入新的 MOTD 文本', defaultValue: currentMotd, width: '100%' },
            ]}
            onSubmit={handleSetMotd}
            submitLabel={busy ? '处理中…' : '更新 MOTD'}
            loading={busy}
            layout="stack"
          />
        </Card>
      </div>

      {!pLoading && onlinePlayers.length > 0 && (
        <Card title={`在线玩家（${onlinePlayers.length}）`}>
          <DataTable
            columns={[
              { key: 'name', header: '玩家名' },
              { key: 'uuid', header: 'UUID', className: 'mono' },
              { key: 'dimension', header: '维度' },
              { key: 'ping', header: '延迟', render: (p: any) => `${p.ping ?? 0} ms` },
            ]}
            data={onlinePlayers as unknown as Record<string, unknown>[]}
            emptyMessage="暂无在线玩家"
            rowKey={(p) => (p as unknown as OnlinePlayer).uuid}
          />
        </Card>
      )}
    </PageContainer>
  )
}
