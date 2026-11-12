import { describe, expect, it } from 'vitest'
import { en } from './en'
import { nb } from './nb'

type Leaf = { path: string; en: unknown; nb: unknown }

function leaves(a: unknown, b: unknown, path = ''): Leaf[] {
  if (typeof a === 'object' && a !== null) {
    return Object.keys(a).flatMap((k) =>
      leaves((a as Record<string, unknown>)[k], (b as Record<string, unknown>)[k], path ? `${path}.${k}` : k),
    )
  }
  return [{ path, en: a, nb: b }]
}

const all = leaves(en, nb)

// Text that is legitimately the same in both languages
// (proper names, the ECO code system, and words Norwegian uses as-is: admin, rating, score, form, format, kudos)
const SAME_IN_BOTH = new Set([
  'appName', 'language.nb', 'language.en', 'logGame.eco', 'common.admin', 'nav.admin', 'gameDetail.adminTitle', 'admin.title',
  'leaderboard.rating', 'awards.rating', 'year.rating', 'year.sRating', 'year.groupIntro', 'leaderboard.score', 'profile.score', 'profile.form', 'tournamentNew.format', 'social.kudos',
])

describe('dictionaries', () => {
  it('have no empty texts', () => {
    for (const l of all) {
      if (typeof l.en === 'string') expect(l.en.trim(), `en.${l.path}`).not.toBe('')
      if (typeof l.nb === 'string') expect(l.nb.trim(), `nb.${l.path}`).not.toBe('')
    }
  })

  it('have no Norwegian text that is just the English copied over', () => {
    const copied = all
      .filter((l) => typeof l.en === 'string' && l.en === l.nb && /\p{L}{3,}/u.test(l.en as string))
      .map((l) => l.path)
      .filter((p) => !SAME_IN_BOTH.has(p))
    expect(copied).toEqual([])
  })

  it('error messages with parameters never show "undefined" when a parameter is missing', () => {
    for (const dict of [en, nb]) {
      for (const [code, entry] of Object.entries(dict.errors.codes)) {
        if (typeof entry === 'function') {
          expect(entry({}), code).not.toMatch(/undefined|NaN|null/)
        }
      }
    }
  })

  it('functions give the same kind of result in both languages', () => {
    for (const l of all) {
      expect(typeof l.nb, l.path).toBe(typeof l.en)
      if (typeof l.en === 'function') {
        expect((l.nb as (...a: unknown[]) => unknown).length, l.path).toBe((l.en as (...a: unknown[]) => unknown).length)
      }
    }
  })
})
