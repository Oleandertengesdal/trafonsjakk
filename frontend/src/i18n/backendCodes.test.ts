// Cross-checks the backend's error codes against both dictionaries, so a new
// ApiException code can't ship without Norwegian and English text.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { en } from './en'
import { nb } from './nb'

const BACKEND = join(process.cwd(), '..', 'backend', 'src', 'main', 'java')

function javaFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f)
    return statSync(p).isDirectory() ? javaFiles(p) : p.endsWith('.java') ? [p] : []
  })
}

function backendCodes() {
  const codes = new Set<string>()
  const constraints = new Set<string>()
  for (const file of javaFiles(BACKEND)) {
    const src = readFileSync(file, 'utf8')
    for (const m of src.matchAll(/ApiException\.\w+\(\s*"([^"]+)"/g)) codes.add(m[1])
    for (const m of src.matchAll(/new ApiException\([^,]+,\s*"([^"]+)"/g)) codes.add(m[1])
    for (const m of src.matchAll(/problem\(\s*HttpStatus\.\w+,\s*"([^"]+)"/g)) codes.add(m[1])
    for (const m of src.matchAll(/setProperty\(\s*"code",\s*"([^"]+)"/g)) codes.add(m[1])
    for (const m of src.matchAll(/case \d{3} -> "([^"]+)"/g)) codes.add(m[1]) // status → code fallbacks
    for (const m of src.matchAll(/default -> (status[^;]*);/g)) for (const q of m[1].matchAll(/"([^"]+)"/g)) codes.add(q[1])
    for (const m of src.matchAll(/write\(request, response, HttpStatus\.\w+, "([^"]+)"/g)) codes.add(m[1]) // security filters
    for (const m of src.matchAll(/\\"code\\":\\"([^\\"]+)\\"/g)) codes.add(m[1]) // hand-written JSON
    for (const m of src.matchAll(/import jakarta\.validation\.constraints\.(\w+);/g)) constraints.add(m[1])
    for (const m of src.matchAll(/@Constraint\([\s\S]*?public @interface (\w+)/g)) constraints.add(m[1]) // our own constraints
  }
  return { codes: [...codes].sort(), constraints: [...constraints].sort() }
}

describe.runIf(existsSync(BACKEND))('backend error codes', () => {
  const { codes, constraints } = backendCodes()

  it('finds the codes (sanity check of the scan)', () => {
    expect(codes.length).toBeGreaterThan(40)
    expect(codes).toContain('game.not_found')
    expect(codes).toContain('validation')
    expect(codes).toEqual(expect.arrayContaining(['csrf', 'not_found', 'method_not_allowed', 'auth.not_logged_in', 'request.malformed', 'request.rejected']))
    expect(constraints).toEqual(expect.arrayContaining(['NotBlank', 'Size', 'PlainText']))
  })

  it.each(codes)('%s is translated in English and Norwegian', (code) => {
    expect(en.errors.codes[code], `en.errors.codes['${code}']`).toBeDefined()
    expect(nb.errors.codes[code], `nb.errors.codes['${code}']`).toBeDefined()
  })

  it.each(constraints)('validation constraint @%s has a field message', (name) => {
    expect(en.errors.field[name], `en.errors.field.${name}`).toBeDefined()
    expect(nb.errors.field[name], `nb.errors.field.${name}`).toBeDefined()
  })
})
