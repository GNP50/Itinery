import React, { useEffect, useState } from 'react'
import { useClient } from '../contexts/ClientContext'
import { useAuth } from '../contexts/AuthContext'
import { useRouterStore } from '../store/routerStore'
import { SagaInstanceDto } from '../api/client'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import {
  ArrowLeft,
  CheckCircle,
  XCircle,
  Clock,
  AlertTriangle,
  RefreshCw,
} from 'lucide-react'

// ─── State badge ──────────────────────────────────────────────────────────

const STATE_STYLES: Record<string, { bg: string; icon: React.ReactNode }> = {
  INITIAL: { bg: 'bg-gray-500/15 text-gray-600 dark:text-gray-400', icon: <Clock className="h-3 w-3" /> },
  GEOCODING: { bg: 'bg-blue-500/15 text-blue-600 dark:text-blue-400', icon: <RefreshCw className="h-3 w-3 animate-spin" /> },
  ROUTING: { bg: 'bg-blue-500/15 text-blue-600 dark:text-blue-400', icon: <RefreshCw className="h-3 w-3 animate-spin" /> },
  AI_ENRICHMENT: { bg: 'bg-purple-500/15 text-purple-600 dark:text-purple-400', icon: <RefreshCw className="h-3 w-3 animate-spin" /> },
  POI_DISCOVERY: { bg: 'bg-indigo-500/15 text-indigo-600 dark:text-indigo-400', icon: <RefreshCw className="h-3 w-3 animate-spin" /> },
  COMPLETED: { bg: 'bg-green-500/15 text-green-600 dark:text-green-400', icon: <CheckCircle className="h-3 w-3" /> },
  FAILED: { bg: 'bg-red-500/15 text-red-600 dark:text-red-400', icon: <XCircle className="h-3 w-3" /> },
  COMPENSATING: { bg: 'bg-orange-500/15 text-orange-600 dark:text-orange-400', icon: <AlertTriangle className="h-3 w-3" /> },
}

function StateBadge({ state }: { state: string }) {
  const style = STATE_STYLES[state] ?? STATE_STYLES.INITIAL
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium ${style.bg}`}>
      {style.icon}
      {state}
    </span>
  )
}

// ─── Saga Detail Page ─────────────────────────────────────────────────────

interface Props {
  itineraryId: string
}

export function SagaDetailPage({ itineraryId }: Props) {
  const client = useClient()
  const { user } = useAuth()
  const { navigate } = useRouterStore()

  const [sagas, setSagas] = useState<SagaInstanceDto[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isAdmin = user?.userType === 'ADMIN'

  useEffect(() => {
    if (!isAdmin) return

    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        // Admin users can access saga info without a specific access token
        const result = await client.getItinerarySagas(itineraryId, '')
        setSagas(result)
      } catch (err: any) {
        setError(err?.response?.data?.message ?? err?.message ?? 'Failed to load saga information')
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [client, itineraryId, isAdmin])

  if (!isAdmin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center px-4">
        <AlertTriangle className="h-12 w-12 text-red-500/70" />
        <h2 className="text-xl font-semibold">Access Denied</h2>
        <p className="text-foreground/60 text-sm max-w-xs">
          Saga information is only accessible to admin users.
        </p>
        <button onClick={() => navigate({ page: 'admin-dashboard' })} className="btn-primary mt-2">
          Go to Admin Dashboard
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* ── Header ── */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate({ page: 'admin-dashboard' })}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-border text-foreground/60 hover:bg-accent hover:text-foreground transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Saga Orchestration Lifecycle</h1>
          <p className="text-sm text-foreground/55 mt-1">
            Itinerary ID: <span className="font-mono">{itineraryId}</span>
          </p>
        </div>
      </div>

      {/* ── Error ── */}
      {error && (
        <div className="flex items-center gap-3 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-400">
          <XCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {/* ── Loading ── */}
      {loading && !sagas && (
        <div className="flex items-center justify-center py-24">
          <LoadingSpinner />
        </div>
      )}

      {/* ── Saga List ── */}
      {sagas && sagas.length === 0 && (
        <div className="rounded-xl border border-border px-4 py-16 text-center text-foreground/40 text-sm">
          No saga instances found for this itinerary.
        </div>
      )}

      {sagas && sagas.length > 0 && (
        <div className="space-y-4">
          {sagas.map((saga) => (
            <div key={saga.id} className="rounded-2xl border border-border p-6 bg-card hover:bg-muted/30 transition-colors">
              {/* Saga Header */}
              <div className="flex items-start justify-between mb-4">
                <div>
                  <div className="flex items-center gap-3">
                    <h3 className="text-lg font-semibold">Saga Instance</h3>
                    <StateBadge state={saga.currentState} />
                  </div>
                  <p className="text-xs text-foreground/40 font-mono mt-1">{saga.id}</p>
                </div>
                <div className="text-right text-sm text-foreground/60">
                  <div>Version: {saga.version}</div>
                  <div>Retries: {saga.retryCount} / {saga.maxRetries}</div>
                </div>
              </div>

              {/* Completed Steps */}
              {saga.completedSteps && saga.completedSteps.length > 0 && (
                <div className="mb-4">
                  <h4 className="text-sm font-medium text-foreground/70 mb-2">Completed Steps:</h4>
                  <div className="flex flex-wrap gap-2">
                    {saga.completedSteps.map((step, idx) => (
                      <span
                        key={idx}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-green-500/15 text-green-600 dark:text-green-400"
                      >
                        <CheckCircle className="h-3 w-3" />
                        {step}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Failed Step & Error */}
              {saga.failedStep && (
                <div className="mb-4">
                  <h4 className="text-sm font-medium text-red-600 dark:text-red-400 mb-2">Failed Step:</h4>
                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-red-500/15 text-red-600 dark:text-red-400">
                    <XCircle className="h-3 w-3" />
                    {saga.failedStep}
                  </span>
                  {saga.errorMessage && (
                    <div className="mt-2 p-3 rounded-lg bg-red-500/10 border border-red-500/30">
                      <p className="text-xs text-red-600 dark:text-red-400 font-mono">{saga.errorMessage}</p>
                    </div>
                  )}
                </div>
              )}

              {/* Timestamps */}
              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-border/50">
                <div>
                  <span className="text-xs text-foreground/50">Created:</span>
                  <p className="text-sm text-foreground/80 mt-0.5">
                    {new Date(saga.createdAt).toLocaleString()}
                  </p>
                </div>
                <div>
                  <span className="text-xs text-foreground/50">Updated:</span>
                  <p className="text-sm text-foreground/80 mt-0.5">
                    {new Date(saga.updatedAt).toLocaleString()}
                  </p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

