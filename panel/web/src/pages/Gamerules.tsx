import { useState, useMemo } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'
import { useApi } from '../lib/useApi'
import { PanelAPI, api } from '../lib/api'
import { useToast } from '../lib/toast'
import type { Gamerule } from '../lib/types'

export default function Gamerules() {
  const { notify } = useToast()
  const [editing, setEditing] = useState<string | null>(null)
  const [editValue, setEditValue] = useState('')
  const [saving, setSaving] = useState(false)

  const { data: rules, loading, error, refetch } = useApi<Gamerule[]>(
    () => api.get<Gamerule[]>('/api/gamerules'),
    [],
    { interval: 30000 },
  )

  const grouped = useMemo(() => {
    const map = new Map<string, Gamerule[]>()
    for (const r of rules || []) {
      const cat = r.category || '其他'
      if (!map.has(cat)) map.set(cat, [])
      map.get(cat)!.push(r)
    }
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]))
  }, [rules])

  const handleEdit = (rule: Gamerule) => {
    setEditing(rule.rule_id)
    setEditValue(rule.value)
  }

  const handleSave = async (ruleId: string) => {
    setSaving(true)
    try {
      await PanelAPI.payload('gamerule_set', { ruleId, value: editValue })
      notify('success', `已更新 ${ruleId}`)
      setEditing(null)
      refetch()
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '更新失败')
    } finally {
      setSaving(false)
    }
  }

  const isBoolean = (value: string) => value === 'true' || value === 'false'

  return (
    <PageContainer title="Gamerule 矩阵" subtitle="查看与修改游戏规则">
      {loading && !rules ? (
        <div className="shimmer-bg" style={{ height: 200, borderRadius: 10 }} />
      ) : error ? (
        <div className="flash-err">{error}</div>
      ) : (
        <div style={{ display: 'grid', gap: 20 }}>
          {grouped.map(([category, catRules]) => (
            <Card key={category} title={category}>
              <div style={{ display: 'grid', gap: 0 }}>
                {catRules.map((rule) => (
                  <div
                    key={rule.rule_id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '10px 0',
                      borderBottom: '1px solid rgba(148, 163, 184, 0.06)',
                    }}
                  >
                    <div style={{ flex: 1 }}>
                      <span className="mono" style={{ color: 'var(--ink-200)' }}>{rule.rule_id}</span>
                    </div>
                    {editing === rule.rule_id ? (
                      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                        {isBoolean(rule.value) ? (
                          <select
                            className="select"
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            style={{ width: 'auto' }}
                          >
                            <option value="true">true</option>
                            <option value="false">false</option>
                          </select>
                        ) : (
                          <input
                            className="input"
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            style={{ width: 180 }}
                            autoFocus
                          />
                        )}
                        <button
                          className="btn btn-primary btn-sm"
                          onClick={() => handleSave(rule.rule_id)}
                          disabled={saving}
                        >
                          {saving ? '保存中…' : '保存'}
                        </button>
                        <button className="btn btn-ghost btn-sm" onClick={() => setEditing(null)}>
                          取消
                        </button>
                      </div>
                    ) : (
                      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                        <Badge type={isBoolean(rule.value) ? (rule.value === 'true' ? 'ok' : 'warn') : 'info'}>
                          {rule.value}
                        </Badge>
                        <button className="btn btn-ghost btn-sm" onClick={() => handleEdit(rule)}>
                          编辑
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </Card>
          ))}
        </div>
      )}
    </PageContainer>
  )
}
