import type { ReplayFile } from '../model'

export type NodeState = 'inactive' | 'source' | 'newly-informed' | 'informed' | 'failed'

export interface MessageState {
  sender: number
  receiver: number
  newInfection: boolean
}

export interface ReplayState {
  /** Current visual round (0 = initial, 1..T = recorded). */
  currentRound: number
  /** Number of recorded rounds. */
  totalRounds: number
  informedNodes: ReadonlySet<number>
  newlyInformedNodes: ReadonlySet<number>
  failedNodes: ReadonlySet<number>
  sourceNode: number
  messages: readonly MessageState[]
  informedCount: number
  messageCount: number
}

export type PlaybackSpeed = 0.25 | 0.5 | 1 | 2 | 4

export const DEFAULT_SPEED: PlaybackSpeed = 1

/** Reconstruct the replay state at the given visual round. */
export function computeReplayState(file: ReplayFile, targetRound: number): ReplayState {
  const totalRounds = file.rounds.length
  const safeTarget = Number.isFinite(targetRound) ? Math.floor(targetRound) : 0
  const round = Math.max(0, Math.min(safeTarget, totalRounds))

  const failedNodes: ReadonlySet<number> = file.failedNodes
    ? new Set(file.failedNodes)
    : new Set()

  const informedNodes = new Set<number>()
  informedNodes.add(file.sourceNode)

  let newlyInformedNodes = new Set<number>()
  let messages: MessageState[] = []

  for (let i = 0; i < round; i++) {
    const recordedRound = file.rounds[i]
    newlyInformedNodes = new Set(recordedRound.newlyInformed)

    for (const nodeId of recordedRound.newlyInformed) {
      informedNodes.add(nodeId)
    }

    if (i === round - 1) {
      messages = recordedRound.messages.map(msg => ({
        sender: msg.sender,
        receiver: msg.receiver,
        newInfection: msg.newInfection,
      }))
    }
  }

  if (round === 0) {
    newlyInformedNodes = new Set()
    messages = []
  }

  return {
    currentRound: round,
    totalRounds,
    informedNodes,
    newlyInformedNodes,
    failedNodes,
    sourceNode: file.sourceNode,
    messages,
    informedCount: informedNodes.size,
    messageCount: messages.length,
  }
}

export function getNodeState(nodeId: number, state: ReplayState): NodeState {
  if (nodeId === state.sourceNode) return 'source'
  if (state.failedNodes.has(nodeId)) return 'failed'
  if (state.newlyInformedNodes.has(nodeId)) return 'newly-informed'
  if (state.informedNodes.has(nodeId)) return 'informed'
  return 'inactive'
}

/** 1000ms at 1x, scales inversely with speed. */
export function roundDelayMs(speed: PlaybackSpeed): number {
  return 1000 / speed
}

/** 600ms at 1x, scales inversely with speed. */
export function pulseDurationMs(speed: PlaybackSpeed): number {
  return 600 / speed
}
