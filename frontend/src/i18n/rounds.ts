import type { TournamentFormat } from '../api/types'
import type { Dict } from './en'

/** Round names are computed client-side so they follow the chosen language. */
export function roundName(t: Dict, format: TournamentFormat, round: number, totalRounds: number): string {
  if (format === 'SINGLE_ELIMINATION' && totalRounds > 0) {
    const fromEnd = totalRounds - round
    if (fromEnd === 0) return t.round.final
    if (fromEnd === 1) return t.round.semifinals
    if (fromEnd === 2) return t.round.quarterfinals
  }
  return t.round.numbered(round)
}
