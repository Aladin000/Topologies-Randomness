import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parseReplayFile } from '../input'

describe('input: real-world result.json fixture', () => {
  it('accepts the bundled result.json', () => {
    const path = resolve(__dirname, '../../result.json')
    const text = readFileSync(path, 'utf8')
    const result = parseReplayFile(text)
    if (!result.ok) {
      throw new Error(`Expected acceptance, got ${result.category}: ${result.error}`)
    }
    expect(result.ok).toBe(true)
  })
})
