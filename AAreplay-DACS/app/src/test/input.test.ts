import { parseReplayFile, validateReplayData } from '../input'
import validRing5 from './fixtures/valid-ring-5.json'

function validJson(): string {
  return JSON.stringify(validRing5)
}

function fixture(): Record<string, unknown> {
  return JSON.parse(JSON.stringify(validRing5))
}

// JSON parsing stage

describe('input: JSON parsing', () => {
  it('rejects malformed JSON', () => {
    const result = parseReplayFile('{ not valid json }')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.category).toBe('parse')
      expect(result.error).toContain('Invalid JSON')
    }
  })

  it('rejects empty string', () => {
    const result = parseReplayFile('')
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.category).toBe('parse')
  })

  it('accepts valid JSON', () => {
    const result = parseReplayFile(validJson())
    expect(result.ok).toBe(true)
  })
})

// Structural validation stage

describe('input: structural validation', () => {
  it('rejects non-object input', () => {
    const result = parseReplayFile('"just a string"')
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.category).toBe('structural')
  })

  it('rejects array input', () => {
    const result = parseReplayFile('[1, 2, 3]')
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.category).toBe('structural')
  })

  it('accepts missing formatVersion as it is optional', () => {
    const data = fixture()
    delete data.formatVersion
    const result = validateReplayData(data)
    expect(result.ok).toBe(true)
  })

  it('rejects unknown top-level fields', () => {
    const data = fixture()
    data.mystery = 'field'
    const result = validateReplayData(data)
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.category).toBe('structural')
  })

  it('reports the failing field path in error message', () => {
    const data = fixture()
    ;(data.configuration as Record<string, unknown>).nodeCount = 'not a number'
    const result = validateReplayData(data)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.category).toBe('structural')
      expect(result.error).toContain('Structural validation failed')
    }
  })
})

// Semantic validation stage

describe('input: semantic validation', () => {
  it('rejects semantically invalid but structurally valid file', () => {
    const data = fixture()
    data.sourceNode = 99
    const result = validateReplayData(data)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.category).toBe('semantic')
      expect(result.error).toContain('Semantic validation failed')
      expect(result.error).toContain('sourceNode')
    }
  })
})

// Full pipeline

describe('input: full pipeline', () => {
  it('returns validated file and normalized edges on success', () => {
    const result = parseReplayFile(validJson())
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.file.formatVersion).toBe('1.0')
      expect(result.file.configuration.nodeCount).toBe(5)
      expect(result.file.sourceNode).toBe(0)
      expect(result.file.rounds).toHaveLength(3)
      expect(result.normalizedEdges.size).toBe(5)
    }
  })

  it('malformed JSON fails at parse stage, not structural', () => {
    const result = parseReplayFile('not json at all')
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.category).toBe('parse')
  })

  it('missing required field fails at structural stage', () => {
    const data = fixture()
    delete data.network
    const result = parseReplayFile(JSON.stringify(data))
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.category).toBe('structural')
  })

  it('inconsistent totalInformed fails at semantic stage', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{ totalInformed: number }>
    rounds[0].totalInformed = 999
    const result = parseReplayFile(JSON.stringify(data))
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.category).toBe('semantic')
  })
})
