import type { PlayerRef } from '../../api/types'
import { Select } from '../ui/Field'

export function PlayerSelect({
  players,
  value,
  onChange,
  exclude,
  placeholder,
  ...aria
}: {
  players: PlayerRef[]
  value: number | ''
  onChange: (id: number | '') => void
  exclude?: number | ''
  placeholder: string
  id?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}) {
  return (
    <Select value={value} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : '')} {...aria}>
      <option value="">{placeholder}</option>
      {players
        .filter((p) => p.id !== exclude)
        .map((p) => (
          <option key={p.id} value={p.id}>
            {`${p.realName} (${p.username}), ${p.rating}${p.provisional ? '?' : ''}`}
          </option>
        ))}
    </Select>
  )
}
