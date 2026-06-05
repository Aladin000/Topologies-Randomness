import type { Result } from '../model'

interface ResultPanelProps {
  result: NonNullable<Result>
}

const KNOWN_KEYS: ReadonlyArray<string> = [
  'T_end',
  'Omega',
  'M',
  'alpha',
  'L_0.5',
  'L_0.9',
  'L_1.0',
  'F_eff',
  'R_run',
]

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'number') {
    return Number.isInteger(value) ? String(value) : value.toFixed(3)
  }
  if (typeof value === 'string' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}

export function ResultPanel({ result }: ResultPanelProps) {
  const record = result as Record<string, unknown>
  const entries: Array<[string, unknown]> = []
  const seen = new Set<string>()

  for (const key of KNOWN_KEYS) {
    if (record[key] !== undefined) {
      entries.push([key, record[key]])
      seen.add(key)
    }
  }
  for (const [key, value] of Object.entries(record)) {
    if (!seen.has(key)) entries.push([key, value])
  }

  if (entries.length === 0) return null

  return (
    <section className="ui-detail-panel">
      <h2 className="ui-detail-title">Result</h2>
      <dl className="ui-detail-list">
        {entries.map(([key, value]) => (
          <div key={key} className="ui-detail-row">
            <dt>{key}</dt>
            <dd>{formatValue(value)}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
