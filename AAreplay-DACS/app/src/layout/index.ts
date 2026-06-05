import {
  forceSimulation,
  forceLink,
  forceManyBody,
  forceCenter,
  forceCollide,
  type SimulationNodeDatum,
  type SimulationLinkDatum,
} from 'd3-force'
import type { ReplayFile } from '../model'

export interface NodePosition {
  id: number
  x: number
  y: number
}

export type LayoutResult = NodePosition[]

export type LayoutType = 'circular' | 'force-directed'

/** Canonicalize topology strings (drop case and non-alphanumerics). */
function normalizeTopology(topologyType?: string): string {
  if (!topologyType) return ''
  return topologyType.toLowerCase().replace(/[^a-z0-9]/g, '')
}

/** Pick the default layout for a topology. */
export function defaultLayoutType(topologyType?: string): LayoutType {
  const key = normalizeTopology(topologyType)
  switch (key) {
    case 'ring':
    case 'smallworld':
      return 'circular'
    default:
      return 'force-directed'
  }
}

/** Compute stable 2D positions for a validated replay file. */
export function computeLayout(
  file: ReplayFile,
  size: number = 600,
  layoutOverride?: LayoutType,
): LayoutResult {
  const nodeCount = file.configuration.nodeCount
  const topologyType = file.configuration.topologyType
  const layout = layoutOverride ?? defaultLayoutType(topologyType)

  if (nodeCount === 0) return []
  if (nodeCount === 1) return [{ id: 0, x: 0, y: 0 }]

  switch (layout) {
    case 'circular':
      return circularLayout(nodeCount, size)
    case 'force-directed':
      return forceDirectedLayout(nodeCount, file.network.edges, size)
  }
}

/** Place nodes evenly on a circle, starting from the top, clockwise. */
function circularLayout(nodeCount: number, size: number): LayoutResult {
  const radius = size * 0.4
  const positions: LayoutResult = []

  for (let i = 0; i < nodeCount; i++) {
    const angle = (2 * Math.PI * i) / nodeCount - Math.PI / 2
    positions.push({
      id: i,
      x: Math.cos(angle) * radius,
      y: Math.sin(angle) * radius,
    })
  }

  return positions
}

/** Deterministic force-directed layout, rescaled to fit the square. */
function forceDirectedLayout(
  nodeCount: number,
  edges: readonly (readonly [number, number])[],
  size: number,
): LayoutResult {
  const nodes: SimulationNodeDatum[] = Array.from(
    { length: nodeCount },
    (_, i) => ({ index: i }),
  )

  const links: SimulationLinkDatum<SimulationNodeDatum>[] = edges.map(
    ([source, target]) => ({ source: nodes[source], target: nodes[target] }),
  )

  const simulation = forceSimulation(nodes)
    .force('link', forceLink(links).distance(30).strength(1))
    .force('charge', forceManyBody().strength(-30))
    .force('center', forceCenter(0, 0))
    .force('collide', forceCollide(6))
    .stop()

  const iterations = Math.min(
    1200,
    Math.max(300, Math.ceil(60 * Math.sqrt(nodeCount))),
  )
  for (let i = 0; i < iterations; i++) {
    simulation.tick()
  }

  // Rescale the result to fill ~90% of the target square.
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const node of nodes) {
    const x = node.x ?? 0
    const y = node.y ?? 0
    if (x < minX) minX = x
    if (y < minY) minY = y
    if (x > maxX) maxX = x
    if (y > maxY) maxY = y
  }
  const simWidth = Math.max(maxX - minX, 1e-6)
  const simHeight = Math.max(maxY - minY, 1e-6)
  const simCx = (minX + maxX) / 2
  const simCy = (minY + maxY) / 2
  const targetHalf = size * 0.45
  const scale = Math.min(
    (2 * targetHalf) / simWidth,
    (2 * targetHalf) / simHeight,
  )

  return nodes.map((node, i) => ({
    id: i,
    x: ((node.x ?? 0) - simCx) * scale,
    y: ((node.y ?? 0) - simCy) * scale,
  }))
}
