import React, { useEffect, useState } from 'react'
import ReactDOM from 'react-dom/client'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { ClientProvider } from './contexts/ClientContext'
import { AuthGate } from './components/auth/AuthGate'
import { AppSidebar } from './components/common/AppSidebar'
import { Toast } from './components/common/Toast'
import { LoadingSpinner } from './components/common/LoadingSpinner'
import { useRouterStore } from './store/routerStore'
import { useQueuePolling } from './hooks/useQueuePolling'

import { HomePage } from './pages/HomePage'
import { CreateItineraryPage } from './pages/CreateItineraryPage'
import { MyItinerariesPage } from './pages/MyItinerariesPage'
import { ItineraryTrackingPage } from './pages/ItineraryTrackingPage'
import { ItineraryDetailPage } from './pages/ItineraryDetailPage'
import { AdminDashboard } from './pages/AdminDashboard'
import { SessionManagementPage } from './pages/SessionManagementPage'
import { SagaDetailPage } from './pages/SagaDetailPage'

import './index.css'

const BASE_URL = import.meta.env.VITE_API_URL ?? ''

function PageContent() {
  const { route } = useRouterStore()

  switch (route.page) {
    case 'home':
      return <HomePage />
    case 'create':
      return <CreateItineraryPage />
    case 'my-itineraries':
      return <MyItinerariesPage />
    case 'itinerary-tracking':
      return <ItineraryTrackingPage id={route.id} accessToken={route.accessToken} />
    case 'itinerary-detail':
      return <ItineraryDetailPage id={route.id} accessToken={route.accessToken} />
    case 'admin-dashboard':
      return <AdminDashboard />
    case 'session-management':
      return <SessionManagementPage />
    case 'saga-detail':
      return <SagaDetailPage itineraryId={route.itineraryId} />
  }
}

function AppShell() {
  const { isAuthenticated, loading, createAnonymous, logout } = useAuth()
  const [authModalOpen, setAuthModalOpen] = useState(false)

  // Auto-create anonymous session — no gate shown on first visit
  useEffect(() => {
    if (!loading && !isAuthenticated) {
      createAnonymous().catch(() => setAuthModalOpen(true))
    }
  }, [loading, isAuthenticated])

  // Global queue polling every 5 s (ONLY when authenticated)
  useQueuePolling({ intervalMs: 5000, enabled: isAuthenticated })

  // When a request returns 401 (expired/invalid token), clear state and re-auth
  useEffect(() => {
    const handleUnauthorized = () => {
      logout()
      // isAuthenticated will flip to false, triggering the effect above
    }
    window.addEventListener('auth:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized)
  }, [logout])

  if (loading) return <LoadingSpinner fullScreen />

  if (!isAuthenticated) return <AuthGate />

  return (
    <div className="flex min-h-screen bg-background text-foreground overflow-x-hidden overflow-x-contain">
      <AppSidebar onSignIn={() => setAuthModalOpen(true)}>
        <PageContent />
      </AppSidebar>

      {authModalOpen && (
        <div className="modal-backdrop" onClick={() => setAuthModalOpen(false)}>
          <div
            className="modal-panel animate-scale-in"
            onClick={(e) => e.stopPropagation()}
          >
            <AuthGate embedded onDone={() => setAuthModalOpen(false)} />
          </div>
        </div>
      )}

      <Toast />
    </div>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ClientProvider baseURL={BASE_URL}>
      <AuthProvider baseURL={BASE_URL}>
        <AppShell />
      </AuthProvider>
    </ClientProvider>
  </React.StrictMode>
)
