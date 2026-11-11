import { renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { en } from './en'
import { I18nProvider } from './I18nProvider'
import { nb } from './nb'
import { useErrorText, useFieldErrors } from './useErrorText'

const wrapper = ({ children }: { children: ReactNode }) => <I18nProvider>{children}</I18nProvider>

function errorText(lang: 'nb' | 'en') {
  localStorage.setItem('tf-lang', lang)
  return renderHook(() => useErrorText(), { wrapper }).result.current
}

describe('useErrorText', () => {
  it('translates a server error code', () => {
    const e = new ApiError(404, 'Game not found', { code: 'game.not_found' })
    expect(errorText('en')(e)).toBe(en.errors.codes['game.not_found'])
    expect(errorText('nb')(e)).toBe(nb.errors.codes['game.not_found'])
  })

  it('fills in parameters', () => {
    const e = new ApiError(409, 'Need at least 3 players', { code: 'tournament.min_players', params: { min: 3 } })
    expect(errorText('en')(e)).toContain('3')
    expect(errorText('nb')(e)).toContain('3')
    const locked = new ApiError(429, 'Locked', { code: 'auth.locked', params: { minutes: 15 } })
    expect(errorText('nb')(locked)).toContain('15')
  })

  it('falls back by status, then to a generic message', () => {
    const t = errorText('en')
    expect(t(new ApiError(0, 'Network error'))).toBe(en.errors.network)
    expect(t(new ApiError(503, 'x', { code: 'not.a.known.code' }))).toBe(en.errors.waking)
    expect(t(new ApiError(404, 'x'))).toBe(en.errors.http[404])
    expect(t(new ApiError(418, 'x'))).toBe(en.errors.generic)
    expect(t(new Error('boom'))).toBe(en.errors.generic)
    expect(t('a string')).toBe(en.errors.generic)
  })

  it('uses Norwegian by default', () => {
    const t = renderHook(() => useErrorText(), { wrapper }).result.current
    expect(t(new ApiError(0, 'Network error'))).toBe(nb.errors.network)
  })
})

describe('useFieldErrors', () => {
  it('maps constraint codes to messages, with a fallback', () => {
    localStorage.setItem('tf-lang', 'en')
    const fields = renderHook(() => useFieldErrors(), { wrapper }).result.current
    const e = new ApiError(400, 'Validation failed', {
      code: 'validation',
      fieldErrors: { username: 'x', eco: 'y', other: 'z' },
      fieldCodes: { username: 'Size', eco: 'Pattern', other: 'SomethingNew' },
    })
    expect(fields(e)).toEqual({ username: en.errors.field.Size, eco: en.errors.field.Pattern, other: en.errors.field.fallback })
    expect(fields(new Error('x'))).toEqual({})
  })
})
