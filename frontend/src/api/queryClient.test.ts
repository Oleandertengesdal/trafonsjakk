import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { shouldRetry } from './queryClient'

describe('retrying failed loads', () => {
  it('keeps trying while the server or its database wakes up', () => {
    for (const status of [0, 502, 503, 504]) {
      expect(shouldRetry(5, new ApiError(status, 'x'))).toBe(true)
      expect(shouldRetry(6, new ApiError(status, 'x'))).toBe(false)
    }
  })

  it('gives a crash one more try and then shows it', () => {
    expect(shouldRetry(0, new ApiError(500, 'x', { ref: 'K7Q2MX' }))).toBe(true)
    expect(shouldRetry(1, new ApiError(500, 'x', { ref: 'K7Q2MX' }))).toBe(false)
  })

  it('never retries what will fail the same way again', () => {
    for (const status of [400, 401, 403, 404, 409, 429]) expect(shouldRetry(0, new ApiError(status, 'x'))).toBe(false)
  })
})
