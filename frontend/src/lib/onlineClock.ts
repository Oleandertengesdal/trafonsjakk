// The clock of an online game, as the page shows it. The server keeps the real clock; between answers
// the page counts down by itself from the moment the answer arrived (not the server's timestamp, so a
// wrong clock on the phone doesn't matter).
import type { Colour, OnlineClock } from '../api/types'

export interface LivePreset {
  initialSeconds: number
  incrementSeconds: number
}

/** The time controls offered when challenging someone. */
export const LIVE_PRESETS: LivePreset[] = [
  { initialSeconds: 180, incrementSeconds: 2 },
  { initialSeconds: 300, incrementSeconds: 3 },
  { initialSeconds: 600, incrementSeconds: 5 },
  { initialSeconds: 900, incrementSeconds: 10 },
  { initialSeconds: 1800, incrementSeconds: 0 },
]

/** Days per move for daily games. */
export const DAILY_PRESETS = [1, 2, 3, 7]

/** "10+5", as the server labels it. */
export function presetLabel(p: LivePreset): string {
  return `${p.initialSeconds / 60}+${p.incrementSeconds}`
}

/**
 * Time left for {@code side}, {@code now − receivedAt} ms after the answer with {@code clock} arrived.
 * A live clock stands still until both first moves are made; a daily game counts down to its deadline.
 */
export function remainingMs(clock: OnlineClock, side: Colour, receivedAt: number, now: number): number {
  const stored = side === 'WHITE' ? clock.whiteMs : clock.blackMs
  const daily = clock.deadline !== null
  const ticking = clock.running && side === clock.toMove && (daily || clock.firstMoveBy === null)
  return ticking ? Math.max(0, stored - (now - receivedAt)) : stored
}

/** "10:00", "1:05", and tenths in the last 20 seconds ("0:09.4"); "1:02:03" past an hour. */
export function formatClock(ms: number): string {
  const t = Math.max(0, ms)
  if (t < 20_000) {
    const tenths = Math.floor(t / 100)
    return `0:${String(Math.floor(tenths / 10)).padStart(2, '0')}.${tenths % 10}`
  }
  const total = Math.floor(t / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = String(total % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

/** Time to hurry: under a tenth of the starting time, or 20 seconds, whichever is less. */
export function isLow(ms: number, initialSeconds: number): boolean {
  return ms < Math.min(20_000, initialSeconds * 100)
}
