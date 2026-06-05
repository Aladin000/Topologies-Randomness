import { useState, useEffect, useMemo, useCallback, useRef } from 'react'
import type { ReplayState } from '../replay'
import type { LayoutResult } from '../layout'

const MIN_ZOOM = 0.25
const MAX_ZOOM = 40
const ZOOM_STEP = 1.2
// Pointer travel under this many screen pixels counts as a click, not a pan.
const CLICK_PIXEL_THRESHOLD = 4

type EdgePair = readonly [number, number]

// Preload graphs (shown while idle). All capped at < 50 nodes.

interface PreloadGraph {
  nodes: { id: number; x: number; y: number }[]
  edges: [number, number][]
}

// Matches the main layout (size=600, radius=0.4*size=240) so stroke widths
// computed from `squareSide` land at the same number of screen pixels under
// vectorEffect="non-scaling-stroke".
const CX = 300
const CY = 300
const R = 240
const MAX_N = 45

function randInt(lo: number, hi: number): number {
  return lo + Math.floor(Math.random() * (hi - lo + 1))
}

function circularNodes(n: number): PreloadGraph['nodes'] {
  return Array.from({ length: n }, (_, i) => {
    const angle = (2 * Math.PI * i) / n - Math.PI / 2
    return { id: i, x: CX + Math.cos(angle) * R, y: CY + Math.sin(angle) * R }
  })
}

function generateRing(): PreloadGraph {
  const n = randInt(10, MAX_N)
  const nodes = circularNodes(n)
  const edges: [number, number][] = []
  for (let i = 0; i < n; i++) edges.push([i, (i + 1) % n])
  return { nodes, edges }
}

// Watts–Strogatz-style: k-regular ring with a few rewired chords.
function generateSmallWorld(): PreloadGraph {
  const n = randInt(16, MAX_N)
  const k = 2
  const nodes = circularNodes(n)
  const edgeSet = new Set<string>()
  const edges: [number, number][] = []
  const addEdge = (a: number, b: number) => {
    const lo = Math.min(a, b), hi = Math.max(a, b)
    const key = `${lo}-${hi}`
    if (edgeSet.has(key) || lo === hi) return
    edgeSet.add(key)
    edges.push([lo, hi])
  }
  for (let i = 0; i < n; i++) {
    for (let j = 1; j <= k; j++) addEdge(i, (i + j) % n)
  }
  const rewires = Math.max(1, Math.floor(edges.length * 0.15))
  for (let r = 0; r < rewires; r++) {
    const src = randInt(0, n - 1)
    const dst = randInt(0, n - 1)
    addEdge(src, dst)
  }
  return { nodes, edges }
}

function generateStar(): PreloadGraph {
  const n = randInt(10, MAX_N)
  const leaves = n - 1
  const nodes: PreloadGraph['nodes'] = [{ id: 0, x: CX, y: CY }]
  for (let i = 0; i < leaves; i++) {
    const angle = (2 * Math.PI * i) / leaves - Math.PI / 2
    nodes.push({ id: i + 1, x: CX + Math.cos(angle) * R, y: CY + Math.sin(angle) * R })
  }
  const edges: [number, number][] = []
  for (let i = 1; i <= leaves; i++) edges.push([0, i])
  return { nodes, edges }
}

function generateWheel(): PreloadGraph {
  const n = randInt(10, MAX_N)
  const rim = n - 1
  const nodes: PreloadGraph['nodes'] = [{ id: 0, x: CX, y: CY }]
  for (let i = 0; i < rim; i++) {
    const angle = (2 * Math.PI * i) / rim - Math.PI / 2
    nodes.push({ id: i + 1, x: CX + Math.cos(angle) * R, y: CY + Math.sin(angle) * R })
  }
  const edges: [number, number][] = []
  for (let i = 1; i <= rim; i++) edges.push([0, i])
  for (let i = 1; i <= rim; i++) edges.push([i, i < rim ? i + 1 : 1])
  return { nodes, edges }
}

function generateComplete(): PreloadGraph {
  const n = randInt(5, 10)
  const nodes = circularNodes(n)
  const edges: [number, number][] = []
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) edges.push([i, j])
  }
  return { nodes, edges }
}

// Barabási–Albert-style preferential attachment, seeded from K5.
function generateScaleFree(): PreloadGraph {
  const n = randInt(14, 32)
  const m = 2
  const ids: number[] = [0, 1, 2, 3, 4]
  const edges: [number, number][] = []
  const edgeSet = new Set<string>()
  const addEdge = (a: number, b: number) => {
    const lo = Math.min(a, b), hi = Math.max(a, b)
    const key = `${lo}-${hi}`
    if (edgeSet.has(key) || lo === hi) return
    edgeSet.add(key)
    edges.push([lo, hi])
  }
  for (let i = 0; i < 5; i++) for (let j = i + 1; j < 5; j++) addEdge(i, j)

  const deg = new Map<number, number>()
  for (const [a, b] of edges) {
    deg.set(a, (deg.get(a) ?? 0) + 1)
    deg.set(b, (deg.get(b) ?? 0) + 1)
  }
  for (let v = ids.length; v < n; v++) {
    const picked = new Set<number>()
    while (picked.size < m) {
      let total = 0
      for (const u of ids) total += deg.get(u) ?? 0
      let pick = Math.random() * total
      let chosen = ids[ids.length - 1]
      for (const u of ids) {
        pick -= deg.get(u) ?? 0
        if (pick <= 0) { chosen = u; break }
      }
      picked.add(chosen)
    }
    for (const u of picked) {
      addEdge(v, u)
      deg.set(v, (deg.get(v) ?? 0) + 1)
      deg.set(u, (deg.get(u) ?? 0) + 1)
    }
    ids.push(v)
  }
  const nodes = circularNodes(n)
  return { nodes, edges }
}

function generatePath(): PreloadGraph {
  const n = randInt(6, 20)
  const step = (R * 2) / (n - 1)
  const startX = CX - R
  const wobble = R * 0.09
  const nodes = Array.from({ length: n }, (_, i) => ({
    id: i,
    x: startX + i * step,
    y: CY + (i % 2 === 0 ? -wobble : wobble),
  }))
  const edges: [number, number][] = []
  for (let i = 0; i < n - 1; i++) edges.push([i, i + 1])
  return { nodes, edges }
}

function generateBipartite(): PreloadGraph {
  const topCount = randInt(4, 10)
  const botCount = randInt(4, 10)
  const nodes: PreloadGraph['nodes'] = []
  const rowOffset = R * 0.48
  const topStep = (R * 2) / (topCount + 1)
  for (let i = 0; i < topCount; i++) {
    nodes.push({ id: i, x: CX - R + topStep * (i + 1), y: CY - rowOffset })
  }
  const botStep = (R * 2) / (botCount + 1)
  for (let i = 0; i < botCount; i++) {
    nodes.push({ id: topCount + i, x: CX - R + botStep * (i + 1), y: CY + rowOffset })
  }
  const edges: [number, number][] = []
  const edgeSet = new Set<string>()
  for (let i = 0; i < topCount; i++) {
    const degree = 1 + Math.floor(Math.random() * Math.min(3, botCount))
    const targets = new Set<number>()
    while (targets.size < degree) {
      targets.add(topCount + Math.floor(Math.random() * botCount))
    }
    for (const j of targets) {
      const key = `${i}-${j}`
      if (!edgeSet.has(key)) {
        edges.push([i, j])
        edgeSet.add(key)
      }
    }
  }
  return { nodes, edges }
}

function generateRandom(): PreloadGraph {
  const n = randInt(10, MAX_N)
  const nodes = circularNodes(n)
  const edges: [number, number][] = []
  const edgeSet = new Set<string>()
  for (let i = 0; i < n - 1; i++) {
    edges.push([i, i + 1])
    edgeSet.add(`${i}-${i + 1}`)
  }
  const extras = Math.floor(Math.random() * n * 0.6)
  for (let k = 0; k < extras; k++) {
    const a = Math.floor(Math.random() * n)
    const b = Math.floor(Math.random() * n)
    if (a !== b) {
      const lo = Math.min(a, b), hi = Math.max(a, b)
      const key = `${lo}-${hi}`
      if (!edgeSet.has(key)) {
        edges.push([lo, hi])
        edgeSet.add(key)
      }
    }
  }
  return { nodes, edges }
}

function generateGrid(): PreloadGraph {
  const grids = [[3, 3], [3, 4], [4, 4], [4, 5], [5, 5], [5, 6], [6, 6]]
  const [rows, cols] = grids[Math.floor(Math.random() * grids.length)]
  const nodes: PreloadGraph['nodes'] = []
  const cellW = (R * 2) / (cols - 1)
  const cellH = (R * 2) / (rows - 1)
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      nodes.push({
        id: r * cols + c,
        x: CX - R + c * cellW,
        y: CY - R + r * cellH,
      })
    }
  }
  const edges: [number, number][] = []
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const id = r * cols + c
      if (c < cols - 1) edges.push([id, id + 1])
      if (r < rows - 1) edges.push([id, id + cols])
    }
  }
  return { nodes, edges }
}

// Petersen-like: outer ring connected to inner star via spokes.
function generatePetersen(): PreloadGraph {
  const k = randInt(4, 8)
  const outerNodes = circularNodes(k)
  const innerR = R * 0.45
  const innerNodes = Array.from({ length: k }, (_, i) => {
    const angle = (2 * Math.PI * i) / k - Math.PI / 2
    return { id: k + i, x: CX + Math.cos(angle) * innerR, y: CY + Math.sin(angle) * innerR }
  })
  const nodes = [...outerNodes, ...innerNodes]
  const edges: [number, number][] = []
  for (let i = 0; i < k; i++) edges.push([i, (i + 1) % k])
  for (let i = 0; i < k; i++) edges.push([k + i, k + (i + 2) % k])
  for (let i = 0; i < k; i++) edges.push([i, k + i])
  return { nodes, edges }
}

// Complete binary tree laid out in concentric rings.
function generateBinaryTree(): PreloadGraph {
  const depth = randInt(3, 5)
  const n = (1 << depth) - 1
  const nodes: PreloadGraph['nodes'] = []
  const edges: [number, number][] = []
  for (let i = 0; i < n; i++) {
    const level = Math.floor(Math.log2(i + 1))
    const posInLevel = i - ((1 << level) - 1)
    const totalInLevel = 1 << level
    const levelR = (R * level) / (depth - 1)
    const angle = (2 * Math.PI * posInLevel) / totalInLevel - Math.PI / 2
    nodes.push({
      id: i,
      x: level === 0 ? CX : CX + Math.cos(angle) * levelR,
      y: level === 0 ? CY : CY + Math.sin(angle) * levelR,
    })
    if (i > 0) edges.push([Math.floor((i - 1) / 2), i])
  }
  return { nodes, edges }
}

// Hexagonal / triangular lattice.
function generateHexLattice(): PreloadGraph {
  const sizes = [[3, 4], [4, 4], [4, 5], [3, 5]]
  const [rows, cols] = sizes[Math.floor(Math.random() * sizes.length)]
  const nodes: PreloadGraph['nodes'] = []
  const edges: [number, number][] = []
  const cellW = (R * 2) / (cols - 1)
  const cellH = (R * 1.6) / (rows - 1)
  for (let r = 0; r < rows; r++) {
    const offset = (r % 2) * cellW * 0.5
    for (let c = 0; c < cols; c++) {
      nodes.push({
        id: r * cols + c,
        x: CX - R + offset + c * cellW,
        y: CY - R * 0.8 + r * cellH,
      })
    }
  }
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const id = r * cols + c
      if (c < cols - 1) edges.push([id, id + 1])
      if (r < rows - 1) {
        edges.push([id, id + cols])
        if (r % 2 === 0 && c > 0) edges.push([id, id + cols - 1])
        if (r % 2 === 1 && c < cols - 1) edges.push([id, id + cols + 1])
      }
    }
  }
  return { nodes, edges }
}

// Lollipop: a complete graph attached to a path tail.
function generateLollipop(): PreloadGraph {
  const clique = randInt(4, 8)
  const tail = randInt(4, 10)
  const cliqueNodes = circularNodes(clique)
  const tailStep = R * 0.7 / tail
  const startX = CX + R * 0.55
  const nodes: PreloadGraph['nodes'] = [...cliqueNodes]
  for (let i = 0; i < tail; i++) {
    nodes.push({ id: clique + i, x: startX + i * tailStep, y: CY })
  }
  const edges: [number, number][] = []
  for (let i = 0; i < clique; i++)
    for (let j = i + 1; j < clique; j++) edges.push([i, j])
  edges.push([0, clique])
  for (let i = 0; i < tail - 1; i++) edges.push([clique + i, clique + i + 1])
  return { nodes, edges }
}

// Barbell: two cliques connected by a path bridge.
function generateBarbell(): PreloadGraph {
  const cSize = randInt(4, 7)
  const bridgeLen = randInt(2, 5)
  const leftCenter = { x: CX - R * 0.55, y: CY }
  const rightCenter = { x: CX + R * 0.55, y: CY }
  const leftR = R * 0.3
  const nodes: PreloadGraph['nodes'] = []
  const edges: [number, number][] = []
  for (let i = 0; i < cSize; i++) {
    const angle = (2 * Math.PI * i) / cSize - Math.PI / 2
    nodes.push({ id: i, x: leftCenter.x + Math.cos(angle) * leftR, y: leftCenter.y + Math.sin(angle) * leftR })
  }
  const bridgeStart = cSize
  const bridgeStep = (rightCenter.x - leftCenter.x - leftR * 2) / (bridgeLen + 1)
  for (let i = 0; i < bridgeLen; i++) {
    nodes.push({ id: bridgeStart + i, x: leftCenter.x + leftR + (i + 1) * bridgeStep, y: CY })
  }
  const rightStart = cSize + bridgeLen
  for (let i = 0; i < cSize; i++) {
    const angle = (2 * Math.PI * i) / cSize - Math.PI / 2
    nodes.push({ id: rightStart + i, x: rightCenter.x + Math.cos(angle) * leftR, y: rightCenter.y + Math.sin(angle) * leftR })
  }
  for (let i = 0; i < cSize; i++)
    for (let j = i + 1; j < cSize; j++) edges.push([i, j])
  for (let i = 0; i < cSize; i++)
    for (let j = i + 1; j < cSize; j++) edges.push([rightStart + i, rightStart + j])
  edges.push([0, bridgeStart])
  for (let i = 0; i < bridgeLen - 1; i++) edges.push([bridgeStart + i, bridgeStart + i + 1])
  edges.push([bridgeLen > 0 ? bridgeStart + bridgeLen - 1 : 0, rightStart])
  return { nodes, edges }
}

const generators = [
  generateRing, generateSmallWorld, generateStar, generateWheel,
  generateComplete, generateScaleFree, generatePath, generateBipartite,
  generateRandom, generateGrid, generatePetersen, generateBinaryTree,
  generateHexLattice, generateLollipop, generateBarbell,
]

function generatePreloadGraph(): PreloadGraph {
  const gen = generators[Math.floor(Math.random() * generators.length)]
  const g = gen()
  if (g.nodes.length === 0 || g.nodes.length >= 50) return generateRing()
  return g
}

/** Build the `d` attribute for all preload edges, using arcs on circular layouts. */
function buildPreloadEdgesD(g: PreloadGraph): string {
  let sx = 0, sy = 0
  for (const n of g.nodes) { sx += n.x; sy += n.y }
  const cx = sx / g.nodes.length
  const cy = sy / g.nodes.length
  let rMin = Infinity, rMax = -Infinity, rSum = 0
  for (const n of g.nodes) {
    const d = Math.hypot(n.x - cx, n.y - cy)
    rSum += d
    if (d < rMin) rMin = d
    if (d > rMax) rMax = d
  }
  const rMean = rSum / g.nodes.length
  const circular = rMean > 1e-6 && (rMax - rMin) / rMean < 0.05

  const parts: string[] = []
  for (const [a, b] of g.edges) {
    const na = g.nodes[a]
    const nb = g.nodes[b]
    if (!na || !nb) continue
    if (circular) {
      const angA = Math.atan2(na.y - cy, na.x - cx)
      const angB = Math.atan2(nb.y - cy, nb.x - cx)
      let delta = angB - angA
      while (delta <= -Math.PI) delta += 2 * Math.PI
      while (delta > Math.PI) delta -= 2 * Math.PI
      if (Math.abs(delta) < Math.PI * 0.15) {
        // Quadratic Bézier with control point pulled inward so the
        // edge visibly curves away from the ring (arcs were invisible
        // because their sag is < 1 px for short chords).
        const mx = (na.x + nb.x) / 2
        const my = (na.y + nb.y) / 2
        const dx = mx - cx
        const dy = my - cy
        const dist = Math.hypot(dx, dy)
        if (dist > 1e-6) {
          const inset = rMean * 0.1
          const cpx = mx - (dx / dist) * inset
          const cpy = my - (dy / dist) * inset
          parts.push(`M${na.x} ${na.y}Q${cpx} ${cpy} ${nb.x} ${nb.y}`)
        } else {
          parts.push(`M${na.x} ${na.y}L${nb.x} ${nb.y}`)
        }
        continue
      }
    }
    parts.push(`M${na.x} ${na.y}L${nb.x} ${nb.y}`)
  }
  return parts.join('')
}

function preloadMinNeighborDistance(g: PreloadGraph): number {
  if (g.nodes.length < 2) return Infinity
  let best = Infinity
  for (let i = 0; i < g.nodes.length; i++) {
    let nodeBest = Infinity
    for (let j = 0; j < g.nodes.length; j++) {
      if (i === j) continue
      const dx = g.nodes[i].x - g.nodes[j].x
      const dy = g.nodes[i].y - g.nodes[j].y
      const d2 = dx * dx + dy * dy
      if (d2 < nodeBest) nodeBest = d2
    }
    if (nodeBest < best) best = nodeBest
  }
  return Math.sqrt(best)
}

// Component

interface SVGRendererProps {
  phase: 'preload' | 'loading' | 'ready' | 'playing' | 'paused' | 'finished'
  layout: LayoutResult | null
  edges: readonly EdgePair[] | null
  state: ReplayState | null
  showLabels?: boolean
  hideRedundantMessages?: boolean
  selectedNodeId?: number | null
  onNodeClick?: (nodeId: number) => void
  pulseDurationMs?: number
}

export function SVGRenderer({
  phase,
  layout,
  edges,
  state,
  showLabels = false,
  hideRedundantMessages = false,
  selectedNodeId = null,
  onNodeClick,
  pulseDurationMs = 600,
}: SVGRendererProps) {

  // All hooks must be called unconditionally.

  const [preloadGraph, setPreloadGraph] = useState<PreloadGraph>(generatePreloadGraph)
  const [preloadOpacity, setPreloadOpacity] = useState(1)

  const isPreload = phase === 'preload' || phase === 'loading' || !layout || layout.length === 0

  // Refresh the idle graph on entry and rotate it every few seconds.
  const wasPreloadRef = useRef(isPreload)
  useEffect(() => {
    if (!isPreload) {
      wasPreloadRef.current = false
      return
    }

    const enteringPreload = !wasPreloadRef.current
    wasPreloadRef.current = true

    let refreshId: number | null = null
    if (enteringPreload) {
      // Defer to a microtask so the setState doesn't happen during commit.
      refreshId = window.setTimeout(() => {
        setPreloadGraph(generatePreloadGraph())
        setPreloadOpacity(1)
      }, 0)
    }

    let timeoutId: number | null = null
    const intervalId = window.setInterval(() => {
      setPreloadOpacity(0)
      timeoutId = window.setTimeout(() => {
        setPreloadGraph(generatePreloadGraph())
        setPreloadOpacity(1)
      }, 400)
    }, 5000)

    return () => {
      if (refreshId !== null) clearTimeout(refreshId)
      clearInterval(intervalId)
      if (timeoutId !== null) clearTimeout(timeoutId)
    }
  }, [isPreload])

  const bounds = useMemo(() => {
    if (!layout || layout.length === 0) return null
    let mx = layout[0].x, my = layout[0].y
    let Mx = layout[0].x, My = layout[0].y
    for (const node of layout) {
      if (node.x < mx) mx = node.x
      if (node.y < my) my = node.y
      if (node.x > Mx) Mx = node.x
      if (node.y > My) My = node.y
    }
    return { minX: mx, minY: my, maxX: Mx, maxY: My }
  }, [layout])

  // Detect circular layouts so adjacent-node edges can be drawn as arcs
  // instead of chords that would cut through the ring.
  const circleInfo = useMemo(() => {
    if (!layout || layout.length < 8) return null
    let sx = 0, sy = 0
    for (const n of layout) { sx += n.x; sy += n.y }
    const cx = sx / layout.length
    const cy = sy / layout.length
    let rSum = 0
    let rMin = Infinity, rMax = -Infinity
    for (const n of layout) {
      const d = Math.hypot(n.x - cx, n.y - cy)
      rSum += d
      if (d < rMin) rMin = d
      if (d > rMax) rMax = d
    }
    const rMean = rSum / layout.length
    if (rMean < 1e-6) return null
    const spread = (rMax - rMin) / rMean
    if (spread > 0.05) return null
    return { cx, cy, r: rMean }
  }, [layout])

  // Nearest-neighbor distance drives density-aware node/edge sizing.
  const minNeighborDistance = useMemo(() => {
    if (!layout || layout.length < 2) return Infinity
    const n = layout.length
    const sampleSize = Math.min(n, 300)
    const stride = Math.max(1, Math.floor(n / sampleSize))
    let best = Infinity
    for (let i = 0; i < n; i += stride) {
      let nodeBest = Infinity
      const xi = layout[i].x
      const yi = layout[i].y
      for (let j = 0; j < n; j++) {
        if (j === i) continue
        const dx = xi - layout[j].x
        const dy = yi - layout[j].y
        const d2 = dx * dx + dy * dy
        if (d2 < nodeBest) nodeBest = d2
      }
      if (nodeBest < best) best = nodeBest
    }
    return Math.sqrt(best)
  }, [layout])

  const dedupedEdges = useMemo(() => {
    if (!edges) return []
    const seen = new Set<string>()
    const result: [number, number][] = []
    for (const [a, b] of edges) {
      const lo = Math.min(a, b)
      const hi = Math.max(a, b)
      const key = `${lo}-${hi}`
      if (!seen.has(key)) {
        seen.add(key)
        result.push([lo, hi])
      }
    }
    return result
  }, [edges])

  const handleNodeClick = useCallback((nodeId: number) => {
    onNodeClick?.(nodeId)
  }, [onNodeClick])

  // Zoom & pan state

  const svgRef = useRef<SVGSVGElement | null>(null)
  const [zoom, setZoom] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const [isDragging, setIsDragging] = useState(false)
  const panDragRef = useRef<{
    startX: number
    startY: number
    originPanX: number
    originPanY: number
    moved: boolean
  } | null>(null)

  // Reset zoom/pan when the underlying graph changes.
  const lastLayoutRef = useRef(layout)
  useEffect(() => {
    if (lastLayoutRef.current === layout) return
    lastLayoutRef.current = layout
    const id = window.setTimeout(() => {
      setZoom(1)
      setPan({ x: 0, y: 0 })
    }, 0)
    return () => clearTimeout(id)
  }, [layout])

  // Frame / geometry derivations

  const geometry = useMemo(() => {
    if (!bounds) {
      return {
        hasGraph: false,
        width: 1, height: 1, squareSide: 1,
        graphCx: 0, graphCy: 0,
        totalSide: 1,
        nodeRadius: 1, frameStroke: 1, nodeStroke: 1, edgeStroke: 1,
      }
    }
    const width = Math.max(bounds.maxX - bounds.minX, 1)
    const height = Math.max(bounds.maxY - bounds.minY, 1)
    const squareSide = Math.max(width, height)
    const graphCx = bounds.minX + width / 2
    const graphCy = bounds.minY + height / 2
    const padding = squareSide * 0.12
    const totalSide = squareSide + padding * 2
    const baseNodeRadius = squareSide * 0.012
    const densityCap = minNeighborDistance * 0.42
    const nodeRadius = Math.max(
      squareSide * 0.0008,
      Math.min(baseNodeRadius, densityCap),
    )
    const frameStroke = squareSide * 0.004
    const nodeStroke = Math.max(squareSide * 0.0004, nodeRadius * 0.28)
    const edgeStroke = Math.max(squareSide * 0.0003, nodeRadius * 0.12)
    return {
      hasGraph: true,
      width, height, squareSide,
      graphCx, graphCy,
      totalSide,
      nodeRadius, frameStroke, nodeStroke, edgeStroke,
    }
  }, [bounds, minNeighborDistance])

  const { totalSide, graphCx, graphCy, squareSide, nodeRadius, nodeStroke, edgeStroke, frameStroke } = geometry
  const viewSide = totalSide / zoom
  const vMinX = graphCx - viewSide / 2 + pan.x
  const vMinY = graphCy - viewSide / 2 + pan.y

  const informedSet = useMemo(
    () => new Set(state?.informedNodes || []),
    [state?.informedNodes],
  )
  const newlyInformedSet = useMemo(
    () => state?.newlyInformedNodes || new Set<number>(),
    [state?.newlyInformedNodes],
  )
  const failedSet = useMemo(
    () => state?.failedNodes || new Set<number>(),
    [state?.failedNodes],
  )

  // All edges collapsed into a single <path>.
  const edgesD = useMemo(() => {
    if (!layout) return ''
    const parts: string[] = []
    for (const [a, b] of dedupedEdges) {
      const na = layout[a]
      const nb = layout[b]
      if (!na || !nb) continue

      if (circleInfo) {
        const angA = Math.atan2(na.y - circleInfo.cy, na.x - circleInfo.cx)
        const angB = Math.atan2(nb.y - circleInfo.cy, nb.x - circleInfo.cx)
        let delta = angB - angA
        while (delta <= -Math.PI) delta += 2 * Math.PI
        while (delta > Math.PI) delta -= 2 * Math.PI
        if (Math.abs(delta) < Math.PI * 0.15) {
          // Quadratic Bézier with control point pulled inward so the
          // edge visibly curves away from the ring (arcs were invisible
          // because their sag is < 1 px for short chords).
          const mx = (na.x + nb.x) / 2
          const my = (na.y + nb.y) / 2
          const dx = mx - circleInfo.cx
          const dy = my - circleInfo.cy
          const dist = Math.hypot(dx, dy)
          if (dist > 1e-6) {
            const inset = circleInfo.r * 0.1
            const cpx = mx - (dx / dist) * inset
            const cpy = my - (dy / dist) * inset
            parts.push(`M${na.x} ${na.y}Q${cpx} ${cpy} ${nb.x} ${nb.y}`)
          } else {
            parts.push(`M${na.x} ${na.y}L${nb.x} ${nb.y}`)
          }
          continue
        }
      }

      parts.push(`M${na.x} ${na.y}L${nb.x} ${nb.y}`)
    }
    return parts.join('')
  }, [dedupedEdges, layout, circleInfo])

  // Nodes grouped by visual state into four <path>s.
  const nodePaths = useMemo(() => {
    if (!layout) return { filled: '', hollow: '', filledNewly: '', failed: '' }
    let filled = ''
    let hollow = ''
    let filledNewly = ''
    let failed = ''
    const rSmall = nodeRadius
    const rLarge = nodeRadius * 1.3
    for (const node of layout) {
      const isFailed = failedSet.has(node.id)
      const isSource = state?.sourceNode === node.id
      const isInformed = informedSet.has(node.id) || isSource
      const isNewly = newlyInformedSet.has(node.id)
      const r = isNewly ? rLarge : rSmall
      const seg = `M${node.x - r} ${node.y}a${r} ${r} 0 1 0 ${r * 2} 0a${r} ${r} 0 1 0 ${-r * 2} 0`
      if (isFailed && !isSource) failed += seg
      else if (isNewly && isInformed) filledNewly += seg
      else if (isInformed) filled += seg
      else hollow += seg
    }
    return { filled, hollow, filledNewly, failed }
  }, [layout, informedSet, newlyInformedSet, failedSet, state?.sourceNode, nodeRadius])

  // Zoom / pan handlers

  const applyZoom = useCallback((nextZoom: number, anchorClientX?: number, anchorClientY?: number) => {
    const svg = svgRef.current
    const target = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, nextZoom))
    if (!svg || !geometry.hasGraph) {
      setZoom(target)
      return
    }
    const rect = svg.getBoundingClientRect()
    const ax = anchorClientX ?? rect.left + rect.width / 2
    const ay = anchorClientY ?? rect.top + rect.height / 2
    const normX = (ax - rect.left) / rect.width
    const normY = (ay - rect.top) / rect.height
    const cursorSvgX = vMinX + normX * viewSide
    const cursorSvgY = vMinY + normY * viewSide
    const newViewSide = totalSide / target
    const newVMinX = cursorSvgX - normX * newViewSide
    const newVMinY = cursorSvgY - normY * newViewSide
    const newPanX = newVMinX + newViewSide / 2 - graphCx
    const newPanY = newVMinY + newViewSide / 2 - graphCy
    setZoom(target)
    setPan({ x: newPanX, y: newPanY })
  }, [vMinX, vMinY, viewSide, totalSide, graphCx, graphCy, geometry.hasGraph])

  // React attaches wheel listeners as passive by default, so preventDefault()
  // on a synthetic onWheel handler is a no-op. Attach a native non-passive
  // listener so zoom cleanly overrides page scroll.
  const applyZoomRef = useRef(applyZoom)
  const zoomRef = useRef(zoom)
  useEffect(() => { applyZoomRef.current = applyZoom }, [applyZoom])
  useEffect(() => { zoomRef.current = zoom }, [zoom])
  const svgCallbackRef = useCallback((svg: SVGSVGElement | null) => {
    const prev = svgRef.current
    if (prev && (prev as SVGSVGElement & { __wheelCleanup?: () => void }).__wheelCleanup) {
      (prev as SVGSVGElement & { __wheelCleanup?: () => void }).__wheelCleanup!()
    }
    svgRef.current = svg
    if (!svg) return
    const listener = (e: WheelEvent) => {
      e.preventDefault()
      const factor = e.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP
      applyZoomRef.current(zoomRef.current * factor, e.clientX, e.clientY)
    }
    svg.addEventListener('wheel', listener, { passive: false })
    ;(svg as SVGSVGElement & { __wheelCleanup?: () => void }).__wheelCleanup = () => {
      svg.removeEventListener('wheel', listener)
    }
  }, [])

  const handlePointerDown = useCallback((e: React.PointerEvent<SVGSVGElement>) => {
    if (e.button !== 0) return
    ;(e.currentTarget as Element).setPointerCapture?.(e.pointerId)
    panDragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      originPanX: pan.x,
      originPanY: pan.y,
      moved: false,
    }
  }, [pan.x, pan.y])

  const handlePointerMove = useCallback((e: React.PointerEvent<SVGSVGElement>) => {
    const drag = panDragRef.current
    if (!drag) return
    const dxPx = e.clientX - drag.startX
    const dyPx = e.clientY - drag.startY
    if (!drag.moved && Math.hypot(dxPx, dyPx) < CLICK_PIXEL_THRESHOLD) return
    drag.moved = true
    setIsDragging(true)
    const svg = svgRef.current
    if (!svg) return
    const rect = svg.getBoundingClientRect()
    const dx = (dxPx / rect.width) * viewSide
    const dy = (dyPx / rect.height) * viewSide
    setPan({ x: drag.originPanX - dx, y: drag.originPanY - dy })
  }, [viewSide])

  const handlePointerUp = useCallback((e: React.PointerEvent<SVGSVGElement>) => {
    ;(e.currentTarget as Element).releasePointerCapture?.(e.pointerId)
    panDragRef.current = null
    setIsDragging(false)
  }, [])

  const handleResetZoom = useCallback(() => {
    setZoom(1)
    setPan({ x: 0, y: 0 })
  }, [])

  const isTransformed = zoom !== 1 || pan.x !== 0 || pan.y !== 0

  // Preload geometry uses the same rules as the main graph.
  const preloadGeometry = useMemo(() => {
    const pg = preloadGraph
    if (pg.nodes.length === 0) return null
    let mnx = pg.nodes[0].x, mxx = pg.nodes[0].x
    let mny = pg.nodes[0].y, mxy = pg.nodes[0].y
    for (const n of pg.nodes) {
      if (n.x < mnx) mnx = n.x
      if (n.x > mxx) mxx = n.x
      if (n.y < mny) mny = n.y
      if (n.y > mxy) mxy = n.y
    }
    const width = Math.max(mxx - mnx, 1)
    const height = Math.max(mxy - mny, 1)
    const squareSide = Math.max(width, height)
    const graphCx = mnx + width / 2
    const graphCy = mny + height / 2
    const padding = squareSide * 0.12
    const totalSide = squareSide + padding * 2
    const baseNodeRadius = squareSide * 0.012
    const densityCap = preloadMinNeighborDistance(pg) * 0.42
    const r = Math.max(
      squareSide * 0.0008,
      Math.min(baseNodeRadius, densityCap),
    )
    return {
      pg,
      graphCx,
      graphCy,
      totalSide,
      nodeRadius: r,
      nodeStroke: Math.max(squareSide * 0.0004, r * 0.28),
      edgeStroke: Math.max(squareSide * 0.0003, r * 0.12),
      frameStroke: squareSide * 0.004,
      edgesD: buildPreloadEdgesD(pg),
    }
  }, [preloadGraph])

  // Preload rendering

  if (isPreload || !bounds) {
    if (!preloadGeometry) return null
    const {
      pg, graphCx: pgx, graphCy: pgy, totalSide: pTotal,
      nodeRadius: pR, nodeStroke: pNS, edgeStroke: pES, frameStroke: pFS,
      edgesD: pEdgesD,
    } = preloadGeometry
    const pViewBox = `${pgx - pTotal / 2} ${pgy - pTotal / 2} ${pTotal} ${pTotal}`

    return (
      <svg
        viewBox={pViewBox}
        preserveAspectRatio="xMidYMid meet"
        className="app__svg-scene"
        style={{ width: '100%', height: '100%', display: 'block' }}
      >
        <rect
          x={pgx - pTotal / 2}
          y={pgy - pTotal / 2}
          width={pTotal}
          height={pTotal}
          fill="white"
        />

        <rect
          x={pgx - pTotal / 2 + pFS / 2}
          y={pgy - pTotal / 2 + pFS / 2}
          width={pTotal - pFS}
          height={pTotal - pFS}
          fill="none"
          stroke="black"
          strokeWidth={pFS}
          vectorEffect="non-scaling-stroke"
        />

        <g style={{ opacity: preloadOpacity, transition: 'opacity 0.4s ease-in-out' }}>
          {pEdgesD && (
            <path
              d={pEdgesD}
              fill="none"
              stroke="black"
              strokeWidth={pES}
              vectorEffect="non-scaling-stroke"
            />
          )}

          {pg.nodes.map(node => (
            <circle
              key={`n-${node.id}`}
              cx={node.x}
              cy={node.y}
              r={pR}
              fill="white"
              stroke="black"
              strokeWidth={pNS}
              vectorEffect="non-scaling-stroke"
            />
          ))}
        </g>
      </svg>
    )
  }

  // Graph rendering

  const viewBox = `${vMinX} ${vMinY} ${viewSide} ${viewSide}`

  return (
    <div className="app__svg-wrapper" style={{ position: 'relative', width: '100%', height: '100%' }}>
      <svg
        ref={svgCallbackRef}
        viewBox={viewBox}
        preserveAspectRatio="xMidYMid meet"
        className="app__svg-scene"
        style={{
          width: '100%',
          height: '100%',
          display: 'block',
          cursor: isDragging ? 'grabbing' : 'grab',
          touchAction: 'none',
        }}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
      >
        {/* Background */}
        <rect
          x={graphCx - totalSide / 2}
          y={graphCy - totalSide / 2}
          width={totalSide}
          height={totalSide}
          fill="white"
        />

        {/* Frame */}
        <rect
          x={graphCx - totalSide / 2 + frameStroke / 2}
          y={graphCy - totalSide / 2 + frameStroke / 2}
          width={totalSide - frameStroke}
          height={totalSide - frameStroke}
          fill="none"
          stroke="black"
          strokeWidth={frameStroke}
        />

        {/* Edges */}
        {edgesD && (
          <path
            d={edgesD}
            fill="none"
            stroke="black"
            strokeWidth={edgeStroke}
            vectorEffect="non-scaling-stroke"
          />
        )}

        {/* Message pulses */}
        <g className="messages">
          {phase !== 'finished' && state?.messages.map((msg, i) => {
            if (!msg.newInfection && hideRedundantMessages) return null
            const ns = layout[msg.sender]
            const nr = layout[msg.receiver]
            if (!ns || !nr) return null

            const size = msg.newInfection ? nodeRadius * 0.6 : nodeRadius * 0.3
            const dur = `${(pulseDurationMs / 1000).toFixed(3)}s`
            return (
              <circle
                key={`msg-${state.currentRound}-${i}`}
                cx={ns.x}
                cy={ns.y}
                r={size}
                fill="black"
              >
                <animate attributeName="cx" values={`${ns.x};${nr.x}`} dur={dur} fill="freeze" />
                <animate attributeName="cy" values={`${ns.y};${nr.y}`} dur={dur} fill="freeze" />
              </circle>
            )
          })}
        </g>

        {/* Nodes */}
        {nodePaths.hollow && (
          <path
            d={nodePaths.hollow}
            fill="white"
            stroke="black"
            strokeWidth={nodeStroke}
            vectorEffect="non-scaling-stroke"
          />
        )}
        {nodePaths.filled && (
          <path
            d={nodePaths.filled}
            fill="black"
            stroke="black"
            strokeWidth={nodeStroke}
            vectorEffect="non-scaling-stroke"
          />
        )}
        {nodePaths.filledNewly && (
          <path
            d={nodePaths.filledNewly}
            fill="black"
            stroke="black"
            strokeWidth={nodeStroke}
            vectorEffect="non-scaling-stroke"
          />
        )}
        {nodePaths.failed && (
          <path
            d={nodePaths.failed}
            fill="#c62828"
            stroke="#c62828"
            strokeWidth={nodeStroke}
            vectorEffect="non-scaling-stroke"
          />
        )}

        {/* Invisible hit-targets for clicks */}
        <g className="node-hits">
          {layout.map(node => (
            <circle
              key={`hit-${node.id}`}
              cx={node.x}
              cy={node.y}
              r={nodeRadius * 1.3}
              fill="transparent"
              style={{ cursor: 'pointer' }}
              onPointerDown={e => {
                e.stopPropagation()
              }}
              onClick={() => handleNodeClick(node.id)}
            />
          ))}
        </g>

        {/* Selection highlight */}
        {selectedNodeId !== null && layout[selectedNodeId] && (
          <circle
            cx={layout[selectedNodeId].x}
            cy={layout[selectedNodeId].y}
            r={
              (newlyInformedSet.has(selectedNodeId) ? nodeRadius * 1.3 : nodeRadius)
              + nodeRadius * 0.7
            }
            fill="none"
            stroke={failedSet.has(selectedNodeId) ? '#c62828' : 'black'}
            strokeWidth={nodeStroke * 0.7}
            strokeDasharray={`${squareSide * 0.004} ${squareSide * 0.003}`}
            vectorEffect="non-scaling-stroke"
            pointerEvents="none"
          />
        )}

        {/* Labels */}
        <g className="labels">
          {layout.map(node => {
            const isSelected = selectedNodeId === node.id
            if (!showLabels && !isSelected) return null
            const isFailed = failedSet.has(node.id) && state?.sourceNode !== node.id

            return (
              <text
                key={`lbl-${node.id}`}
                x={node.x}
                y={node.y - nodeRadius * 2}
                fontSize={nodeRadius * (isSelected ? 1.2 : 0.9)}
                fontWeight={isSelected ? 600 : 400}
                textAnchor="middle"
                fill={isFailed ? '#c62828' : 'black'}
                pointerEvents="none"
                fontFamily="'JetBrains Mono', monospace"
              >
                {node.id}
              </text>
            )
          })}
        </g>
      </svg>

      <div className="app__zoom-controls" role="group" aria-label="Zoom controls">
        <button
          type="button"
          className="ui-btn ui-btn--zoom"
          title="Zoom in"
          aria-label="Zoom in"
          onClick={() => applyZoom(zoom * ZOOM_STEP)}
          disabled={zoom >= MAX_ZOOM}
        >
          +
        </button>
        <button
          type="button"
          className="ui-btn ui-btn--zoom"
          title="Zoom out"
          aria-label="Zoom out"
          onClick={() => applyZoom(zoom / ZOOM_STEP)}
          disabled={zoom <= MIN_ZOOM}
        >
          −
        </button>
        <button
          type="button"
          className="ui-btn ui-btn--zoom"
          title="Reset zoom and pan"
          aria-label="Reset zoom and pan"
          onClick={handleResetZoom}
          disabled={!isTransformed}
        >
          ⌖
        </button>
      </div>
    </div>
  )
}
