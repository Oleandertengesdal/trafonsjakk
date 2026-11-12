import { renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useWakeLock } from './useWakeLock'

describe('useWakeLock', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'wakeLock')
  })

  it('keeps the screen on while active and lets go afterwards', async () => {
    const release = vi.fn().mockResolvedValue(undefined)
    const request = vi.fn().mockResolvedValue({ released: false, release })
    Object.defineProperty(navigator, 'wakeLock', { value: { request }, configurable: true })
    const { rerender, unmount } = renderHook(({ on }) => useWakeLock(on), { initialProps: { on: false } })
    expect(request).not.toHaveBeenCalled()
    rerender({ on: true })
    await vi.waitFor(() => expect(request).toHaveBeenCalledWith('screen'))
    unmount()
    expect(release).toHaveBeenCalled()
  })

  it('does nothing where the browser has no wake lock', () => {
    expect(() => renderHook(() => useWakeLock(true)).unmount()).not.toThrow()
  })
})
