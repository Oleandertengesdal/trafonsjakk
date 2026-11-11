import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './client'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      retry: (count, error) => {
        // Retry while a free-tier backend is waking up; never retry auth/validation errors.
        if (error instanceof ApiError && error.status > 0 && error.status < 500) return false
        // Free hosting sleeps when idle and takes up to a minute to wake: keep trying for ~50 s.
        return count < 6
      },
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
