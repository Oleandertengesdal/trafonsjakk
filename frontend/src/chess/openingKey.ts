/**
 * A short key for a position, for looking up its opening name: an FNV-1a hash (base 36) of the FEN's first
 * four fields (pieces, side to move, castling rights, en passant square), so the same position reached by
 * a different move order gets the same key, and move counters don't matter.
 */
export function positionKey(fen: string): string {
  const text = fen.split(' ').slice(0, 4).join(' ')
  let h = 0x811c9dc5
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return (h >>> 0).toString(36)
}
