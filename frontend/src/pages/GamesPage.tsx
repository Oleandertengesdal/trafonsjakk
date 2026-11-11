import { useSearchParams } from 'react-router'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Swords } from 'lucide-react'
import { gamesApi, usersApi } from '../api/endpoints'
import type { GameStatus } from '../api/types'
import { GameRow } from '../components/chess/GameRow'
import { PageHeader } from '../components/layout/PageHeader'
import { Button, ButtonLink } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Select } from '../components/ui/Field'
import { Panel } from '../components/ui/Panel'
import { SkeletonRows } from '../components/ui/Skeleton'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'

const PAGE_SIZE = 25
const STATUSES: GameStatus[] = ['CONFIRMED', 'PENDING', 'DISPUTED', 'VOIDED']

export function GamesPage() {
  const { t } = useI18n()
  const [params, setParams] = useSearchParams()
  const player = params.get('player') ?? ''
  const rawStatus = params.get('status') ?? ''
  const status = (STATUSES as string[]).includes(rawStatus) ? (rawStatus as GameStatus) : ''
  const rawPage = Number(params.get('page') ?? 0)
  const page = Number.isInteger(rawPage) && rawPage > 0 ? rawPage : 0

  const users = useQuery({ queryKey: ['users'], queryFn: usersApi.list })
  const games = useQuery({
    queryKey: ['games', { player, status, page }],
    queryFn: () => gamesApi.list({ player: player || undefined, status: status || undefined, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  const update = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v)
    else next.delete(k)
    if (k !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }
  const filtered = !!(player || status)

  return (
    <>
      <PageHeader title={t.games.title} description={t.games.description} />
      <div className="mb-4 grid gap-3 sm:grid-cols-[16rem_16rem]">
        <Select aria-label={t.games.filterPlayer} value={player} onChange={(e) => update('player', e.target.value)}>
          <option value="">{t.games.allPlayers}</option>
          {users.data?.map((u) => (
            <option key={u.id} value={u.username}>
              {u.realName}
            </option>
          ))}
        </Select>
        <Select aria-label={t.games.filterStatus} value={status} onChange={(e) => update('status', e.target.value)}>
          <option value="">{t.games.defaultStatuses}</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {t.gameStatus[s]}
            </option>
          ))}
        </Select>
      </div>

      <Panel>
        {games.isPending ? (
          <SkeletonRows rows={8} />
        ) : games.isError ? (
          <ErrorNotice error={games.error} onRetry={() => games.refetch()} />
        ) : games.data.content.length === 0 ? (
          <EmptyState
            icon={<Swords />}
            title={filtered ? t.games.noMatchTitle : t.games.emptyTitle}
            action={!filtered ? <ButtonLink to="/log">{t.games.logFirst}</ButtonLink> : undefined}
          >
            {filtered ? t.games.noMatchBody : t.games.emptyBody}
          </EmptyState>
        ) : (
          <>
            <ul className={cn('divide-y divide-line py-1 transition-opacity', games.isPlaceholderData && 'opacity-60')}>
              {games.data.content.map((g) => (
                <GameRow key={g.id} game={g} />
              ))}
            </ul>
            {games.data.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-line px-5 py-3 text-sm text-muted">
                <span>{t.games.page(games.data.page + 1, games.data.totalPages)}</span>
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => update('page', String(page - 1))}>
                    {t.games.newer}
                  </Button>
                  <Button variant="secondary" size="sm" disabled={page + 1 >= games.data.totalPages} onClick={() => update('page', String(page + 1))}>
                    {t.games.older}
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </Panel>
    </>
  )
}
