import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './client'

/**
 * Retry while a free-tier backend (or its database) is waking up; never retry auth/validation errors.
 * A crash in our own code (500) gets one more try, then shows its error code instead of spinning.
 */
export function shouldRetry(count: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status > 0 && error.status < 500) return false
  if (error instanceof ApiError && error.status === 500) return count < 1
  // Free hosting sleeps when idle and takes up to a minute to wake: keep trying for ~50 s.
  return count < 6
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      retry: shouldRetry,
      retryDelay: (n) => Math.min(1500 * 2 ** n, 10_000),
    },
  },
})

/** Everything that changes when a game is logged/confirmed: refetch it all. */
export function invalidateResults() {
  return Promise.all(
    ['games', 'pending', 'leaderboard', 'stats', 'profile', 'tournaments', 'tournament', 'myMatches', 'me', 'disputed'].map(
      (key) => queryClient.invalidateQueries({ queryKey: [key] }),
    ),
  )
}
