import { Component, type ErrorInfo, type ReactNode } from 'react'
import { newErrorRef, reportCrash } from '../../lib/errorReporting'
import { isStaleChunkError, reloadForUpdate } from '../../lib/staleChunks'

type Props = {
  children: ReactNode
  /** When this changes (e.g. the route), a crashed page gets another chance. */
  resetKey?: unknown
  fallback: (crash: { error: unknown; reference: string }) => ReactNode
}
type State = { error: unknown; reference: string | null }

/**
 * Catches a crash while rendering, so one broken page doesn't blank the whole app. Gives the crash a
 * short reference, reports it (when error alerts are on), and reloads once by itself when the cause
 * is a new deploy.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null, reference: null }

  static getDerivedStateFromError(error: unknown): State {
    return { error, reference: newErrorRef() }
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    if (isStaleChunkError(error) && reloadForUpdate()) return
    reportCrash(error, this.state.reference ?? '', info.componentStack ?? undefined)
  }

  componentDidUpdate(prev: Props) {
    if (this.state.reference && prev.resetKey !== this.props.resetKey) this.setState({ error: null, reference: null })
  }

  render() {
    if (this.state.reference) return this.props.fallback({ error: this.state.error, reference: this.state.reference })
    return this.props.children
  }
}
