import { useState } from 'react'
import { PageContainer, Card } from '../components/ui/Card'
import { FormBuilder } from '../components/ui/FormBuilder'
import { PanelAPI } from '../lib/api'
import { useToast } from '../lib/toast'

export default function MessagesTitle() {
  const { notify } = useToast()
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (data: Record<string, string | number | boolean>) => {
    setSubmitting(true)
    try {
      await PanelAPI.payload('title_broadcast', {
        kind: String(data.kind),
        text: String(data.text || ''),
        subtitle: String(data.subtitle || ''),
      })
      notify('success', '标题已广播')
    } catch (e) {
      notify('error', e instanceof Error ? e.message : '广播失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PageContainer title="标题 / 字幕广播" subtitle="向全体玩家发送大字标题、副标题或动作栏消息">
      <div style={{ marginBottom: 20 }}>
        <Card title="发送标题">
          <FormBuilder
            fields={[
              {
                name: 'kind',
                label: '消息类型',
                type: 'select',
                required: true,
                defaultValue: 'title',
                options: [
                  { value: 'title', label: '主标题（Title）' },
                  { value: 'subtitle', label: '副标题（Subtitle）' },
                  { value: 'actionbar', label: '动作栏（ActionBar）' },
                ],
              },
              { name: 'text', label: '主文本', type: 'text', placeholder: '例如：欢迎来到服务器', required: true },
              { name: 'subtitle', label: '副文本（仅 Title 模式有效）', type: 'text', placeholder: '可选副标题文本' },
            ]}
            onSubmit={handleSubmit}
            submitLabel="广播标题"
            loading={submitting}
          />
        </Card>
      </div>

      <Card title="使用说明">
        <ul style={{ paddingLeft: 20, lineHeight: 2, color: 'var(--ink-300)', fontSize: 13 }}>
          <li><strong style={{ color: 'var(--accent-cyan)' }}>主标题</strong>：在屏幕中央显示大字文本，支持副标题。</li>
          <li><strong style={{ color: 'var(--accent-cyan)' }}>副标题</strong>：作为主标题下方的小字行显示。</li>
          <li><strong style={{ color: 'var(--accent-cyan)' }}>动作栏</strong>：在快捷栏上方显示一条文字，不影响游戏视野。</li>
          <li>标题消息默认对全体在线玩家广播，支持彩色代码（&amp; 符号）。</li>
        </ul>
      </Card>
    </PageContainer>
  )
}
