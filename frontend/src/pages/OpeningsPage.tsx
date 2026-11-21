// The opening explorer: which openings the group plays and how they score, for everyone or one player.
// The board shows a position; the table lists the moves played from there, with the results. Tap a move
// (or play it on the board) to go deeper. Positions reached by different move orders are merged.
import { useCallback, useMemo } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Chess } from 'chess.js'
import { useQuery } from '@tanstack/react-query'
import { BookOpen, ChevronFirst, ChevronLeft } from 'lucide-react'
import { openingsApi, usersApi } from '../api/endpoints'
import { isNotFound } from '../api/client'
import { buildExplorer, openingOf, playLine, score, START_KEY, type OpeningBook, type Tally } from '../chess/explorer'
import bookUrl from '../chess/openings.json?url'
import { AnalysisBoard } from '../components/chess/AnalysisBoard'
import { PageHeader } from '../components/layout/PageHeader'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Select } from '../components/ui/Field'
import { Panel } from '../components/ui/Panel'
import { Segmented } from '../components/ui/Segmented'
import { Skeleton, SkeletonRows } from '../components/ui/Skeleton'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'
import { NotFoundPage } from './NotFoundPage'

const SCORE = { WHITE_WIN: '1–0', BLACK_WIN: '0–1', DRAW: '½–½' } as const
type Colour = 'both' | 'white' | 'black'

export function OpeningsPage() {
  const { t, f } = useI18n()
  const o = t.openings
  const [params, setParams] = useSearchParams()
  const player = params.get('player') ?? ''
  const rawColour = params.get('colour')
  const colour: Colour = rawColour === 'white' || rawColour === 'black' ? rawColour : 'both'
  const line = useMemo(() => (params.get('line') ?? '').split(' ').filter(Boolean), [params])

  const users = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  // The opening names (≈50 kB), a hashed file the browser keeps
  const book = useQuery({
    queryKey: ['openingBook'],
    queryFn: async () => (await (await fetch(bookUrl)).json()) as OpeningBook,
    staleTime: Infinity,
    gcTime: Infinity,
  })
  const BOOK = book.data
  const games = useQuery({ queryKey: ['openings', player], queryFn: () => openingsApi.games(player || undefined) })
  const me = player ? (users.data?.find((u) => u.username.toLowerCase() === player.toLowerCase()) ?? null) : null

  const explorer = useMemo(
    () => (games.data && BOOK ? buildExplorer(games.data, BOOK, { me: me?.id ?? null, colour: me && colour !== 'both' ? colour : null }) : null),
    [games.data, BOOK, me, colour],
  )
  const here = useMemo(() => playLine(line) ?? playLine([])!, [line])
  const node = explorer?.nodes.get(here.key)
  const name = useMemo(() => (BOOK ? openingOf(line, BOOK) : null), [line, BOOK])

  const update = useCallback(
    (changes: Record<string, string | null>) => {
      const next = new URLSearchParams(params)
      for (const [k, v] of Object.entries(changes)) {
        if (v) next.set(k, v)
        else next.delete(k)
      }
      setParams(next, { replace: true })
    },
    [params, setParams],
  )
  const go = useCallback((l: string[]) => update({ line: l.length ? l.join(' ') : null }), [update])

  const onMove = useCallback(
    (from: string, to: string) => {
      const next = playLine(line)
      if (!next) return false
      const legal = playMove(next.fen, from, to)
      if (!legal) return false
      go([...line, legal])
      return true
    },
    [line, go],
  )

  if (games.isError && isNotFound(games.error)) return <NotFoundPage what="player" />

  return (
    <>
      <PageHeader title={o.title} description={o.description} />
      <div className="mb-5 grid gap-3 sm:grid-cols-[16rem_18rem] sm:items-center">
        <Select aria-label={o.filterPlayer} value={player} onChange={(e) => update({ player: e.target.value || null, colour: null })}>
          <option value="">{o.everyone}</option>
          {users.data?.map((u) => (
            <option key={u.id} value={u.username}>
              {u.realName}
            </option>
          ))}
        </Select>
        {player && (
          <div>
            <Segmented
              label={o.colourLabel}
              hideLabel
              value={colour}
              onChange={(v) => update({ colour: v === 'both' ? null : v })}
              options={[
                { value: 'both', label: o.bothColours },
                { value: 'white', label: o.asWhite },
                { value: 'black', label: o.asBlack },
              ]}
            />
          </div>
        )}
      </div>

      {games.isPending || book.isPending ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,30rem)_1fr]">
          <Skeleton className="aspect-square w-full" />
          <SkeletonRows rows={6} />
        </div>
      ) : games.isError || book.isError ? (
        <Panel>
          <ErrorNotice error={games.error ?? book.error} onRetry={() => void (games.isError ? games.refetch() : book.refetch())} />
        </Panel>
      ) : explorer && explorer.total === 0 ? (
        <Panel>
          <EmptyState icon={<BookOpen />} title={o.emptyTitle}>
            {o.emptyBody}
          </EmptyState>
        </Panel>
      ) : (
        explorer && (
          <div className="flex flex-col gap-6">
            <div className="grid gap-6 lg:grid-cols-[minmax(0,30rem)_1fr]">
              <div className="flex min-w-0 flex-col gap-3">
                <AnalysisBoard
                  id="opening-explorer"
                  fen={here.fen}
                  orientation={colour === 'black' ? 'black' : 'white'}
                  lastMove={here.lastUci}
                  best={null}
                  score={null}
                  interactive
                  onMove={onMove}
                />
                <div className="flex gap-1">
                  <Button variant="secondary" size="sm" aria-label={o.toStart} title={o.toStart} disabled={!line.length} onClick={() => go([])}>
                    <ChevronFirst aria-hidden className="size-4.5" />
                  </Button>
                  <Button variant="secondary" size="sm" aria-label={o.back} title={o.back} disabled={!line.length} onClick={() => go(line.slice(0, -1))}>
                    <ChevronLeft aria-hidden className="size-4.5" />
                  </Button>
                </div>
              </div>

              <div className="flex min-w-0 flex-col gap-4">
                <div aria-live="polite">
                  <p className="font-display text-lg font-semibold">
                    {name ? (
                      <>
                        <span className="mr-2 text-muted tabular-nums">{name.eco}</span>
                        {name.name}
                      </>
                    ) : (
                      o.startPosition
                    )}
                  </p>
                  <LineText line={line} onJump={go} />
                </div>

                <Panel title={o.movesTitle(node?.tally.games ?? 0)}>
                  {node && node.moves.length > 0 ? (
                    <table className="w-full text-[0.9375rem]">
                      <caption className="sr-only">{o.movesCaption}</caption>
                      <thead>
                        <tr className="text-left text-[0.8125rem] text-muted">
                          <th scope="col" className="px-5 py-2 font-medium">{o.move}</th>
                          <th scope="col" className="px-3 py-2 text-right font-medium">{o.games}</th>
                          <th scope="col" className="hidden w-2/5 px-3 py-2 font-medium sm:table-cell">{me ? o.resultsFor(me.realName.split(' ')[0]) : o.results}</th>
                          <th scope="col" className="px-5 py-2 text-right font-medium">{me ? o.score : o.whiteScore}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {node.moves.map((m) => (
                          <tr key={m.san} className="border-t border-line">
                            <td className="px-5 py-1.5">
                              <button
                                type="button"
                                onClick={() => go([...line, m.san])}
                                className="rounded-[6px] px-1.5 py-1 font-mono font-semibold whitespace-nowrap hover:bg-raised hover:text-accent"
                              >
                                {moveLabel(line.length, m.san)}
                              </button>
                            </td>
                            <td className="px-3 py-1.5 text-right tabular-nums">{m.tally.games}</td>
                            <td className="hidden px-3 py-1.5 sm:table-cell">
                              <ResultBar tally={m.tally} mine={!!me} />
                            </td>
                            <td className="px-5 py-1.5 text-right font-medium tabular-nums">{f.pct(score(m.tally))}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  ) : (
                    <p className="px-5 pb-5 text-[0.9375rem] text-muted">{line.length || here.key !== START_KEY ? o.noMoreGames : o.noMoves}</p>
                  )}
                </Panel>

                {line.length > 0 && node && node.games.length > 0 && (
                  <Panel title={o.gamesHere}>
                    <ul className="divide-y divide-line pb-2">
                      {node.games.slice(0, 8).map((g) => (
                        <li key={g.id}>
                          <Link to={`/games/${g.id}`} className="flex items-baseline justify-between gap-3 px-5 py-2.5 text-[0.9375rem] hover:bg-raised/60">
                            <span className="min-w-0 truncate">
                              {g.white.realName} – {g.black.realName}
                            </span>
                            <span className="shrink-0 text-muted tabular-nums">
                              {SCORE[g.result]}, {f.date(g.playedAt)}
                            </span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  </Panel>
                )}
              </div>
            </div>

            <Panel title={o.byName}>
              {explorer.openings.length === 0 ? (
                <p className="px-5 pb-5 text-[0.9375rem] text-muted">{o.noNames}</p>
              ) : (
                <table className="w-full text-[0.9375rem]">
                  <caption className="sr-only">{o.byName}</caption>
                  <thead>
                    <tr className="text-left text-[0.8125rem] text-muted">
                      <th scope="col" className="px-5 py-2 font-medium">{o.opening}</th>
                      <th scope="col" className="px-3 py-2 text-right font-medium">{o.games}</th>
                      <th scope="col" className="hidden w-1/3 px-3 py-2 font-medium sm:table-cell">{me ? o.resultsFor(me.realName.split(' ')[0]) : o.results}</th>
                      <th scope="col" className="px-5 py-2 text-right font-medium">{me ? o.score : o.whiteScore}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {explorer.openings.slice(0, 15).map((op) => (
                      <tr key={op.name} className="border-t border-line">
                        <td className="px-5 py-2">
                          {op.line.length > 0 ? (
                            <button type="button" onClick={() => go(op.line)} className="text-left hover:text-accent">
                              {op.eco && <span className="mr-2 text-muted tabular-nums">{op.eco}</span>}
                              {op.name}
                            </button>
                          ) : (
                            <span>{op.name}</span>
                          )}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums">{op.tally.games}</td>
                        <td className="hidden px-3 py-2 sm:table-cell">
                          <ResultBar tally={op.tally} mine={!!me} />
                        </td>
                        <td className="px-5 py-2 text-right font-medium tabular-nums">{f.pct(score(op.tally))}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <p className="px-5 pt-1 pb-4 text-[0.8125rem] text-faint">
                {o.coverage(explorer.withMoves, explorer.total)} {o.source}
              </p>
            </Panel>
          </div>
        )
      )}
    </>
  )
}

/** "1. e4 c5 2. Nf3"; each move jumps back to that point. */
function LineText({ line, onJump }: { line: string[]; onJump: (l: string[]) => void }) {
  const o = useI18n().t.openings
  if (!line.length) return <p className="text-sm text-muted">{o.pickMove}</p>
  return (
    <p className="mt-1 flex flex-wrap gap-x-1 font-mono text-sm text-muted" aria-label={o.line}>
      {line.map((san, i) => (
        <button key={i} type="button" onClick={() => onJump(line.slice(0, i + 1))} className="rounded px-0.5 hover:text-accent">
          {i % 2 === 0 ? `${i / 2 + 1}. ${san}` : san}
        </button>
      ))}
    </p>
  )
}

function moveLabel(ply: number, san: string) {
  const n = Math.floor(ply / 2) + 1
  return ply % 2 === 0 ? `${n}. ${san}` : `${n}… ${san}`
}

/** White wins, draws and Black wins (or, for one player: their wins, draws and losses) as one bar. */
function ResultBar({ tally, mine }: { tally: Tally; mine: boolean }) {
  const { t } = useI18n()
  const o = t.openings
  const wins = mine ? Math.round(tally.points - tally.draws / 2) : tally.whiteWins
  const losses = mine ? tally.games - wins - tally.draws : tally.blackWins
  const parts = [
    { n: wins, cls: mine ? 'bg-win' : 'bg-trim ring-1 ring-inset ring-faint/70' },
    { n: tally.draws, cls: 'bg-draw' },
    { n: losses, cls: mine ? 'bg-loss' : 'bg-roof' },
  ]
  return (
    <div
      role="img"
      aria-label={mine ? o.mineBar(wins, tally.draws, losses) : o.bar(tally.whiteWins, tally.draws, tally.blackWins)}
      className="flex h-2.5 gap-[2px] overflow-hidden rounded-full"
    >
      {parts.map((p, i) => p.n > 0 && <span key={i} className={cn('h-full', p.cls)} style={{ width: `${(p.n / tally.games) * 100}%` }} />)}
    </div>
  )
}

/** The SAN of a legal move from `fen`, queen for promotions; null if it isn't legal. */
function playMove(fen: string, from: string, to: string): string | null {
  try {
    return new Chess(fen).move({ from, to, promotion: 'q' }).san
  } catch {
    return null
  }
}
