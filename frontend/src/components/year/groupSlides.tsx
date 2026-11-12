import { Link } from 'react-router'
import type { GroupYear } from '../../api/types'
import { notation, signed } from '../../i18n/format'
import { useI18n } from '../../i18n/I18nProvider'
import { Big, CardAvatar, CountUp, Line, Podium, RecordBar, StoryCard } from './StoryCard'
import type { Slide } from './StoryViewer'

/** The group's year: totals, the game of the year, the giant killer, the award winners and the rivalry. */
export function useGroupSlides(y: GroupYear): Slide[] {
  const { t, f } = useI18n()
  const c = t.year
  const footer = `Trafonsjakk ${y.year}`
  const slides: Slide[] = []

  if (y.games === 0) {
    slides.push({
      key: 'empty',
      label: c.groupEmpty(y.year),
      card: (
        <StoryCard tone="paper" kicker={c.title(y.year)} footer={footer}>
          <Big sentence>{c.groupEmpty(y.year)}</Big>
          <Line>{c.emptyBody}</Line>
        </StoryCard>
      ),
    })
    return slides
  }

  slides.push({
    key: 'intro',
    label: `${c.groupIntro} ${y.year}`,
    card: (
      <StoryCard tone="brick" kicker={c.groupIntro} footer={y.complete ? footer : c.soFar(y.year)}>
        <Big>{String(y.year)}</Big>
        <Line className="text-xl font-medium">{c.groupIntroBody(y.games, y.players)}</Line>
        <Line className="opacity-75">{y.complete ? c.introBody : c.introBodySoFar}</Line>
      </StoryCard>
    ),
  })

  if (y.gameOfTheYear) {
    const g = y.gameOfTheYear
    slides.push({
      key: 'game',
      label: `${c.gameOfYear}: ${g.game.white.realName} – ${g.game.black.realName}`,
      card: (
        <StoryCard tone="paper" kicker={c.gameOfYear} footer={footer}>
          <p className="text-center font-display text-2xl font-semibold">{g.game.white.realName}</p>
          <Big className="my-3">{notation[g.game.result]}</Big>
          <p className="text-center font-display text-2xl font-semibold">{g.game.black.realName}</p>
          <Line className="font-medium">{c.buzzBody(g.kudos, g.comments)}</Line>
          <Line className="opacity-75">{f.date(g.game.playedAt)}</Line>
          <p className="mt-5 text-center">
            <Link to={`/games/${g.game.id}`} className="font-medium underline underline-offset-4">
              {c.seeGame}
            </Link>
          </p>
        </StoryCard>
      ),
    })
  }

  if (y.giantKiller) {
    const u = y.giantKiller
    slides.push({
      key: 'giant',
      label: `${c.giantKiller}: ${u.winner.realName}`,
      card: (
        <StoryCard tone="green" kicker={c.giantKiller} footer={footer}>
          <Big>{f.pct(u.expected)}</Big>
          <Line className="text-xl font-medium">{c.giantKillerBody(u.winner.realName, u.loser.realName, u.winnerRating, u.loserRating)}</Line>
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

  const leader = y.awardLeaders[0]
  if (leader && leader.playerOfMonth > 0) {
    slides.push({
      key: 'awards',
      label: `${c.awardLeader}: ${leader.player.realName}`,
      card: (
        <StoryCard tone="brass" kicker={c.awardLeader} footer={footer}>
          <div className="flex justify-center">
            <CardAvatar player={leader.player} size="lg" />
          </div>
          <Big className="mt-5">{leader.player.realName}</Big>
          <Line className="text-xl font-medium">{c.awardLeaderBody(leader.playerOfMonth)}</Line>
          {leader.mostActive > 0 && <Line>{c.awardActive(leader.mostActive)}</Line>}
          <Podium rows={y.awardLeaders.slice(1, 4).filter((l) => l.playerOfMonth > 0).map((l) => [l.player.realName, `${l.playerOfMonth}×`])} />
        </StoryCard>
      ),
    })
  }

  if (y.mostGames.length > 0) {
    const top = y.mostGames[0]
    slides.push({
      key: 'games',
      label: `${c.mostGames}: ${top.player.realName}`,
      card: (
        <StoryCard tone="slate" kicker={c.mostGames} footer={footer}>
          <Big>
            <CountUp value={top.games} />
          </Big>
          <Line className="text-xl font-medium">{top.player.realName}</Line>
          <Podium rows={y.mostGames.slice(1).map((r) => [r.player.realName, String(r.games)])} />
        </StoryCard>
      ),
    })
  }

  if (y.climbers.length > 0) {
    const top = y.climbers[0]
    slides.push({
      key: 'climbers',
      label: `${c.climbers}: ${top.player.realName}`,
      card: (
        <StoryCard tone="paper" kicker={c.climbers} footer={footer}>
          <Big>{signed(top.change)}</Big>
          <Line className="text-xl font-medium">{top.player.realName}</Line>
          <Line className="opacity-75">{c.climberBody(top.start, top.end)}</Line>
          <Podium rows={y.climbers.slice(1).map((r) => [r.player.realName, signed(r.change)])} />
        </StoryCard>
      ),
    })
  }

  if (y.longestWinStreak && y.longestWinStreak.length >= 2) {
    const s = y.longestWinStreak
    slides.push({
      key: 'streak',
      label: `${c.groupStreak}: ${s.player.realName}`,
      card: (
        <StoryCard tone="brick" kicker={c.groupStreak} footer={footer}>
          <Big>
            <CountUp value={s.length} />
          </Big>
          <Line className="text-xl font-medium">
            {s.player.realName}, {c.streakUnit(s.length)}
          </Line>
          <Line className="opacity-75">{c.fromTo(f.date(s.from), f.date(s.to))}</Line>
        </StoryCard>
      ),
    })
  }

  if (y.topRivalry) {
    const r = y.topRivalry
    slides.push({
      key: 'rivalry',
      label: `${c.rivalry}: ${r.a.realName} – ${r.b.realName}`,
      card: (
        <StoryCard tone="slate" kicker={c.rivalry} footer={footer}>
          <div className="flex items-center justify-center gap-3">
            <CardAvatar player={r.a} size="md" />
            <span className="font-display text-xl opacity-70">{t.common.vs}</span>
            <CardAvatar player={r.b} size="md" />
          </div>
          <p className="mt-5 text-center font-display text-[1.75rem] leading-tight font-bold">
            {r.a.realName}
            <br />
            {r.b.realName}
          </p>
          <Line className="text-xl font-medium">{c.rivalryBody(r.games)}</Line>
          <Line className="font-display text-[2.25rem] font-semibold">{c.rivalRecord(r.aWins, r.bWins, r.draws)}</Line>
          <RecordBar w={r.aWins} d={r.draws} l={r.bWins} />
        </StoryCard>
      ),
    })
  }

  const facts: string[] = []
  if (y.busiestMonth) facts.push(capitalise(c.busiestMonth(f.month(y.busiestMonth.month), y.busiestMonth.games)))
  if (y.busiestWeekday) facts.push(c.busiestDay(f.weekdays(y.busiestWeekday.day)))
  facts.push(c.drawShare(f.pct(y.draws / y.games)))
  facts.push(c.whiteScore(f.pct((y.whiteWins + y.draws / 2) / y.games)))
  if (y.gamesWithMoves > 0) facts.push(c.movesRecorded(y.gamesWithMoves))
  if (y.kudos + y.comments > 0) facts.push(c.social(y.kudos, y.comments))
  slides.push({
    key: 'facts',
    label: c.facts,
    card: (
      <StoryCard tone="green" kicker={c.facts} footer={footer}>
        <Big>
          <CountUp value={y.games} />
        </Big>
        <Line className="text-xl font-medium">{c.gamesPlayed(y.games)}</Line>
        <ul className="mt-6 space-y-2.5 text-center text-[1rem] leading-snug">
          {facts.map((x) => (
            <li key={x}>{x}</li>
          ))}
        </ul>
      </StoryCard>
    ),
  })
  return slides
}

const capitalise = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)
