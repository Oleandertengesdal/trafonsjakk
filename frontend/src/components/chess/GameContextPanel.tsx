import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Zap } from 'lucide-react'
import { gamesApi } from '../../api/endpoints'
import type { Game, GameContext } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'
import { Panel } from '../ui/Panel'
import { Skeleton } from '../ui/Skeleton'

// The two sides keep the colours of their pieces in every bar here: light for this game's
// white player, dark for black, grey for draws. Fixed tones so they read the same in both themes.
const LIGHT = 'bg-[#efe4cc] ring-1 ring-inset ring-[#c9b78f]'
const DARK = 'bg-[#2e333a] ring-1 ring-inset ring-white/10'
const DRAW = 'bg-faint/50'

/**
 * What the game meant before it was played: each side's expected score from Glicko-2,
 * whether the result was an upset, and how the two have done against each other.
 */
export function GameContextPanel({ game }: { game: Game }) {
  const { t } = useI18n()
  const q = useQuery({ queryKey: ['games', 'context', game.id], queryFn: () => gamesApi.context(game.id) })

  // Extra information: if it fails, the page simply goes without it.
  if (q.isError) return null
  return (
    <Panel title={t.context.title}>
      {q.isPending ? (
        <div className="px-5 pt-2 pb-5">
          <Skeleton className="h-24 w-full" />
        </div>
      ) : (
        <ContextBody game={game} ctx={q.data} />
      )}
    </Panel>
  )
}

function ContextBody({ game, ctx }: { game: Game; ctx: GameContext }) {
  const { t, f } = useI18n()
  const c = t.context
  const white = game.white.realName
  const black = game.black.realName
  const h = ctx.headToHead
  const winner = game.result === 'WHITE_WIN' ? { name: white, p: ctx.expected.white } : game.result === 'BLACK_WIN' ? { name: black, p: ctx.expected.black } : null
  const nameOf = (whiteId: number, result: GameContext['recent'][number]['result']) => {
    if (result === 'DRAW') return null
    const winnerId = result === 'WHITE_WIN' ? whiteId : whiteId === game.white.id ? game.black.id : game.white.id
    return winnerId === game.white.id ? white : black
  }

  return (
    <div className="flex flex-col gap-6 px-5 pt-2 pb-5">
      <section>
        <h3 className="sr-only">{c.expected}</h3>
        <div className="flex items-baseline justify-between gap-4 text-sm">
          <span className="min-w-0 truncate">
            <span className="font-display text-2xl font-semibold tabular-nums">{f.pct(ctx.expected.white)}</span>{' '}
            <span className="text-muted">{white}</span>
          </span>
          <span className="min-w-0 truncate text-right">
            <span className="text-muted">{black}</span>{' '}
            <span className="font-display text-2xl font-semibold tabular-nums">{f.pct(ctx.expected.black)}</span>
          </span>
        </div>
        <div
          role="img"
          aria-label={`${c.expected}: ${white} ${f.pct(ctx.expected.white)}, ${black} ${f.pct(ctx.expected.black)}`}
          className="mt-2 flex h-3 gap-0.5"
        >
          <span className={cn('rounded-l-full', LIGHT)} style={{ flexGrow: ctx.expected.white }} />
          <span className={cn('rounded-r-full', DARK)} style={{ flexGrow: ctx.expected.black }} />
        </div>
        <p className="mt-2 text-[0.8125rem] text-muted">
          {c.expected}. {c.expectedHint}.
        </p>

        {ctx.upset && winner && (
          <div className="mt-4 flex gap-3 rounded-xl border border-gold/40 bg-gold/8 px-4 py-3">
            <Zap aria-hidden className="mt-0.5 size-4.5 shrink-0 fill-current text-gold" />
            <p className="text-[0.9375rem]">
              <strong className="font-semibold">{c.upset}</strong>
              <span className="block text-muted">{c.upsetBody(winner.name, f.pct(winner.p))}</span>
            </p>
          </div>
        )}
      </section>

      <section>
        <h3 className="font-display text-[0.9375rem] font-semibold">{c.headToHead}</h3>
        {h.games === 0 ? (
          <p className="mt-1 text-[0.9375rem] text-muted">{c.firstMeeting}</p>
        ) : (
          <>
            <div className="mt-2 grid grid-cols-[1fr_auto_1fr] items-center gap-3">
              <span className="truncate text-sm text-muted">{white}</span>
              <span className="text-center">
                <span className="block font-display text-2xl font-semibold tabular-nums">{c.record(h.whiteWins, h.blackWins)}</span>
                {h.draws > 0 && <span className="block text-[0.8125rem] text-muted">{c.draws(h.draws)}</span>}
              </span>
              <span className="truncate text-right text-sm text-muted">{black}</span>
            </div>
            <div aria-hidden className="mt-2 flex h-2 gap-0.5">
              {h.whiteWins > 0 && <span className={cn('rounded-l-full', LIGHT, h.draws + h.blackWins === 0 && 'rounded-r-full')} style={{ flexGrow: h.whiteWins }} />}
              {h.draws > 0 && <span className={cn(DRAW, h.whiteWins === 0 && 'rounded-l-full', h.blackWins === 0 && 'rounded-r-full')} style={{ flexGrow: h.draws }} />}
              {h.blackWins > 0 && <span className={cn('rounded-r-full', DARK, h.whiteWins + h.draws === 0 && 'rounded-l-full')} style={{ flexGrow: h.blackWins }} />}
            </div>
            <p className="mt-2 flex flex-wrap gap-x-1 text-[0.8125rem] text-muted">
              {ctx.meeting === 1 ? <span>{c.firstMeeting}</span> : ctx.meeting ? <span>{c.meeting(ctx.meeting)}</span> : null}
              {ctx.meeting !== 1 && h.firstPlayedAt && <span>{c.since(f.date(h.firstPlayedAt))}</span>}
            </p>
          </>
        )}

        {ctx.recent.length > 0 && (
          <>
            <h4 className="mt-4 text-[0.8125rem] text-muted">{c.recent}</h4>
            <ul aria-label={c.recent} className="mt-1 divide-y divide-line border-y border-line">
              {ctx.recent.map((r) => {
                const won = nameOf(r.whiteId, r.result)
                return (
                  <li key={r.id}>
                    <Link to={`/games/${r.id}`} className="flex items-center justify-between gap-3 py-2 text-[0.9375rem] hover:text-accent">
                      <span>{won ? c.won(won) : c.drawn}</span>
                      <span className="text-sm text-muted tabular-nums">{f.date(r.playedAt)}</span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </section>
    </div>
  )
}
