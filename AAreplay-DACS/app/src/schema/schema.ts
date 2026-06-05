import { z } from 'zod'

// Leaf schemas

export const messageSchema = z.strictObject({
  sender: z.number().int().nonnegative(),
  receiver: z.number().int().nonnegative(),
  newInfection: z.boolean(),
})

export const roundSchema = z.strictObject({
  round: z.number().int().positive(),
  messages: z.array(messageSchema),
  newlyInformed: z.array(z.number().int().nonnegative()),
  totalInformed: z.number().int().positive(),
  messageCount: z.number().int().nonnegative(),
})

const edgePairSchema = z.tuple([
  z.number().int().nonnegative(),
  z.number().int().nonnegative(),
])

export const networkSchema = z.strictObject({
  edges: z.array(edgePairSchema),
})

// configuration and result are looseObject: unknown fields are accepted.
export const configurationSchema = z.looseObject({
  nodeCount: z.number().int().positive(),
  topologyType: z.string().optional(),
  viewFraction: z.number().optional(),
  fanOut: z.number().int().optional(),
  graphSeed: z.number().int().optional(),
  simulationSeed: z.number().int().optional(),
  k: z.number().int().optional(),
  m: z.number().int().optional(),
  p: z.number().optional(),
  beta: z.number().optional(),
  maxRounds: z.number().int().optional(),
  stableRounds: z.number().int().optional(),
  failureProbability: z.number().min(0).max(1).optional(),
})

export const resultSchema = z.looseObject({
  T_end: z.number().optional(),
  Omega: z.number().optional(),
  M: z.number().optional(),
  alpha: z.number().optional(),
  F_eff: z.number().optional(),
  R_run: z.number().optional(),
}).optional()

// Top-level replay file.
// formatVersion is optional; when present it must equal "1.0".
export const replayFileSchema = z.strictObject({
  formatVersion: z.literal('1.0').optional(),
  configuration: configurationSchema,
  network: networkSchema,
  sourceNode: z.number().int().nonnegative(),
  failureProbability: z.number().min(0).max(1).optional(),
  sourceForcedActive: z.boolean().optional(),
  failedNodes: z.array(z.number().int().nonnegative()).optional(),
  rounds: z.array(roundSchema),
  result: resultSchema,
})

// Inferred types

export type Message = z.infer<typeof messageSchema>
export type Round = z.infer<typeof roundSchema>
export type Network = z.infer<typeof networkSchema>
export type Configuration = z.infer<typeof configurationSchema>
export type Result = z.infer<typeof resultSchema>
export type ReplayFile = z.infer<typeof replayFileSchema>
