import { useEffect } from 'react'

/**
 * Keeps the screen on while `active`, so a phone or tablet doesn't lock between moves. Browsers drop the
 * lock when the page is hidden, so it's taken again when the page comes back. Where the Screen Wake Lock
 * API isn't available (or is refused, e.g. on low battery) nothing happens.
 */
export function useWakeLock(active: boolean) {
  useEffect(() => {
    if (!active || typeof navigator === 'undefined' || !('wakeLock' in navigator)) return
    let lock: WakeLockSentinel | null = null
    let stopped = false
    const take = async () => {
      if (stopped || document.visibilityState !== 'visible' || (lock && !lock.released)) return
      try {
        lock = await navigator.wakeLock.request('screen')
        if (stopped) void lock.release()
      } catch {
        /* refused: the screen just behaves as usual */
      }
    }
    void take()
    document.addEventListener('visibilitychange', take)
    return () => {
      stopped = true
      document.removeEventListener('visibilitychange', take)
      void lock?.release().catch(() => {})
    }
  }, [active])
}
