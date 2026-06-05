import { replayFileSchema, validateSemantics } from '../schema'
import validRing5 from './fixtures/valid-ring-5.json'
import validRing5Byz from './fixtures/valid-ring-5-byzantine.json'

function fixture(): Record<string, unknown> {
  return JSON.parse(JSON.stringify(validRing5))
}

function byzFixture(): Record<string, unknown> {
  return JSON.parse(JSON.stringify(validRing5Byz))
}

function parseAndValidate(data: unknown) {
  const parsed = replayFileSchema.safeParse(data)
  if (!parsed.success) {
    return { structuralError: parsed.error }
  }
  return validateSemantics(parsed.data)
}

// Structural validation (Zod schema)

describe('schema: structural validation', () => {
  it('accepts a valid replay file', () => {
    const result = replayFileSchema.safeParse(validRing5)
    expect(result.success).toBe(true)
  })

  it('accepts missing formatVersion as it is optional', () => {
    const data = fixture()
    delete data.formatVersion
    const result = replayFileSchema.safeParse(data)
    expect(result.success).toBe(true)
  })

  it('rejects unsupported formatVersion', () => {
    const data = fixture()
    data.formatVersion = '2.0'
    const result = replayFileSchema.safeParse(data)
    expect(result.success).toBe(false)
  })

  it('rejects formatVersion that is not an exact string match', () => {
    const data = fixture()
    data.formatVersion = '1.0.0'
    expect(replayFileSchema.safeParse(data).success).toBe(false)

    data.formatVersion = 1.0
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('rejects missing required fields', () => {
    for (const field of ['configuration', 'network', 'sourceNode', 'rounds']) {
      const data = fixture()
      delete data[field]
      const result = replayFileSchema.safeParse(data)
      expect(result.success).toBe(false)
    }
  })

  it('rejects missing nodeCount in configuration', () => {
    const data = fixture()
    const config = data.configuration as Record<string, unknown>
    delete config.nodeCount
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('rejects unknown top-level fields', () => {
    const data = fixture()
    data.extraField = 'unexpected'
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('accepts unknown fields in configuration', () => {
    const data = fixture()
    const config = data.configuration as Record<string, unknown>
    config.customMetadata = 'should be accepted'
    expect(replayFileSchema.safeParse(data).success).toBe(true)
  })

  it('accepts unknown fields in result', () => {
    const data = fixture()
    const result = data.result as Record<string, unknown>
    result.customMetric = 42
    expect(replayFileSchema.safeParse(data).success).toBe(true)
  })

  it('rejects unknown fields in network', () => {
    const data = fixture()
    const net = data.network as Record<string, unknown>
    net.extraField = true
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('rejects unknown fields in round objects', () => {
    const data = fixture()
    const rounds = data.rounds as Record<string, unknown>[]
    rounds[0].extraField = true
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('rejects unknown fields in message objects', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{ messages: Record<string, unknown>[] }>
    rounds[0].messages[0].extraField = true
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('accepts files with no result block', () => {
    const data = fixture()
    delete data.result
    expect(replayFileSchema.safeParse(data).success).toBe(true)
  })

  it('accepts files with empty rounds array', () => {
    const data = fixture()
    data.rounds = []
    delete data.result
    expect(replayFileSchema.safeParse(data).success).toBe(true)
  })
})

// Semantic validation

describe('schema: semantic validation', () => {
  it('accepts a valid replay file', () => {
    const parsed = replayFileSchema.parse(validRing5)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(true)
  })

  it('rejects sourceNode outside valid range', () => {
    const data = fixture()
    data.sourceNode = 99
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) {
      expect(result.error).toContain('sourceNode')
      expect(result.category).toBe('semantic')
    }
  })

  it('rejects edge with endpoint outside valid range', () => {
    const data = fixture()
    const net = data.network as { edges: number[][] }
    net.edges.push([0, 99])
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('outside valid range')
  })

  it('rejects self-loops', () => {
    const data = fixture()
    const net = data.network as { edges: number[][] }
    net.edges.push([2, 2])
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('self-loop')
  })

  it('rejects duplicate edges (normalized)', () => {
    const data = fixture()
    const net = data.network as { edges: number[][] }
    net.edges.push([1, 0])
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('duplicate')
  })

  it('rejects message referring to non-existent edge', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      round: number
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
      newlyInformed: number[]
      totalInformed: number
      messageCount: number
    }>
    rounds[0].messages.push({ sender: 1, receiver: 3, newInfection: false })
    rounds[0].messageCount = rounds[0].messages.length
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('no edge exists')
  })

  it('rejects mismatched messageCount', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{ messageCount: number }>
    rounds[0].messageCount = 999
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('messageCount')
  })

  it('rejects non-ordered round numbers', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{ round: number }>
    rounds[1].round = 1
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('consecutive')
  })

  it('rejects source node appearing in newlyInformed', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      newlyInformed: number[]
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
      messageCount: number
      totalInformed: number
    }>
    // Append source to newlyInformed; source is 0 in the fixture.
    rounds[0].newlyInformed.push(0)
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('source node')
  })

  it('rejects node appearing in newlyInformed across multiple rounds', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      newlyInformed: number[]
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
      messageCount: number
      totalInformed: number
    }>
    rounds[1].newlyInformed.push(1)
    rounds[1].messages.push({ sender: 0, receiver: 1, newInfection: true })
    rounds[1].messageCount = rounds[1].messages.length
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('already informed')
  })

  it('rejects newlyInformed node without a newInfection message', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      newlyInformed: number[]
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
      totalInformed: number
    }>
    rounds[0].messages[0].newInfection = false
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('no message with newInfection = true')
  })

  it('rejects inconsistent totalInformed', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{ totalInformed: number }>
    rounds[0].totalInformed = 99
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('totalInformed')
  })

  it('accepts empty rounds array', () => {
    const data = fixture()
    data.rounds = []
    delete data.result
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(true)
  })

  it('rejects inconsistent result T_end', () => {
    const data = fixture()
    const res = data.result as Record<string, unknown>
    res.T_end = 99
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('T_end')
  })

  it('rejects a message whose sender was not informed before the round', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
      messageCount: number
    }>
    // Replace the round-1 sender with node 2, which is not informed yet.
    rounds[0].messages[0] = { sender: 2, receiver: 1, newInfection: true }
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toMatch(/was not informed/)
  })

  it('rejects newInfection=true targeting an already-informed node', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
    }>
    // Redirect a newInfection=true message at an already-informed node.
    rounds[1].messages[1] = { sender: 1, receiver: 0, newInfection: true }
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toMatch(/already informed/)
  })

  it('rejects newInfection=true whose receiver is not in newlyInformed', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      newlyInformed: number[]
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
      totalInformed: number
      messageCount: number
    }>
    // Strip the receiver from newlyInformed but keep the newInfection=true
    // message. Fix totalInformed so that earlier checks don't fire first.
    rounds[0].newlyInformed = []
    rounds[0].totalInformed = 1
    rounds[1].messages = []
    rounds[1].newlyInformed = []
    rounds[1].totalInformed = 1
    rounds[1].messageCount = 0
    rounds[2].messages = []
    rounds[2].newlyInformed = []
    rounds[2].totalInformed = 1
    rounds[2].messageCount = 0
    delete (data as Record<string, unknown>).result
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toMatch(/not in newlyInformed/)
  })

  it('rejects a self-referential message (sender === receiver)', () => {
    const data = fixture()
    const rounds = data.rounds as Array<{
      messages: Array<{ sender: number; receiver: number; newInfection: boolean }>
    }>
    rounds[0].messages[0] = { sender: 0, receiver: 0, newInfection: false }
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toMatch(/same node/)
  })
})

// Byzantine structural validation

describe('schema: Byzantine structural validation', () => {
  it('accepts a valid Byzantine replay file', () => {
    expect(replayFileSchema.safeParse(validRing5Byz).success).toBe(true)
  })

  it('accepts a file without Byzantine fields (backward compatible)', () => {
    expect(replayFileSchema.safeParse(validRing5).success).toBe(true)
  })

  it('rejects failureProbability > 1', () => {
    const data = byzFixture()
    data.failureProbability = 1.5
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('rejects failureProbability < 0', () => {
    const data = byzFixture()
    data.failureProbability = -0.1
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('rejects non-integer node ids in failedNodes', () => {
    const data = byzFixture()
    data.failedNodes = [1.5]
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })

  it('rejects negative node ids in failedNodes', () => {
    const data = byzFixture()
    data.failedNodes = [-1]
    expect(replayFileSchema.safeParse(data).success).toBe(false)
  })
})

// Byzantine semantic validation

describe('schema: Byzantine semantic validation', () => {
  it('accepts a valid Byzantine file', () => {
    const parsed = replayFileSchema.parse(validRing5Byz)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(true)
  })

  it('rejects failedNodes entry outside valid range', () => {
    const data = byzFixture()
    data.failedNodes = [99]
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('outside valid range')
  })

  it('rejects duplicate entries in failedNodes', () => {
    const data = byzFixture()
    data.failedNodes = [2, 2]
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('duplicate')
  })

  it('rejects source in failedNodes without sourceForcedActive', () => {
    const data = byzFixture()
    data.failedNodes = [0]
    data.sourceForcedActive = false
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('sourceForcedActive')
  })

  it('accepts source in failedNodes when sourceForcedActive is true', () => {
    const data = byzFixture()
    data.failedNodes = [0, 2]
    data.sourceForcedActive = true
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(true)
  })

  it('rejects a failed node appearing as sender in a message', () => {
    const data = byzFixture()
    data.failedNodes = [1]
    const parsed = replayFileSchema.parse(data)
    const result = validateSemantics(parsed)
    expect(result.valid).toBe(false)
    if (!result.valid) expect(result.error).toContain('failed node')
  })
})

// Combined parse + validate pipeline

describe('schema: full validation pipeline', () => {
  it('valid file passes both structural and semantic checks', () => {
    const result = parseAndValidate(validRing5)
    expect('valid' in result && result.valid).toBe(true)
  })

  it('malformed JSON object is rejected structurally', () => {
    const result = parseAndValidate({ random: 'garbage' })
    expect('structuralError' in result).toBe(true)
  })

  it('structurally valid but semantically invalid file is caught', () => {
    const data = fixture()
    data.sourceNode = 99
    const result = parseAndValidate(data)
    expect('valid' in result && !result.valid).toBe(true)
  })
})
