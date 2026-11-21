import { useQuery } from '@tanstack/react-query'
import { awardsApi } from '../../api/endpoints'
import type { Award } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { AwardIcon } from './AwardIcon'

/** Profile: every monthly award the player has won, newest first. Nothing at all if they have none. */
export function AwardBadges({ username }: { username: string }) {
  const { t, f } = useI18n()
  const a = t.awards
  const q = useQuery({ queryKey: ['awards', 'list', username], queryFn: () => awardsApi.list(username) })
  if (!q.data?.length) return null

  const detail = (w: Award) =>
    w.kind === 'PLAYER_OF_MONTH'
      ? a.badgeDetail(f.signedDecimal(w.overExpected), t.common.games(w.games))
      : w.kind === 'MOST_ACCURATE'
        ? a.badgeAccurateDetail(f.pct((w.accuracy ?? 0) / 100), t.common.games(w.games))
        : a.badgeActiveDetail(t.common.games(w.games))

  return (
    <ul aria-label={a.title} className="flex flex-wrap gap-2">
      {q.data.map((w) => (
        <li
          key={`${w.month}-${w.kind}`}
          title={detail(w)}
          className="inline-flex items-center gap-2 rounded-full border border-gold/45 bg-gold/10 py-1.5 pr-3.5 pl-2.5 text-sm"
        >
          <AwardIcon kind={w.kind} />
          <span className="font-medium">{a.kind[w.kind]}</span>
          <span className="text-muted">{f.monthYear(w.month)}</span>
          <span className="sr-only">. {detail(w)}</span>
        </li>
      ))}
    </ul>
  )
}
