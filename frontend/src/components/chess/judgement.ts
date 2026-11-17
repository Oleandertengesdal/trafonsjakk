import type { Judgement } from '../../chess/analysis'

/** The colour of a move's mark (??, ?, ?!) wherever it's shown. */
export const glyphColour: Record<Judgement, string> = { blunder: 'text-loss', mistake: 'text-gold', inaccuracy: 'text-muted' }
