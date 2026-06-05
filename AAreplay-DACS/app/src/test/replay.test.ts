import {
  computeReplayState,
  getNodeState,
  roundDelayMs,
  pulseDurationMs,
  DEFAULT_SPEED,
} from '../replay'
import type { ReplayFile } from '../model'
import validRing5 from './fixtures/valid-ring-5.json'
import validRing5Byz from './fixtures/valid-ring-5-byzantine.json'

const file = validRing5 as unknown as ReplayFile
const byzFile = validRing5Byz as unknown as ReplayFile

function emptyRoundsFile(): ReplayFile {
  return {
    formatVersion: '1.0',
    configuration: { nodeCount: 5 },
    network: { edges: [[0, 1], [1, 2], [2, 3], [3, 4], [4, 0]] },
    sourceNode: 0,
    rounds: [],
  } as unknown as ReplayFile
}

// Visual round 0

describe('replay: visual round 0', () => {
  it('shows only the source node as informed', () => {
    const state = computeReplayState(file, 0)
    expect(state.informedCount).toBe(1)
    expect(state.informedNodes.has(0)).toBe(true)
  })

  it('has zero messages', () => {
    const state = computeReplayState(file, 0)
    expect(state.messageCount).toBe(0)
    expect(state.messages).toHaveLength(0)
  })

  it('has no newly informed nodes', () => {
    const state = computeReplayState(file, 0)
    expect(state.newlyInformedNodes.size).toBe(0)
  })

  it('reports current round as 0', () => {
    const state = computeReplayState(file, 0)
    expect(state.currentRound).toBe(0)
  })

  it('reports correct total rounds', () => {
    const state = computeReplayState(file, 0)
    expect(state.totalRounds).toBe(3)
  })

  it('source node has state "source"', () => {
    const state = computeReplayState(file, 0)
    expect(getNodeState(0, state)).toBe('source')
  })

  it('non-source nodes are inactive', () => {
    const state = computeReplayState(file, 0)
    for (let i = 1; i < 5; i++) {
      expect(getNodeState(i, state)).toBe('inactive')
    }
  })
})

// Round stepping correctness

describe('replay: round stepping', () => {
  it('round 1 informs node 1 via message from 0', () => {
    const state = computeReplayState(file, 1)
    expect(state.currentRound).toBe(1)
    expect(state.informedCount).toBe(2)
    expect(state.informedNodes.has(1)).toBe(true)
    expect(state.newlyInformedNodes.has(1)).toBe(true)
    expect(state.messageCount).toBe(1)
    expect(state.messages[0].sender).toBe(0)
    expect(state.messages[0].receiver).toBe(1)
    expect(state.messages[0].newInfection).toBe(true)
  })

  it('round 2 informs nodes 2 and 4', () => {
    const state = computeReplayState(file, 2)
    expect(state.currentRound).toBe(2)
    expect(state.informedCount).toBe(4)
    expect(state.newlyInformedNodes.has(2)).toBe(true)
    expect(state.newlyInformedNodes.has(4)).toBe(true)
    expect(state.messageCount).toBe(2)
  })

  it('round 3 informs node 3 (all nodes now informed)', () => {
    const state = computeReplayState(file, 3)
    expect(state.currentRound).toBe(3)
    expect(state.informedCount).toBe(5)
    expect(state.newlyInformedNodes.has(3)).toBe(true)
    const redundantMsg = state.messages.find(
      m => m.sender === 4 && m.receiver === 3
    )
    expect(redundantMsg).toBeDefined()
    expect(redundantMsg!.newInfection).toBe(false)
  })

  it('source node always has state "source" regardless of round', () => {
    for (let r = 0; r <= 3; r++) {
      const state = computeReplayState(file, r)
      expect(getNodeState(0, state)).toBe('source')
    }
  })

  it('informed nodes accumulate across rounds', () => {
    const r1 = computeReplayState(file, 1)
    const r2 = computeReplayState(file, 2)
    const r3 = computeReplayState(file, 3)

    expect(r1.informedCount).toBe(2)
    expect(r2.informedCount).toBe(4)
    expect(r3.informedCount).toBe(5)
  })

  it('previously informed nodes have state "informed" (not "newly-informed")', () => {
    const state = computeReplayState(file, 2)
    expect(getNodeState(1, state)).toBe('informed')
    expect(getNodeState(2, state)).toBe('newly-informed')
  })
})

// Backward navigation correctness

describe('replay: backward navigation', () => {
  it('stepping backward reconstructs earlier state correctly', () => {
    const forward = computeReplayState(file, 3)
    const backward = computeReplayState(file, 1)

    expect(forward.informedCount).toBe(5)
    expect(backward.informedCount).toBe(2)
    expect(backward.currentRound).toBe(1)
    expect(backward.newlyInformedNodes.has(1)).toBe(true)
    expect(backward.informedNodes.has(2)).toBe(false)
  })

  it('stepping back to round 0 shows only source', () => {
    const state = computeReplayState(file, 0)
    expect(state.informedCount).toBe(1)
    expect(state.messageCount).toBe(0)
    expect(state.newlyInformedNodes.size).toBe(0)
  })

  it('reconstruction is deterministic (same result each time)', () => {
    const a = computeReplayState(file, 2)
    const b = computeReplayState(file, 2)

    expect(a.currentRound).toBe(b.currentRound)
    expect(a.informedCount).toBe(b.informedCount)
    expect(a.messageCount).toBe(b.messageCount)
    expect([...a.informedNodes]).toEqual([...b.informedNodes])
    expect([...a.newlyInformedNodes]).toEqual([...b.newlyInformedNodes])
  })
})

// Replay completion

describe('replay: completion', () => {
  it('requesting round beyond total clamps to final', () => {
    const state = computeReplayState(file, 99)
    expect(state.currentRound).toBe(3)
    expect(state.informedCount).toBe(5)
  })

  it('final state preserves all informed nodes', () => {
    const state = computeReplayState(file, 3)
    for (let i = 0; i < 5; i++) {
      expect(state.informedNodes.has(i)).toBe(true)
    }
  })
})

// Empty replay

describe('replay: empty rounds', () => {
  it('stays at visual round 0 with no recorded rounds', () => {
    const emptyFile = emptyRoundsFile()
    const state = computeReplayState(emptyFile, 0)

    expect(state.currentRound).toBe(0)
    expect(state.totalRounds).toBe(0)
    expect(state.informedCount).toBe(1)
    expect(state.messageCount).toBe(0)
  })

  it('clamping to round 1+ still returns round 0', () => {
    const emptyFile = emptyRoundsFile()
    const state = computeReplayState(emptyFile, 5)

    expect(state.currentRound).toBe(0)
    expect(state.informedCount).toBe(1)
  })
})

// Playback timing

describe('replay: playback timing', () => {
  it('default speed is 1x', () => {
    expect(DEFAULT_SPEED).toBe(1)
  })

  it('round delay at 1x is 1000ms', () => {
    expect(roundDelayMs(1)).toBe(1000)
  })

  it('round delay at 2x is 500ms', () => {
    expect(roundDelayMs(2)).toBe(500)
  })

  it('round delay at 0.25x is 4000ms', () => {
    expect(roundDelayMs(0.25)).toBe(4000)
  })

  it('pulse duration at 1x is 600ms', () => {
    expect(pulseDurationMs(1)).toBe(600)
  })

  it('pulse duration scales inversely with speed', () => {
    expect(pulseDurationMs(2)).toBe(300)
    expect(pulseDurationMs(0.5)).toBe(1200)
  })
})

// Utility functions

describe('replay: utilities', () => {
  it('negative round clamped to 0', () => {
    const state = computeReplayState(file, -5)
    expect(state.currentRound).toBe(0)
  })
})

// Byzantine / failed nodes

describe('replay: Byzantine failed nodes', () => {
  it('populates failedNodes set from file', () => {
    const state = computeReplayState(byzFile, 0)
    expect(state.failedNodes.size).toBe(1)
    expect(state.failedNodes.has(2)).toBe(true)
  })

  it('failed node has state "failed"', () => {
    const state = computeReplayState(byzFile, 0)
    expect(getNodeState(2, state)).toBe('failed')
  })

  it('non-failed nodes retain normal states', () => {
    const state = computeReplayState(byzFile, 1)
    expect(getNodeState(0, state)).toBe('source')
    expect(getNodeState(1, state)).toBe('newly-informed')
    expect(getNodeState(3, state)).toBe('inactive')
  })

  it('source node is still "source" even if file has failedNodes', () => {
    const state = computeReplayState(byzFile, 0)
    expect(getNodeState(0, state)).toBe('source')
  })

  it('failedNodes set is empty for non-Byzantine files', () => {
    const state = computeReplayState(file, 0)
    expect(state.failedNodes.size).toBe(0)
  })

  it('failed node stays "failed" across all rounds', () => {
    for (let r = 0; r <= 3; r++) {
      const state = computeReplayState(byzFile, r)
      expect(getNodeState(2, state)).toBe('failed')
    }
  })
})
