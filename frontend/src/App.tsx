import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './api/queryClient'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAdmin, RequireAuth } from './auth/RequireAuth'
import { AppShell } from './components/layout/AppShell'
import { Skeleton } from './components/ui/Skeleton'
import { ToastProvider } from './components/ui/Toast'
import { I18nProvider } from './i18n/I18nProvider'
import { GamesPage } from './pages/GamesPage'
import { HomePage } from './pages/HomePage'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { LogGamePage } from './pages/LogGamePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { TournamentsPage } from './pages/TournamentsPage'

const ProfilePage = lazy(() => import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage })))
const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })))
const TournamentDetailPage = lazy(() => import('./pages/TournamentDetailPage').then((m) => ({ default: m.TournamentDetailPage })))
const TournamentNewPage = lazy(() => import('./pages/TournamentNewPage').then((m) => ({ default: m.TournamentNewPage })))
const InvitesPage = lazy(() => import('./pages/InvitesPage').then((m) => ({ default: m.InvitesPage })))
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((m) => ({ default: m.SettingsPage })))
const GameDetailPage = lazy(() => import('./pages/GameDetailPage').then((m) => ({ default: m.GameDetailPage })))

const page = (node: ReactNode) => <Suspense fallback={<Skeleton className="h-96 w-full rounded-panel" />}>{node}</Suspense>

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <AuthProvider>
            <BrowserRouter>
              <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route
                  element={
                    <RequireAuth>
                      <AppShell />
                    </RequireAuth>
                  }
                >
                  <Route index element={<HomePage />} />
                  <Route path="log" element={<LogGamePage />} />
                  <Route path="games" element={<GamesPage />} />
                  <Route path="games/:id" element={page(<GameDetailPage />)} />
                  <Route path="leaderboard" element={<LeaderboardPage />} />
                  <Route path="players/:username" element={page(<ProfilePage />)} />
                  <Route path="tournaments" element={<TournamentsPage />} />
                  <Route path="tournaments/new" element={page(<TournamentNewPage />)} />
                  <Route path="tournaments/:id" element={page(<TournamentDetailPage />)} />
                  <Route path="invites" element={page(<InvitesPage />)} />
                  <Route path="settings" element={page(<SettingsPage />)} />
                  <Route path="admin" element={<RequireAdmin>{page(<AdminPage />)}</RequireAdmin>} />
                  <Route path="*" element={<NotFoundPage />} />
                </Route>
              </Routes>
            </BrowserRouter>
          </AuthProvider>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>
  )
}
