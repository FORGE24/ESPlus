import { ReactNode, FormEvent } from 'react'

interface Field {
  name: string
  label: string
  type?: 'text' | 'password' | 'number' | 'select' | 'textarea' | 'checkbox'
  placeholder?: string
  options?: { value: string; label: string }[]
  defaultValue?: string | number | boolean
  required?: boolean
  min?: number
  max?: number
  width?: string
}

interface FormBuilderProps {
  fields: Field[]
  onSubmit: (data: Record<string, string | number | boolean>) => void
  submitLabel?: string
  loading?: boolean
  actions?: ReactNode
  layout?: 'grid' | 'stack'
}

export function FormBuilder({
  fields,
  onSubmit,
  submitLabel = '提交',
  loading,
  actions,
  layout = 'grid',
}: FormBuilderProps) {
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const form = e.target as HTMLFormElement
    const data: Record<string, string | number | boolean> = {}
    fields.forEach((f) => {
      const el = form.elements.namedItem(f.name) as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
      if (!el) return
      if (f.type === 'checkbox') {
        data[f.name] = (el as HTMLInputElement).checked
      } else if (f.type === 'number') {
        data[f.name] = el.value === '' ? '' : Number(el.value)
      } else {
        data[f.name] = el.value
      }
    })
    onSubmit(data)
  }

  return (
    <form onSubmit={handleSubmit} className={layout === 'grid' ? 'form-grid' : ''} style={layout === 'stack' ? { display: 'grid', gap: 16, maxWidth: 560 } : undefined}>
      {fields.map((f) => (
        <div key={f.name} style={{ width: f.width || '100%' }}>
          <label className="label">{f.label}{f.required && <span style={{ color: 'var(--accent-rose)' }}> *</span>}</label>
          {f.type === 'select' ? (
            <select name={f.name} className="select" defaultValue={String(f.defaultValue ?? '')} required={f.required}>
              <option value="">— 选择 —</option>
              {f.options?.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          ) : f.type === 'textarea' ? (
            <textarea name={f.name} className="textarea" placeholder={f.placeholder} defaultValue={String(f.defaultValue ?? '')} required={f.required} />
          ) : f.type === 'checkbox' ? (
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
              <input type="checkbox" name={f.name} defaultChecked={Boolean(f.defaultValue)} style={{ width: 18, height: 18, accentColor: 'var(--accent-cyan)' }} />
              <span style={{ fontSize: 13, color: 'var(--ink-300)' }}>{f.placeholder || '启用'}</span>
            </label>
          ) : (
            <input
              type={f.type || 'text'}
              name={f.name}
              className="input"
              placeholder={f.placeholder}
              defaultValue={f.defaultValue !== undefined ? String(f.defaultValue) : ''}
              required={f.required}
              min={f.min}
              max={f.max}
            />
          )}
        </div>
      ))}
      <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 10, alignItems: 'center' }}>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? '提交中…' : submitLabel}
        </button>
        {actions}
      </div>
    </form>
  )
}
