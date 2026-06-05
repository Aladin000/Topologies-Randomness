import { replayFileSchema } from '../schema'
import { validateSemantics } from '../schema/validate'
import type { ReplayFile } from '../schema'

export type InputResult =
  | { ok: true; file: ReplayFile; normalizedEdges: Set<string> }
  | { ok: false; error: string; category: 'parse' | 'structural' | 'semantic' }

/** Parse a JSON string and validate it as a replay file. */
export function parseReplayFile(jsonString: string): InputResult {
  let raw: unknown
  try {
    raw = JSON.parse(jsonString)
  } catch (e) {
    const message = e instanceof Error ? e.message : 'Unknown parse error'
    return { ok: false, error: `Invalid JSON: ${message}`, category: 'parse' }
  }

  return validateReplayData(raw)
}

/** Validate an already-parsed JSON value. */
export function validateReplayData(data: unknown): InputResult {
  const parsed = replayFileSchema.safeParse(data)
  if (!parsed.success) {
    const firstIssue = parsed.error.issues[0]
    const path = firstIssue?.path?.join('.') || ''
    const message = firstIssue?.message || 'Unknown validation error'
    const detail = path ? `${path}: ${message}` : message
    return {
      ok: false,
      error: `Structural validation failed: ${detail}`,
      category: 'structural',
    }
  }

  const semanticResult = validateSemantics(parsed.data)
  if (!semanticResult.valid) {
    return {
      ok: false,
      error: `Semantic validation failed: ${semanticResult.error}`,
      category: 'semantic',
    }
  }

  return {
    ok: true,
    file: semanticResult.file,
    normalizedEdges: semanticResult.normalizedEdges,
  }
}

/** Read a File object and run the full validation pipeline. */
export async function loadReplayFile(file: File): Promise<InputResult> {
  let text: string
  try {
    text = await file.text()
  } catch (e) {
    const message = e instanceof Error ? e.message : 'Unknown read error'
    return { ok: false, error: `Failed to read file: ${message}`, category: 'parse' }
  }

  return parseReplayFile(text)
}
