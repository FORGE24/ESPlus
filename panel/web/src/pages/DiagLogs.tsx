import { useState, useEffect, useRef } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'
import type { ServerLog } from '../lib/types'

export default function DiagLogs() {
  const { notify } = useToast()
  const [level, setLevel] = useState('')
  const [query, setQuery] = useState('')
  const [activeLevel, setActiveLevel] = useState('')
  const [activeQuery, setActiveQuery] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(true)
  const logboxRef = useRef<HTMLDivElement>(null)

  const { data, loading, error } = useApi(
    () => PanelAPI.serverLogs(activeLevel, activeQuery),
    [activeLevel, activeQuery],
    { interval: autoRefresh ? 5000 : undefined },
  )

  useEffect(() => {
    if (logboxRef.current) {
      logboxRef.current.scrollTop = logboxRef.current.scrollHeight
    }
  }, [data])

  const handleFilter = (e: React.FormEvent) => {
    e.preventDefault()
    setActiveLevel(level)
    setActiveQuery(query)
    notify('info', '日志已刷新')
  }

  if (loading && !data) {
    return (
      <PageContainer title="服务器日志" subtitle="实时日志查看与过滤">
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      </PageContainer>
    )
  }
  if (error) return <PageContainer title="服务器日志"><div className="flash-err">{error}</div></PageContainer>

  const logs: ServerLog[] = data || []
  const errorCount = logs.filter((l) => l.level === 'ERROR' || l.level === 'FATAL').length
  const warnCount = logs.filter((l) => l.level === 'WARN').length

  const levelColors: Record<string, string> = {
    DEBUG: 'var(--ink-500)',
    INFO: 'var(--ink-200)',
    WARN: 'var(--warn)',
    ERROR: 'var(--danger)',
    FATAL: 'var(--danger)',
  }

  return (
    <PageContainer
      title="服务器日志"
      subtitle="实时日志查看与过滤"
      actions={
        <button
          className={`btn btn-sm ${autoRefresh ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setAutoRefresh(!autoRefresh)}
        >
          {autoRefresh ? '自动刷新：开' : '自动刷新：关'}
        </button>
      }
    >
      <Card title="日志过滤" style={{ marginBottom: 20 }}>
        <form onSubmit={handleFilter} className="form-grid">
          <div>
            <label className="label">日志级别</label>
            <select
              className="select"
              value={level}
              onChange={(e) => setLevel(e.target.value)}
            >
              <option value="">全部</option>
              <option value="DEBUG">DEBUG</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
              <option value="FATAL">FATAL</option>
            </select>
          </div>
          <div>
            <label className="label">搜索关键词</label>
            <input
              type="text"
              className="input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索日志内容…"
            />
          </div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end' }}>
            <button type="submit" className="btn btn-primary">过滤</button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => { setLevel(''); setQuery(''); setActiveLevel(''); setActiveQuery('') }}
            >
              重置
            </button>
          </div>
        </form>
        <div style={{ display: 'flex', gap: 16, marginTop: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Badge type="info">共 {logs.length} 条</Badge>
          </div>
          {errorCount > 0 && <Badge type="danger">{errorCount} 条错误</Badge>}
          {warnCount > 0 && <Badge type="warn">{warnCount} 条警告</Badge>}
        </div>
      </Card>

      <Card title="日志输出" noPadding>
        <div ref={logboxRef} className="logbox">
          {logs.length === 0 ? (
            <div style={{ color: 'var(--ink-500)', textAlign: 'center', padding: 40 }}>
              暂无日志记录
            </div>
          ) : (
            logs.map((log) => (
              <div
                key={log.id}
                className={`log-${log.level}`}
                style={{ marginBottom: 2, lineHeight: 1.6 }}
              >
                <span style={{ color: 'var(--ink-500)' }}>[{log.ts}]</span>
                {' '}
                <span style={{ color: levelColors[log.level] || 'var(--ink-300)', fontWeight: 600 }}>
                  [{log.level}]
                </span>
                {log.logger && <span style={{ color: 'var(--ink-500)' }}> [{log.logger}]</span>}
                {' '}
                {log.message}
              </div>
            ))
          )}
        </div>
      </Card>
    </PageContainer>
  )
}
