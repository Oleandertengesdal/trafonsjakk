import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ThumbsUp } from 'lucide-react'
import { socialApi } from '../../api/endpoints'
import type { Game, Kudos } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { cn } from '../../lib/cn'
import { useToast } from '../ui/Toast'

/** Kudos on a game, like on Strava: one per person, not on your own games, once the result is confirmed. */
export function KudosButton({ game }: { game: Game }) {
  const { t } = useI18n()
  const s = t.social
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const key = ['games', 'kudos', game.id]
  const givers = useQuery({ queryKey: key, queryFn: () => socialApi.kudos(game.id), enabled: game.kudosCount > 0 })
  const state: Pick<Kudos, 'count' | 'givenByMe'> = givers.data ?? { count: game.kudosCount, givenByMe: game.kudosGivenByMe }

  const toggle = useMutation({
    mutationFn: () => (state.givenByMe ? socialApi.takeKudos(game.id) : socialApi.giveKudos(game.id)),
    onSuccess: (k) => {
      qc.setQueryData(key, k)
      void qc.invalidateQueries({ queryKey: ['games'], refetchType: 'none' })
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  if (!game.canGiveKudos && state.count === 0) return null
  const names = givers.data?.givers.map((p) => p.realName).join(', ')

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
      {game.canGiveKudos ? (
        <button
          type="button"
          aria-pressed={state.givenByMe}
          aria-label={state.givenByMe ? s.takeKudos : s.giveKudos}
          disabled={toggle.isPending}
          onClick={() => toggle.mutate()}
          className={cn(
            'inline-flex h-10 items-center gap-2 rounded-full border px-4 font-medium tabular-nums transition-[background-color,border-color,color,transform] active:translate-y-px disabled:opacity-60',
            state.givenByMe ? 'border-brand bg-brand text-on-accent' : 'border-line bg-surface text-ink hover:border-faint',
          )}
        >
          <ThumbsUp aria-hidden className={cn('size-4.5', state.givenByMe && 'fill-current')} />
          {state.count}
        </button>
      ) : (
        <span className="inline-flex items-center gap-2 text-[0.9375rem] font-medium">
          <ThumbsUp aria-hidden className="size-4.5 text-accent" />
          {s.kudosCount(state.count)}
        </span>
      )}
      {names && <span className="text-sm text-muted">{s.kudosFrom(names)}</span>}
    </div>
  )
}
