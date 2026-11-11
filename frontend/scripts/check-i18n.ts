// i18n guard, run in CI:  node --experimental-strip-types scripts/check-i18n.ts
// 1) en and nb dictionaries have exactly the same keys (deep), incl. the error-code tables.
// 2) no hard-coded user-facing text in components (JSX text, text-bearing attributes, toast strings).
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import ts from 'typescript'
import { en } from '../src/i18n/en.ts'
import { nb } from '../src/i18n/nb.ts'

let failures = 0
const fail = (msg: string) => {
  failures++
  console.error('✗ ' + msg)
}

// ---------- 1. key parity ----------
function walk(a: unknown, b: unknown, path: string) {
  const isObj = (v: unknown) => typeof v === 'object' && v !== null
  if (isObj(a) !== isObj(b)) return fail(`${path}: shape differs between en and nb`)
  if (typeof a === 'function' && typeof b !== 'function') return fail(`${path}: en is a function, nb is not`)
  if (typeof a === 'string' && typeof b !== 'string') return fail(`${path}: en is text, nb is not`)
  if (!isObj(a)) return
  const ka = Object.keys(a as object)
  const kb = Object.keys(b as object)
  for (const k of ka) if (!kb.includes(k)) fail(`${path}.${k}: missing in nb`)
  for (const k of kb) if (!ka.includes(k)) fail(`${path}.${k}: missing in en`)
  for (const k of ka) if (kb.includes(k)) walk((a as Record<string, unknown>)[k], (b as Record<string, unknown>)[k], `${path}.${k}`)
}
walk(en, nb, 'dict')

// Same strings in both languages usually means a forgotten translation.
// Words that are genuinely the same in Norwegian and English.
const allowedSame = new Set(['Trafonsjakk', 'Admin', 'Format', 'Rating', 'Score', 'Form', 'ECO', 'Norsk', 'English', 'Kudos'])
function sameText(a: unknown, b: unknown, path: string) {
  if (typeof a === 'string' && a === b && a.length > 3 && !allowedSame.has(a)) {
    console.warn(`! ${path}: identical in en and nb ("${a}")`)
  }
  if (typeof a === 'object' && a && typeof b === 'object' && b) {
    for (const k of Object.keys(a)) sameText((a as Record<string, unknown>)[k], (b as Record<string, unknown>)[k], `${path}.${k}`)
  }
}
sameText(en, nb, 'dict')

// ---------- 2. hard-coded text ----------
const TEXT_ATTRS = new Set(['aria-label', 'title', 'placeholder', 'alt', 'label', 'hint', 'description', 'summary'])
// Placeholders/examples that are deliberately language-neutral
const ALLOWED_LITERALS = new Set(['Trafonsjakk', 'SB', 'C50', 'K7QX4-MZP2R', '1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5', '#'])
const hasLetters = (s: string) => /\p{L}{2,}/u.test(s)

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f)
    // Tests may contain literal text (fixtures, assertions); only app components are checked.
    return statSync(p).isDirectory() ? files(p) : p.endsWith('.tsx') && !p.endsWith('.test.tsx') ? [p] : []
  })
}

for (const file of files('src')) {
  const src = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const report = (node: ts.Node, text: string) => {
    const { line } = src.getLineAndCharacterOfPosition(node.getStart())
    fail(`${file}:${line + 1}: hard-coded text "${text.trim().slice(0, 60)}"`)
  }
  const visit = (node: ts.Node) => {
    if (ts.isJsxText(node)) {
      const text = node.getText().trim()
      if (text && hasLetters(text) && !ALLOWED_LITERALS.has(text)) report(node, text)
    }
    if (ts.isJsxAttribute(node) && node.initializer) {
      const name = node.name.getText()
      const init = node.initializer
      const lit = ts.isStringLiteral(init) ? init.text : ts.isJsxExpression(init) && init.expression && ts.isStringLiteralLike(init.expression) ? init.expression.text : null
      if (lit !== null && TEXT_ATTRS.has(name) && hasLetters(lit) && !ALLOWED_LITERALS.has(lit)) report(node, lit)
    }
    // {'Some text'} or {`Some text`} as a JSX child
    if (ts.isJsxExpression(node) && node.expression && ts.isJsxElement(node.parent) || (ts.isJsxExpression(node) && node.expression && ts.isJsxFragment(node.parent))) {
      const e = node.expression!
      if (ts.isStringLiteralLike(e) && hasLetters(e.text) && !ALLOWED_LITERALS.has(e.text)) report(node, e.text)
      if (ts.isTemplateExpression(e) && hasLetters(e.head.text + e.templateSpans.map((x) => x.literal.text).join(''))) report(node, e.getText())
    }
    // { label: 'White' } style options
    if (ts.isPropertyAssignment(node) && TEXT_ATTRS.has(node.name.getText()) && ts.isStringLiteralLike(node.initializer)) {
      const v = node.initializer.text
      if (hasLetters(v) && !ALLOWED_LITERALS.has(v)) report(node, v)
    }
    if (ts.isCallExpression(node) && node.expression.getText() === 'toast') {
      const arg = node.arguments[0]
      if (arg && ts.isStringLiteralLike(arg) && hasLetters(arg.text)) report(node, arg.text)
    }
    ts.forEachChild(node, visit)
  }
  visit(src)
}

if (failures) {
  console.error(`\n${failures} i18n problem(s)`)
  process.exit(1)
}
console.log('i18n OK: en/nb keys match and no hard-coded UI text found')
