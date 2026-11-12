/**
 * Which year "Årets sjakk" is about right now: in December the year that's ending, in January the one that
 * just ended, and otherwise the current year so far. `featured` is true in December and January, when the
 * home page shows it.
 */
export function reviewYear(now = new Date()): { year: number; featured: boolean } {
  const month = now.getMonth()
  if (month === 0) return { year: now.getFullYear() - 1, featured: true }
  return { year: now.getFullYear(), featured: month === 11 }
}
