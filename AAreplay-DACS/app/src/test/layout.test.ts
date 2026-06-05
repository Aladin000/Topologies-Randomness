import { computeLayout, defaultLayoutType } from '../layout'
import type { ReplayFile } from '../model'
import validRing5 from './fixtures/valid-ring-5.json'

const ring5 = validRing5 as unknown as ReplayFile

function makeFile(overrides: {
  nodeCount?: number
  topologyType?: string
  edges?: [number, number][]
}): ReplayFile {
  const nodeCount = overrides.nodeCount ?? 5
  const edges = overrides.edges ?? [[0, 1], [1, 2], [2, 3], [3, 4], [4, 0]]
  return {
    formatVersion: '1.0',
    configuration: {
      nodeCount,
      topologyType: overrides.topologyType,
    },
    network: { edges },
    sourceNode: 0,
    rounds: [],
  } as unknown as ReplayFile
}

// Layout type selection

describe('layout: default layout type', () => {
  it('selects circular for ring topology', () => {
    expect(defaultLayoutType('ring')).toBe('circular')
  })

  it('selects circular for Ring (case insensitive)', () => {
    expect(defaultLayoutType('Ring')).toBe('circular')
  })

  it('selects circular for small-world topology', () => {
    expect(defaultLayoutType('small-world')).toBe('circular')
  })

  it('selects circular for small-world variants (camelCase, snake, spaced)', () => {
    expect(defaultLayoutType('SmallWorld')).toBe('circular')
    expect(defaultLayoutType('small_world')).toBe('circular')
    expect(defaultLayoutType('Small World')).toBe('circular')
    expect(defaultLayoutType('SMALLWORLD')).toBe('circular')
  })

  it('selects force-directed for random topology', () => {
    expect(defaultLayoutType('random')).toBe('force-directed')
  })

  it('selects force-directed for scale-free topology', () => {
    expect(defaultLayoutType('scale-free')).toBe('force-directed')
  })

  it('selects force-directed when topology is undefined', () => {
    expect(defaultLayoutType(undefined)).toBe('force-directed')
  })
})

// Circular layout

describe('layout: circular', () => {
  it('produces correct number of positions', () => {
    const positions = computeLayout(ring5, 600, 'circular')
    expect(positions).toHaveLength(5)
  })

  it('assigns unique positions to each node', () => {
    const positions = computeLayout(ring5, 600, 'circular')
    const ids = positions.map(p => p.id)
    expect(new Set(ids).size).toBe(5)
  })

  it('places nodes roughly on a circle', () => {
    const size = 600
    const positions = computeLayout(ring5, size, 'circular')
    const expectedRadius = size * 0.4

    for (const pos of positions) {
      const distance = Math.sqrt(pos.x ** 2 + pos.y ** 2)
      expect(distance).toBeCloseTo(expectedRadius, 0)
    }
  })

  it('places first node at the top (x ≈ 0, y < 0)', () => {
    const size = 600
    const positions = computeLayout(ring5, size, 'circular')
    const first = positions[0]
    expect(first.x).toBeCloseTo(0, 0)
    expect(first.y).toBeLessThan(0)
  })

  it('produces the same result on repeated calls (deterministic)', () => {
    const a = computeLayout(ring5, 600, 'circular')
    const b = computeLayout(ring5, 600, 'circular')
    expect(a).toEqual(b)
  })
})

// Force-directed layout

describe('layout: force-directed', () => {
  it('produces correct number of positions', () => {
    const positions = computeLayout(ring5, 600, 'force-directed')
    expect(positions).toHaveLength(5)
  })

  it('assigns unique ids', () => {
    const positions = computeLayout(ring5, 600, 'force-directed')
    const ids = positions.map(p => p.id)
    expect(new Set(ids).size).toBe(5)
  })

  it('positions are within bounds', () => {
    const size = 600
    const positions = computeLayout(ring5, size, 'force-directed')
    const halfSize = size * 0.45

    for (const pos of positions) {
      expect(pos.x).toBeGreaterThanOrEqual(-halfSize)
      expect(pos.x).toBeLessThanOrEqual(halfSize)
      expect(pos.y).toBeGreaterThanOrEqual(-halfSize)
      expect(pos.y).toBeLessThanOrEqual(halfSize)
    }
  })

  it('nodes are not all at the same position', () => {
    const positions = computeLayout(ring5, 600, 'force-directed')
    const uniquePositions = new Set(
      positions.map(p => `${Math.round(p.x)},${Math.round(p.y)}`)
    )
    expect(uniquePositions.size).toBeGreaterThan(1)
  })
})

// Edge cases

describe('layout: edge cases', () => {
  it('returns empty array for zero nodes', () => {
    const file = makeFile({ nodeCount: 0, edges: [] })
    const positions = computeLayout(file, 600)
    expect(positions).toHaveLength(0)
  })

  it('returns single centered node for one node', () => {
    const file = makeFile({ nodeCount: 1, edges: [] })
    const positions = computeLayout(file, 600)
    expect(positions).toHaveLength(1)
    expect(positions[0]).toEqual({ id: 0, x: 0, y: 0 })
  })

  it('uses topology-aware default when no override given', () => {
    const ringFile = makeFile({ topologyType: 'ring' })
    const randomFile = makeFile({ topologyType: 'random' })

    const ringLayout = computeLayout(ringFile, 600)
    const randomLayout = computeLayout(randomFile, 600)

    // Ring: all nodes at the same distance from center.
    const ringDistances = ringLayout.map(p => Math.sqrt(p.x ** 2 + p.y ** 2))
    const ringDistanceVariance = Math.max(...ringDistances) - Math.min(...ringDistances)
    expect(ringDistanceVariance).toBeLessThan(1)

    // Random: force-directed produces valid positions.
    expect(randomLayout).toHaveLength(5)
  })
})
