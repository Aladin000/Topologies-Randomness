import type { Configuration } from '../model'

interface ConfigPanelProps {
  configuration: Configuration
}

// Display order for known keys; unknown keys are appended afterwards.
const KNOWN_KEYS: ReadonlyArray<keyof Configuration & string> = [
  'topologyType',
  'nodeCount',
  'viewFraction',
  'fanOut',
  'graphSeed',
  'simulationSeed',
  'k',
  'm',
  'p',
  'beta',
  'maxRounds',
  'stableRounds',
  'failureProbability',
]

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'number') {
    return Number.isInteger(value) ? String(value) : value.toFixed(3)
  }
  if (typeof value === 'string' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}

export function ConfigPanel({ configuration }: ConfigPanelProps) {
  const entries: Array<[string, unknown]> = []
  const seen = new Set<string>()

  for (const key of KNOWN_KEYS) {
    if (configuration[key] !== undefined) {
      entries.push([key, configuration[key]])
      seen.add(key)
    }
  }
  for (const [key, value] of Object.entries(configuration)) {
    if (!seen.has(key)) entries.push([key, value])
  }

  return (
    <section className="ui-detail-panel">
      <h2 className="ui-detail-title">Configuration</h2>
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
