import { describe, expect, it } from 'vitest'
import { isIos, keyBytes } from './push'

describe('push helpers', () => {
  it('turns a base64url key into bytes', () => {
    expect(Array.from(keyBytes('AQID_-8'))).toEqual([1, 2, 3, 255, 239])
    expect(keyBytes('BAAA').length).toBe(3)
  })

  it('knows an iPhone or iPad when it sees one', () => {
    const nav = (userAgent: string, platform = '', maxTouchPoints = 0) => ({ userAgent, platform, maxTouchPoints }) as Navigator
    expect(isIos(nav('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)'))).toBe(true)
    expect(isIos(nav('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 'MacIntel', 5))).toBe(true)
    expect(isIos(nav('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 'MacIntel', 0))).toBe(false)
    expect(isIos(nav('Mozilla/5.0 (Linux; Android 15; Pixel 9)'))).toBe(false)
  })
})
