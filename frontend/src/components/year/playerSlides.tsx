import { Link } from 'react-router'
import { cn } from '../../lib/cn'
import type { PlayerYear } from '../../api/types'
import { signed } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'
import { Sparkline } from './Sparkline'
import { Big, CardAvatar, CountUp, Line, RecordBar, StoryCard } from './StoryCard'
import type { Slide } from './StoryViewer'

const first = (name: string) => name.split(' ')[0]

/** The cards for one player's year. Cards without data (no openings recorded, no awards…) are left out. */
export function usePlayerSlides(y: PlayerYear, isMe: boolean): Slide[] {
  const { t, f } = useI18n()
  const c = t.year
  const name = first(y.player.realName)
  const footer = `Trafonsjakk ${y.year}`
  const slides: Slide[] = []

  if (y.games === 0) {
    const title = isMe ? c.emptyYou(y.year) : c.emptyPlayer(name, y.year)
    slides.push({
      key: 'empty',
      label: title,
      card: (
        <StoryCard tone="paper" kicker={c.title(y.year)} footer={footer}>
          <Big sentence>{title}</Big>
          <Line>{c.emptyBody}</Line>
        </StoryCard>
      ),
    })
    return slides
  }

  const intro = isMe ? c.introYou : c.introPlayer(name)
  slides.push({
    key: 'intro',
    label: `${intro} ${y.year}`,
    card: (
      <StoryCard tone="brick" kicker={c.title(y.year)} footer={footer}>
        <div className="flex justify-center">
          <CardAvatar player={y.player} size="lg" />
        </div>
        <Big className="mt-6">{intro}</Big>
        <Line>{y.complete ? c.introBody : c.introBodySoFar}</Line>
      </StoryCard>
    ),
  })

  slides.push({
    key: 'games',
    label: `${y.games} ${c.gamesPlayed(y.games)}`,
    card: (
      <StoryCard tone="paper" kicker={c.games} footer={footer}>
        <Big>
          <CountUp value={y.games} />
        </Big>
        <Line className="text-xl font-medium">{c.gamesPlayed(y.games)}</Line>
        <Line>{c.record(y.wins, y.draws, y.losses)}</Line>
        {y.rankByGames && y.players > 1 && (
          <Line className="opacity-75">{y.rankByGames === 1 ? (isMe ? c.mostGamesYou : c.mostGamesPlayer(name)) : c.rankByGames(y.rankByGames, y.players)}</Line>
        )}
      </StoryCard>
    ),
  })

  if (y.ratingStart !== null && y.ratingEnd !== null) {
    const change = y.ratingEnd - y.ratingStart
    slides.push({
      key: 'rating',
      label: `${c.rating}: ${signed(change)}`,
      card: (
        <StoryCard tone="slate" kicker={c.rating} footer={footer}>
          <Big>{signed(change)}</Big>
          <Line className="text-xl font-medium">{c.ratingFromTo(y.ratingStart, y.ratingEnd)}</Line>
          <Sparkline start={y.ratingStart} points={y.ratingByMonth.map((m) => m.rating)} className="mx-auto mt-6 w-4/5" />
          {y.ratingPeak !== null && <Line className="opacity-75">{c.ratingPeak(y.ratingPeak)}</Line>}
        </StoryCard>
      ),
    })
  }

  if (y.bestMonth) {
    const m = y.bestMonth
    slides.push({
      key: 'month',
      label: `${c.bestMonth}: ${f.month(m.month)}`,
      card: (
        <StoryCard tone="brass" kicker={c.bestMonth} footer={footer}>
          <Big>{f.month(m.month)}</Big>
          <Line className="text-xl font-medium">{c.monthRecord(m.wins, m.draws, m.losses, m.games)}</Line>
          <Line>
            {c.overExpected(f.signedDecimal(m.overExpected))}, {c.rating.toLowerCase()} {signed(m.ratingChange)}
          </Line>
        </StoryCard>
      ),
    })
  }

  if (y.biggestUpset) {
    const u = y.biggestUpset
    slides.push({
      key: 'upset',
      label: `${c.biggestUpset}: ${f.pct(u.expected)}`,
      card: (
        <StoryCard tone="green" kicker={c.biggestUpset} footer={footer}>
          <Big>{f.pct(u.expected)}</Big>
          <Line className="text-xl font-medium">
            {isMe ? c.upsetYou(u.loser.realName, u.loserRating) : c.upsetPlayer(name, u.loser.realName, u.loserRating)}
          </Line>
          <Line className="opacity-75">{f.date(u.playedAt)}</Line>
          <p className="mt-5 text-center">
            <Link to={`/games/${u.gameId}`} className="font-medium underline underline-offset-4">
              {c.seeGame}
            </Link>
          </p>
        </StoryCard>
      ),
    })
  }

  if (y.rival) {
    const r = y.rival
    slides.push({
      key: 'rival',
      label: `${c.rival}: ${r.opponent.realName}`,
      card: (
        <StoryCard tone="brick" kicker={c.rival} footer={footer}>
          <div className="flex justify-center">
            <CardAvatar player={r.opponent} size="lg" />
          </div>
          <Big className="mt-5">{r.opponent.realName}</Big>
          <Line className="text-xl font-medium">{c.rivalGames(r.games)}</Line>
          <Line className="font-display text-[2.25rem] font-semibold">{c.rivalRecord(r.wins, r.losses, r.draws)}</Line>
          <RecordBar w={r.wins} d={r.draws} l={r.losses} />
        </StoryCard>
      ),
    })
  }

  if (y.longestWinStreak && y.longestWinStreak.length >= 2) {
    const s = y.longestWinStreak
    slides.push({
      key: 'streak',
      label: `${c.streak}: ${s.length}`,
      card: (
        <StoryCard tone="paper" kicker={c.streak} footer={footer}>
          <Big>
            <CountUp value={s.length} />
          </Big>
          <Line className="text-xl font-medium">{c.streakUnit(s.length)}</Line>
          <Line className="opacity-75">{c.fromTo(f.date(s.from), f.date(s.to))}</Line>
        </StoryCard>
      ),
    })
  }

  if (y.favoriteOpening) {
    const o = y.favoriteOpening
    slides.push({
      key: 'opening',
      label: `${c.opening}: ${o.name}`,
      card: (
        <StoryCard tone="slate" kicker={c.opening} footer={footer}>
          <Big>{o.name}</Big>
          <Line className="text-xl font-medium">{c.openingBody(o.games, f.pct(o.scorePct))}</Line>
        </StoryCard>
      ),
    })
  }

  if (y.awards.length > 0) {
    const pom = y.awards.filter((a) => a.kind === 'PLAYER_OF_MONTH').map((a) => f.month(a.month))
    const active = y.awards.filter((a) => a.kind === 'MOST_ACTIVE').map((a) => f.month(a.month))
    slides.push({
      key: 'awards',
      label: `${c.awards}: ${y.awards.length}`,
      card: (
        <StoryCard tone="brass" kicker={c.awards} footer={footer}>
          <Big>{c.awardTimes(y.awards.length)}</Big>
          {pom.length > 0 && <Line className="text-xl font-medium">{c.awardMonths(t.awards.kind.PLAYER_OF_MONTH, pom.join(', '))}</Line>}
          {active.length > 0 && <Line>{c.awardMonths(t.awards.kind.MOST_ACTIVE, active.join(', '))}</Line>}
        </StoryCard>
      ),
    })
  }

  if (y.busiestWeekday && y.busiestWeekday.games >= 2) {
    const w = y.busiestWeekday
    slides.push({
      key: 'weekday',
      label: `${c.weekday}: ${f.weekday(w.day)}`,
      card: (
        <StoryCard tone="green" kicker={c.weekday} footer={footer}>
          <Big>{f.weekday(w.day)}</Big>
          <Line className="text-xl font-medium">{c.weekdayBody(w.games, y.games, f.weekdays(w.day))}</Line>
        </StoryCard>
      ),
    })
  }

  if (y.mostKudos) {
    const k = y.mostKudos
    const opponent = k.game.white.id === y.player.id ? k.game.black : k.game.white
    slides.push({
      key: 'buzz',
      label: `${c.buzz}: ${opponent.realName}`,
      card: (
        <StoryCard tone="paper" kicker={c.buzz} footer={footer}>
          <Big>{c.against(opponent.realName)}</Big>
          <Line className="text-xl font-medium">{c.buzzBody(k.kudos, k.comments)}</Line>
          <Line className="opacity-75">{f.date(k.game.playedAt)}</Line>
          <p className="mt-5 text-center">
            <Link to={`/games/${k.game.id}`} className="font-medium underline underline-offset-4">
              {c.seeGame}
            </Link>
          </p>
        </StoryCard>
      ),
    })
  }

  const stats: [string, string][] = [
    [c.sGames, String(y.games)],
    [c.sWins, String(y.wins)],
  ]
  if (y.ratingStart !== null && y.ratingEnd !== null) stats.push([c.sRating, signed(y.ratingEnd - y.ratingStart)])
  if (y.bestMonth) stats.push([c.sBestMonth, f.month(y.bestMonth.month)])
  if (y.rival) stats.push([c.sRival, first(y.rival.opponent.realName)])
  if (y.longestWinStreak && y.longestWinStreak.length >= 2) stats.push([c.sStreak, String(y.longestWinStreak.length)])
  slides.push({
    key: 'summary',
    label: c.summary,
    card: (
      <StoryCard tone="brick" kicker={c.summary} footer={footer}>
        <div className="flex items-center justify-center gap-3">
          <CardAvatar player={y.player} size="md" />
          <p className="font-display text-2xl font-semibold">{c.summaryTitle(y.player.realName, y.year)}</p>
        </div>
        <dl className="mt-7 grid grid-cols-2 gap-x-4 gap-y-5">
          {stats.map(([label, value]) => (
            <div key={label} className="text-center">
              <dt className="text-[0.875rem] opacity-80">{label}</dt>
              <dd className={cn('mt-0.5 font-display leading-tight font-bold', value.length > 6 ? 'text-[7cqw]' : 'text-[9cqw]')}>{value}</dd>
            </div>
          ))}
        </dl>
      </StoryCard>
    ),
  })
  return slides
}
