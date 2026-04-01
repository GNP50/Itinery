import React, { useEffect, useState, useCallback } from 'react'
import { useClient } from '../contexts/ClientContext'
import { useAuth } from '../contexts/AuthContext'
import { useRouterStore } from '../store/routerStore'
import { AdminItinerarySummary, AdminPagedItineraries } from '../api/client'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import {
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  ExternalLink,
  AlertCircle,
  ShieldAlert,
} from 'lucide-react'

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_STYLES: Record<string, string> = {
  QUEUED:      'bg-yellow-500/15 text-yellow-600 dark:text-yellow-400',
  PROCESSING:  'bg-blue-500/15 text-blue-600 dark:text-blue-400',
  COMPLETED:   'bg-green-500/15 text-green-600 dark:text-green-400',
  FAILED:      'bg-red-500/15 text-red-600 dark:text-red-400',
  CANCELLED:   'bg-gray-500/15 text-gray-500',
}

function StatusBadge({ status }: { status: string }) {
  const cls = STATUS_STYLES[status] ?? 'bg-gray-500/15 text-gray-500'
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium ${cls}`}>
      {status}
    </span>
  )
}

// ─── User type badge ──────────────────────────────────────────────────────────

function UserTypeBadge({ type }: { type?: string }) {
  if (!type) return <span className="text-foreground/40 text-xs">—</span>
  const isRegistered = type === 'REGISTERED'
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium ${
      isRegistered
        ? 'bg-sidebar-primary/15 text-sidebar-primary'
        : 'bg-foreground/10 text-foreground/60'
    }`}>
      {isRegistered ? 'Registered' : 'Anonymous'}
    </span>
  )
}

// ─── Pagination controls ──────────────────────────────────────────────────────

function Pagination({
  page,
  totalPages,
  onPrev,
  onNext,
}: {
  page: number
  totalPages: number
  onPrev: () => void
  onNext: () => void
}) {
  return (
    <div className="flex items-center gap-3">
      <button
        onClick={onPrev}
        disabled={page === 0}
        className="flex h-8 w-8 items-center justify-center rounded-lg border border-border text-foreground/60
                   hover:bg-accent disabled:opacity-40 disabled:pointer-events-none transition-colors"
        title="Previous page"
      >
        <ChevronLeft className="h-4 w-4" />
      </button>
      <span className="text-sm text-foreground/60 tabular-nums">
        {totalPages > 0 ? `${page + 1} / ${totalPages}` : '—'}
      </span>
      <button
        onClick={onNext}
        disabled={page + 1 >= totalPages}
        className="flex h-8 w-8 items-center justify-center rounded-lg border border-border text-foreground/60
                   hover:bg-accent disabled:opacity-40 disabled:pointer-events-none transition-colors"
        title="Next page"
      >
        <ChevronRight className="h-4 w-4" />
      </button>
    </div>
  )
}

// ─── Admin Dashboard ──────────────────────────────────────────────────────────

const PAGE_SIZE = 20

export function AdminDashboard() {
  const client    = useClient()
  const { user }  = useAuth()
  const { navigate } = useRouterStore()

  const [data,    setData]    = useState<AdminPagedItineraries | null>(null)
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState<string | null>(null)
  const [page,    setPage]    = useState(0)

  const isAdmin = user?.userType === 'ADMIN'

  const load = useCallback(async (p: number) => {
    setLoading(true)
    setError(null)
    try {
      const result = await client.getAdminItineraries(p, PAGE_SIZE)
      setData(result)
    } catch (err: any) {
      setError(err?.response?.data?.message ?? err?.message ?? 'Failed to load itineraries')
    } finally {
      setLoading(false)
    }
  }, [client])

  // Load on page change
  useEffect(() => {
    if (isAdmin) load(page)
  }, [page, load, isAdmin])

  // Auto-refresh every 10 seconds for real-time dashboard
  useEffect(() => {
    if (!isAdmin) return

    const interval = setInterval(() => {
      load(page)
    }, 10000) // 10 seconds

    return () => clearInterval(interval)
  }, [page, load, isAdmin])

  const handleView = (item: AdminItinerarySummary) => {
    console.log('[AdminDashboard] handleView called with:', item)
    if (item.accessToken) {
      console.log('[AdminDashboard] Navigating to itinerary-detail:', { id: item.id, accessToken: item.accessToken })
      navigate({ page: 'itinerary-detail', id: item.id, accessToken: item.accessToken })
    } else {
      console.warn('[AdminDashboard] No accessToken available for item:', item.id)
    }
  }

  // Guard: only ADMIN users
  if (!isAdmin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center px-4">
        <ShieldAlert className="h-12 w-12 text-red-500/70" />
        <h2 className="text-xl font-semibold">Access Denied</h2>
        <p className="text-foreground/60 text-sm max-w-xs">
          The Admin Dashboard is only accessible to admin users.
        </p>
        <button
          onClick={() => navigate({ page: 'home' })}
          className="btn-primary mt-2"
        >
          Go to Dashboard
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto space-y-6">

      {/* ── Header ── */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Admin Dashboard</h1>
          <p className="text-sm text-foreground/55 mt-1">
            All itinerary requests across all users
            {data && (
              <span className="ml-2 text-foreground/40">
                ({data.totalElements.toLocaleString()} total)
              </span>
            )}
            <span className="ml-2 text-xs text-foreground/30">• auto-refreshes every 10s</span>
          </p>
        </div>

        <button
          onClick={() => load(page)}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-2 rounded-xl border border-border text-sm
                     text-foreground/60 hover:bg-accent hover:text-foreground transition-colors
                     disabled:opacity-40 disabled:pointer-events-none self-start sm:self-auto"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* ── Error ── */}
      {error && (
        <div className="flex items-center gap-3 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-400">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {/* ── Table ── */}
      <div className="rounded-2xl border border-border overflow-hidden">
        {loading && !data ? (
          <div className="flex items-center justify-center py-24">
            <LoadingSpinner />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/40">
                  <th className="text-left px-4 py-3 font-medium text-foreground/60 whitespace-nowrap">Title</th>
                  <th className="text-left px-4 py-3 font-medium text-foreground/60 whitespace-nowrap">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-foreground/60 whitespace-nowrap">Owner</th>
                  <th className="text-left px-4 py-3 font-medium text-foreground/60 whitespace-nowrap">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-foreground/60 whitespace-nowrap">Created</th>
                  <th className="text-left px-4 py-3 font-medium text-foreground/60 whitespace-nowrap">Queue</th>
                  <th className="text-left px-4 py-3 font-medium text-foreground/60 whitespace-nowrap">Saga</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {data?.items.length === 0 && (
                  <tr>
                    <td colSpan={8} className="px-4 py-16 text-center text-foreground/40 text-sm">
                      No itineraries found.
                    </td>
                  </tr>
                )}
                {data?.items.map((item) => (
                  <tr
                    key={item.id}
                    className="border-b border-border/60 last:border-0 hover:bg-muted/30 transition-colors cursor-pointer"
                    onClick={() => handleView(item)}
                  >
                    {/* Title */}
                    <td className="px-4 py-3 max-w-[200px]">
                      <span className="font-medium truncate block" title={item.title}>
                        {item.title}
                      </span>
                      <span className="text-[10px] text-foreground/35 font-mono">
                        {item.id.slice(0, 8)}…
                      </span>
                    </td>

                    {/* Status */}
                    <td className="px-4 py-3 whitespace-nowrap">
                      <StatusBadge status={item.status} />
                    </td>

                    {/* Owner */}
                    <td className="px-4 py-3 max-w-[180px]">
                      {item.ownerEmail ? (
                        <span className="truncate block text-foreground/80" title={item.ownerEmail}>
                          {item.ownerEmail}
                        </span>
                      ) : item.ownerName ? (
                        <span className="truncate block text-foreground/60">{item.ownerName}</span>
                      ) : (
                        <span className="text-foreground/35 text-xs">
                          {item.ownerId ? item.ownerId.slice(0, 8) + '…' : '—'}
                        </span>
                      )}
                    </td>

                    {/* User type */}
                    <td className="px-4 py-3 whitespace-nowrap">
                      <UserTypeBadge type={item.ownerType} />
                    </td>

                    {/* Created */}
                    <td className="px-4 py-3 whitespace-nowrap text-foreground/60">
                      {item.createdAt
                        ? new Date(item.createdAt).toLocaleDateString(undefined, {
                            year:  'numeric',
                            month: 'short',
                            day:   'numeric',
                          })
                        : '—'}
                    </td>

                    {/* Queue position */}
                    <td className="px-4 py-3 whitespace-nowrap text-foreground/60 tabular-nums">
                      {item.queuePosition != null ? `#${item.queuePosition}` : '—'}
                    </td>

                    {/* View action */}
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      {item.accessToken ? (
                        <button
                          onClick={() => handleView(item)}
                          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium
                                     border border-border text-foreground/60 hover:bg-accent hover:text-foreground
                                     transition-colors whitespace-nowrap"
                          title="View itinerary"
                        >
                          <ExternalLink className="h-3 w-3" />
                          View
                        </button>
                      ) : (
                        <span className="text-foreground/30 text-xs">No token</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Pagination ── */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-foreground/50">
            Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, data.totalElements)} of{' '}
            {data.totalElements.toLocaleString()} itineraries
          </p>
          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPrev={() => setPage((p) => Math.max(0, p - 1))}
            onNext={() => setPage((p) => p + 1)}
          />
        </div>
      )}
    </div>
  )
}
