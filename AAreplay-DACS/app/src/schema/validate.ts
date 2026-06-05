import type { ReplayFile } from './schema'

export type ValidationResult =
  | { valid: true; file: ReplayFile; normalizedEdges: Set<string> }
  | { valid: false; error: string; category: 'structural' | 'semantic' }

function edgeKey(a: number, b: number): string {
  return a < b ? `${a}:${b}` : `${b}:${a}`
}

/** Run semantic checks on a structurally valid replay file. */
export function validateSemantics(file: ReplayFile): ValidationResult {
  const { configuration, network, sourceNode, rounds } = file
  const nodeCount = configuration.nodeCount

  if (sourceNode < 0 || sourceNode >= nodeCount) {
    return fail('semantic', `sourceNode ${sourceNode} is outside valid range [0, ${nodeCount - 1}]`)
  }

  // Byzantine / failed nodes
  const failedSet = new Set<number>()
  if (file.failedNodes) {
    for (let i = 0; i < file.failedNodes.length; i++) {
      const nid = file.failedNodes[i]
      if (nid < 0 || nid >= nodeCount) {
        return fail('semantic', `failedNodes[${i}]: node ${nid} is outside valid range [0, ${nodeCount - 1}]`)
      }
      if (failedSet.has(nid)) {
        return fail('semantic', `failedNodes[${i}]: duplicate node ${nid}`)
      }
      failedSet.add(nid)
    }

    if (failedSet.has(sourceNode) && !file.sourceForcedActive) {
      return fail('semantic',
        `sourceNode ${sourceNode} is in failedNodes but sourceForcedActive is not true`)
    }
  }

  // Edges
  const edgeSet = new Set<string>()

  for (let i = 0; i < network.edges.length; i++) {
    const [a, b] = network.edges[i]

    if (a < 0 || a >= nodeCount) {
      return fail('semantic', `Edge ${i}: endpoint ${a} is outside valid range [0, ${nodeCount - 1}]`)
    }
    if (b < 0 || b >= nodeCount) {
      return fail('semantic', `Edge ${i}: endpoint ${b} is outside valid range [0, ${nodeCount - 1}]`)
    }
    if (a === b) {
      return fail('semantic', `Edge ${i}: self-loop [${a}, ${b}] is not allowed`)
    }

    const key = edgeKey(a, b)
    if (edgeSet.has(key)) {
      return fail('semantic', `Edge ${i}: duplicate edge [${a}, ${b}]`)
    }
    edgeSet.add(key)
  }

  // Rounds
  const globallyInformed = new Set<number>()
  globallyInformed.add(sourceNode)

  let previousRound = 0

  for (let i = 0; i < rounds.length; i++) {
    const round = rounds[i]

    if (round.round !== previousRound + 1) {
      return fail('semantic',
        `Round at index ${i}: round number ${round.round} must be consecutive starting at 1 (expected ${previousRound + 1})`)
    }
    previousRound = round.round

    if (round.messageCount !== round.messages.length) {
      return fail('semantic',
        `Round ${round.round}: messageCount (${round.messageCount}) does not match messages array length (${round.messages.length})`)
    }

    // Snapshot of informed nodes before this round.
    const priorInformed = new Set(globallyInformed)

    for (let j = 0; j < round.messages.length; j++) {
      const msg = round.messages[j]

      if (msg.sender < 0 || msg.sender >= nodeCount) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: sender ${msg.sender} is outside valid range [0, ${nodeCount - 1}]`)
      }
      if (msg.receiver < 0 || msg.receiver >= nodeCount) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: receiver ${msg.receiver} is outside valid range [0, ${nodeCount - 1}]`)
      }
      if (msg.sender === msg.receiver) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: sender and receiver are the same node (${msg.sender})`)
      }

      const msgEdgeKey = edgeKey(msg.sender, msg.receiver)
      if (!edgeSet.has(msgEdgeKey)) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: no edge exists between ${msg.sender} and ${msg.receiver}`)
      }

      if (!priorInformed.has(msg.sender)) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: sender ${msg.sender} was not informed before this round`)
      }

      const senderIsFailed = failedSet.has(msg.sender)
      const senderForcedActive = msg.sender === sourceNode && file.sourceForcedActive
      if (senderIsFailed && !senderForcedActive) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: sender ${msg.sender} is a failed node and must not send messages`)
      }

      if (msg.newInfection && priorInformed.has(msg.receiver)) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: receiver ${msg.receiver} was already informed before this round but newInfection is true`)
      }
    }

    const roundNewlyInformed = new Set<number>()

    for (const nodeId of round.newlyInformed) {
      if (nodeId < 0 || nodeId >= nodeCount) {
        return fail('semantic',
          `Round ${round.round}: newlyInformed node ${nodeId} is outside valid range [0, ${nodeCount - 1}]`)
      }

      if (roundNewlyInformed.has(nodeId)) {
        return fail('semantic',
          `Round ${round.round}: node ${nodeId} appears more than once in newlyInformed`)
      }
      roundNewlyInformed.add(nodeId)

      if (nodeId === sourceNode) {
        return fail('semantic',
          `Round ${round.round}: source node ${sourceNode} must not appear in newlyInformed`)
      }

      if (globallyInformed.has(nodeId)) {
        return fail('semantic',
          `Round ${round.round}: node ${nodeId} was already informed in a previous round`)
      }

      const hasInfectingMessage = round.messages.some(
        msg => msg.receiver === nodeId && msg.newInfection
      )
      if (!hasInfectingMessage) {
        return fail('semantic',
          `Round ${round.round}: node ${nodeId} is in newlyInformed but has no message with newInfection = true targeting it`)
      }
    }

    // Every newInfection=true message targets a newlyInformed receiver.
    for (let j = 0; j < round.messages.length; j++) {
      const msg = round.messages[j]
      if (msg.newInfection && !roundNewlyInformed.has(msg.receiver)) {
        return fail('semantic',
          `Round ${round.round}, message ${j}: newInfection is true but receiver ${msg.receiver} is not in newlyInformed`)
      }
    }

    for (const nodeId of roundNewlyInformed) {
      globallyInformed.add(nodeId)
    }

    if (round.totalInformed !== globallyInformed.size) {
      return fail('semantic',
        `Round ${round.round}: totalInformed (${round.totalInformed}) does not match expected count (${globallyInformed.size})`)
    }
  }

  // Cross-check result summary against the last round.
  if (file.result && rounds.length > 0) {
    const finalRound = rounds[rounds.length - 1]
    if (file.result.T_end !== undefined && file.result.T_end !== finalRound.round) {
      return fail('semantic',
        `Result T_end (${file.result.T_end}) does not match final round number (${finalRound.round})`)
    }
  }

  return { valid: true, file, normalizedEdges: edgeSet }
}

function fail(category: 'structural' | 'semantic', error: string): ValidationResult {
  return { valid: false, error, category }
}
