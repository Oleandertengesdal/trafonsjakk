// Builds src/chess/openings.json, the opening names the explorer and the game page use, from lichess's
// chess-openings data set (https://github.com/lichess-org/chess-openings, CC0: public domain).
//
//   curl -O https://raw.githubusercontent.com/lichess-org/chess-openings/master/{a,b,c,d,e}.tsv
//   node --experimental-strip-types scripts/build-openings.mjs a.tsv b.tsv c.tsv d.tsv e.tsv
//
// Each line is "eco<TAB>name<TAB>pgn". The output maps a short hash of the position each line ends in
// (pieces, side to move, castling, en passant; so transpositions find their name) to [eco, name].
import { readFileSync, writeFileSync } from 'node:fs'
import { Chess } from 'chess.js'
import { positionKey } from '../src/chess/openingKey.ts'

const out = {}
let lines = 0
for (const file of process.argv.slice(2)) {
  for (const line of readFileSync(file, 'utf8').split('\n').slice(1)) {
    const [eco, name, pgn] = line.split('\t')
    if (!pgn) continue
    const chess = new Chess()
    for (const token of pgn.split(/\s+/)) {
      if (/^\d+\.+$/.test(token) || !token) continue
      chess.move(token)
    }
    const key = positionKey(chess.fen())
    if (!(key in out)) out[key] = [eco, name]
    lines++
  }
}
writeFileSync(new URL('../src/chess/openings.json', import.meta.url), JSON.stringify(out))
console.log(`${lines} lines, ${Object.keys(out).length} positions`)
