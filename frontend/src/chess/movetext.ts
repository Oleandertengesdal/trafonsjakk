// Shows stored moves as a numbered list without loading chess.js (it isn't needed just to display).

export interface MovePair {
  n: number
  white: string
  black: string | null
}

const SAN = /^(?:[KQRBN][a-h]?[1-8]?x?[a-h][1-8]|[a-h](?:x[a-h])?[1-8](?:=[QRBN])?|O-O(?:-O)?)[+#]?$/

/** "1. e4 e5 2. Nf3" -> rows of white/black moves; null if the text isn't a plain move list. */
export function splitMovetext(text: string): MovePair[] | null {
  const moves = text
    .split(/\s+/)
    .filter((t) => t !== '' && !/^\d+\.+$/.test(t) && !['1-0', '0-1', '1/2-1/2', '½-½', '*'].includes(t))
  if (moves.length === 0 || !moves.every((m) => SAN.test(m))) return null
  const rows: MovePair[] = []
  for (let i = 0; i < moves.length; i += 2) rows.push({ n: i / 2 + 1, white: moves[i], black: moves[i + 1] ?? null })
  return rows
}
