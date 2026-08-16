import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './lib/auth'
import { ToastProvider } from './lib/toast'
import { Layout } from './components/Layout'
import { FullScreenSpinner } from './components/ui/Spinner'

// ── Lazy-loaded pages ─────────────────────────────────────────
const Login = lazy(() => import('./pages/Login'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Status = lazy(() => import('./pages/Status'))
const StatusPerformance = lazy(() => import('./pages/StatusPerformance'))
const StatusTrends = lazy(() => import('./pages/StatusTrends'))
const StatusConnection = lazy(() => import('./pages/StatusConnection'))
const StatusVersions = lazy(() => import('./pages/StatusVersions'))
const Players = lazy(() => import('./pages/Players'))
const PlayerProfile = lazy(() => import('./pages/PlayerProfile'))
const Bans = lazy(() => import('./pages/Bans'))
const Whitelist = lazy(() => import('./pages/Whitelist'))
const PlayerActions = lazy(() => import('./pages/PlayerActions'))
const Messages = lazy(() => import('./pages/Messages'))
const MessagesSchedule = lazy(() => import('./pages/MessagesSchedule'))
const MessagesTitle = lazy(() => import('./pages/MessagesTitle'))
const MessagesBossbar = lazy(() => import('./pages/MessagesBossbar'))
const MessagesFilter = lazy(() => import('./pages/MessagesFilter'))
const MessagesMute = lazy(() => import('./pages/MessagesMute'))
const WorldTime = lazy(() => import('./pages/WorldTime'))
const WorldDifficulty = lazy(() => import('./pages/WorldDifficulty'))
const WorldBorder = lazy(() => import('./pages/WorldBorder'))
const WorldSpawn = lazy(() => import('./pages/WorldSpawn'))
const WorldDimensions = lazy(() => import('./pages/WorldDimensions'))
const Gamerules = lazy(() => import('./pages/Gamerules'))
const Entities = lazy(() => import('./pages/Entities'))
const EntitiesCleanup = lazy(() => import('./pages/EntitiesCleanup'))
const ItemsGive = lazy(() => import('./pages/ItemsGive'))
const ItemsInventory = lazy(() => import('./pages/ItemsInventory'))
const ItemsClear = lazy(() => import('./pages/ItemsClear'))
const Search = lazy(() => import('./pages/Search'))
const Admins = lazy(() => import('./pages/Admins'))
const SecuritySudo = lazy(() => import('./pages/SecuritySudo'))
const SecurityAccounts = lazy(() => import('./pages/SecurityAccounts'))
const Audit = lazy(() => import('./pages/Audit'))
const Alerts = lazy(() => import('./pages/Alerts'))
const SecurityRisk = lazy(() => import('./pages/SecurityRisk'))
const SecurityApprovals = lazy(() => import('./pages/SecurityApprovals'))
const SecurityIntegrity = lazy(() => import('./pages/SecurityIntegrity'))
const SecurityWebhooks = lazy(() => import('./pages/SecurityWebhooks'))
const SecurityConfigHistory = lazy(() => import('./pages/SecurityConfigHistory'))
const SecuritySnapshots = lazy(() => import('./pages/SecuritySnapshots'))
const SecurityEconomy = lazy(() => import('./pages/SecurityEconomy'))
const SecurityModSources = lazy(() => import('./pages/SecurityModSources'))
const SecurityMfa = lazy(() => import('./pages/SecurityMfa'))
const SecurityLockdown = lazy(() => import('./pages/SecurityLockdown'))
const Center = lazy(() => import('./pages/Center'))
const AccessOps = lazy(() => import('./pages/AccessOps'))
const AccessSpectator = lazy(() => import('./pages/AccessSpectator'))
const Scoreboard = lazy(() => import('./pages/Scoreboard'))
const ScoreboardTeams = lazy(() => import('./pages/ScoreboardTeams'))
const Console = lazy(() => import('./pages/Console'))
const Remote = lazy(() => import('./pages/Remote'))
const SystemSave = lazy(() => import('./pages/SystemSave'))
const SystemRetention = lazy(() => import('./pages/SystemRetention'))
const SystemReload = lazy(() => import('./pages/SystemReload'))
const SystemStop = lazy(() => import('./pages/SystemStop'))
const SystemMaintenance = lazy(() => import('./pages/SystemMaintenance'))
const SystemSchedules = lazy(() => import('./pages/SystemSchedules'))
const Automation = lazy(() => import('./pages/Automation'))
const AutomationDetail = lazy(() => import('./pages/AutomationDetail'))
const SystemRuntime = lazy(() => import('./pages/SystemRuntime'))
const DiagLogs = lazy(() => import('./pages/DiagLogs'))
const DiagActions = lazy(() => import('./pages/DiagActions'))
const DiagMovements = lazy(() => import('./pages/DiagMovements'))
const Trace = lazy(() => import('./pages/Trace'))
const Incident = lazy(() => import('./pages/Incident'))

function Protected({ children, admin }: { children: React.ReactNode; admin?: boolean }) {
  const { user, loading } = useAuth()
  if (loading) return <FullScreenSpinner />
  if (!user) return <Navigate to="/login" replace />
  if (admin && user.role !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/login/mfa" element={<Login />} />
      <Route
        path="/*"
        element={
          <Protected>
            <Layout>
              <Suspense fallback={<FullScreenSpinner />}>
                <Routes>
                  <Route path="/" element={<Dashboard />} />
                  <Route path="/status" element={<Status />} />
                  <Route path="/status/performance" element={<StatusPerformance />} />
                  <Route path="/status/trends" element={<StatusTrends />} />
                  <Route path="/status/connection" element={<StatusConnection />} />
                  <Route path="/status/versions" element={<StatusVersions />} />
                  <Route path="/players" element={<Players />} />
                  <Route path="/players/profile" element={<PlayerProfile />} />
                  <Route path="/players/actions" element={<PlayerActions />} />
                  <Route path="/bans" element={<Bans />} />
                  <Route path="/whitelist" element={<Whitelist />} />
                  <Route path="/messages" element={<Messages />} />
                  <Route path="/messages/schedule" element={<MessagesSchedule />} />
                  <Route path="/messages/title" element={<MessagesTitle />} />
                  <Route path="/messages/bossbar" element={<MessagesBossbar />} />
                  <Route path="/messages/filter" element={<MessagesFilter />} />
                  <Route path="/messages/mute" element={<MessagesMute />} />
                  <Route path="/world/time" element={<WorldTime />} />
                  <Route path="/world/difficulty" element={<WorldDifficulty />} />
                  <Route path="/world/border" element={<WorldBorder />} />
                  <Route path="/world/spawn" element={<WorldSpawn />} />
                  <Route path="/world/dimensions" element={<WorldDimensions />} />
                  <Route path="/gamerules" element={<Gamerules />} />
                  <Route path="/entities" element={<Entities />} />
                  <Route path="/entities/cleanup" element={<EntitiesCleanup />} />
                  <Route path="/items/give" element={<ItemsGive />} />
                  <Route path="/items/inventory" element={<ItemsInventory />} />
                  <Route path="/items/clear" element={<ItemsClear />} />
                  <Route path="/search" element={<Search />} />
                  <Route path="/admins" element={<Protected admin><Admins /></Protected>} />
                  <Route path="/security/sudo" element={<SecuritySudo />} />
                  <Route path="/security/accounts" element={<SecurityAccounts />} />
                  <Route path="/audit" element={<Audit />} />
                  <Route path="/alerts" element={<Alerts />} />
                  <Route path="/security/risk" element={<SecurityRisk />} />
                  <Route path="/security/approvals" element={<Protected admin><SecurityApprovals /></Protected>} />
                  <Route path="/security/integrity" element={<SecurityIntegrity />} />
                  <Route path="/security/webhooks" element={<SecurityWebhooks />} />
                  <Route path="/security/config-history" element={<Protected admin><SecurityConfigHistory /></Protected>} />
                  <Route path="/security/snapshots" element={<Protected admin><SecuritySnapshots /></Protected>} />
                  <Route path="/security/economy" element={<SecurityEconomy />} />
                  <Route path="/security/mod-sources" element={<SecurityModSources />} />
                  <Route path="/security/mfa" element={<Protected admin><SecurityMfa /></Protected>} />
                  <Route path="/security/lockdown" element={<Protected admin><SecurityLockdown /></Protected>} />
                  <Route path="/center" element={<Center />} />
                  <Route path="/access/ops" element={<AccessOps />} />
                  <Route path="/access/spectator" element={<AccessSpectator />} />
                  <Route path="/scoreboard" element={<Scoreboard />} />
                  <Route path="/scoreboard/teams" element={<ScoreboardTeams />} />
                  <Route path="/console" element={<Protected admin><Console /></Protected>} />
                  <Route path="/remote" element={<Remote />} />
                  <Route path="/system/save" element={<Protected admin><SystemSave /></Protected>} />
                  <Route path="/system/retention" element={<Protected admin><SystemRetention /></Protected>} />
                  <Route path="/system/reload" element={<Protected admin><SystemReload /></Protected>} />
                  <Route path="/system/stop" element={<Protected admin><SystemStop /></Protected>} />
                  <Route path="/system/maintenance" element={<Protected admin><SystemMaintenance /></Protected>} />
                  <Route path="/system/schedules" element={<Protected admin><SystemSchedules /></Protected>} />
                  <Route path="/automation" element={<Automation />} />
                  <Route path="/automation/:id" element={<AutomationDetail />} />
                  <Route path="/system/runtime" element={<SystemRuntime />} />
                  <Route path="/diag/logs" element={<DiagLogs />} />
                  <Route path="/diag/actions" element={<DiagActions />} />
                  <Route path="/diag/movements" element={<DiagMovements />} />
                  <Route path="/trace/:traceId" element={<Trace />} />
                  <Route path="/incident/:eventId" element={<Incident />} />
                </Routes>
              </Suspense>
            </Layout>
          </Protected>
        }
      />
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
