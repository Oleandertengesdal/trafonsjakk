/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Turns on error alerts (Sentry). Public: it ends up in the JS bundle, which is how Sentry DSNs work. */
  readonly VITE_SENTRY_DSN?: string
  readonly VITE_SENTRY_ENVIRONMENT?: string
}
