# Stockfish engine (third-party, GPLv3)

These files are **Stockfish.js 18.0.8** ("lite, single-threaded" build), unmodified, from the
[`stockfish` npm package](https://www.npmjs.com/package/stockfish/v/18.0.8):

- `stockfish-18-lite-single.js`
- `stockfish-18-lite-single.wasm`

Stockfish.js (c) Chess.com, LLC, based on [Stockfish](https://github.com/official-stockfish/Stockfish) by the Stockfish
developers. Licensed under the GNU General Public License v3; see `COPYING.txt`.
Source code: <https://github.com/nmrugg/stockfish.js> (tag `v18.0.8`) and <https://github.com/official-stockfish/Stockfish>.

The engine runs as a separate program in a Web Worker in the browser; Trafonsjakk talks to it over the UCI text protocol
(`src/chess/engine.ts`). It is served as-is from this folder and is not part of the app's own bundle.

To update: `npm pack stockfish@<version>`, copy the two `lite-single` files from `bin/` here, and update the version above
and `ENGINE_URL` in `src/chess/engine.ts`.
