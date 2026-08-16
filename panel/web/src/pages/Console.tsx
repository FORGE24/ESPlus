import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { ServerLog } from '../lib/types'

export default function Console() {
  const { notify } = useToast()
  const [level, setLevel] = useState('')
  const [q, setQ] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const { data: logs, loading, error, refetch } = useApi<ServerLog[]>(
    () => PanelAPI.serverLogs(level, q),
    [level, q],
    { interval: 8000 },
  )

  const handleCommand = async (data: Record<string, string | number | boolean>) => {
    const cmd = String(data.command).trim()
    if (!cmd) return
    setSubmitting(true)
    try {
      await PanelAPI.consoleCmd(cmd)
      notify('success', '指令已执行')
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '指令执行失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PageContainer title="游戏控制台" subtitle="在线执行指令 · 实时日志查看">
      <div style={{ marginBottom: 20 }}>
        <Card title="执行指令">
          <FormBuilder
            fields={[
              { name: 'command', label: '指令', type: 'text', placeholder: '例如：/weather clear  或  difficulty hard', required: true, width: '100%' },
            ]}
            onSubmit={handleCommand}
            submitLabel="执行"
            loading={submitting}
            layout="stack"
          />
        </Card>
      </div>

      <Card
        title="服务器日志"
        actions={<button className="btn btn-ghost btn-sm" onClick={refetch}>刷新</button>}
      >
        <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
          <div style={{ minWidth: 160 }}>
            <label className="label">级别筛选</label>
            <select className="select" value={level} onChange={(e) => setLevel(e.target.value)}>
              <option value="">全部级别</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
              <option value="DEBUG">DEBUG</option>
              <option value="FATAL">FATAL</option>
            </select>
          </div>
          <div style={{ flex: 1, minWidth: 240 }}>
            <label className="label">关键字搜索</label>
            <input
              className="input"
              placeholder="搜索日志内容…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
        </div>

        {loading && !logs ? (
          <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
        ) : error ? (
          <div className="flash-err">{error}</div>
        ) : (
          <div className="logbox">
            {logs && logs.length > 0 ? (
              logs.map((log) => (
                <div key={log.id} style={{ marginBottom: 2 }}>
                  <span style={{ color: 'var(--ink-500)' }}>[{log.ts}]</span>{' '}
                  <span className={`log-${log.level}`} style={{ fontWeight: 600 }}>
                    [{log.level}]
                  </span>{' '}
                  {log.logger && <span style={{ color: 'var(--ink-400)' }}>[{log.logger}] </span>}
                  <span className={`log-${log.level}`}>{log.message}</span>
                </div>
              ))
            ) : (
              <p style={{ color: 'var(--ink-500)' }}>暂无日志记录</p>
            )}
          </div>
        )}
      </Card>
    </PageContainer>
  )
}
