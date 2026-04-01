import React, { useEffect, useState } from 'react'
import {
  MapPin,
  Plus,
  Loader2,
  CheckCircle2,
  ArrowRight,
  Navigation,
  Sparkles,
  Route,
  TrendingUp,
  Activity,
} from 'lucide-react'
import { useRouterStore } from '../store/routerStore'
import { useAuth } from '../contexts/AuthContext'
import { useQueueStore } from '../store/queueStore'
import { useClient } from '../contexts/ClientContext'

interface ItineraryRecord {
  id: string
  accessToken: string
  title: string
  status: string
  createdAt: string
}

function StatusBadge({ status }: { status: string }) {
  const s = status.toUpperCase()
  if (s === 'COMPLETED') return <span className="badge-completed">Completed</span>
  if (s === 'PROCESSING')
    return (
      <span className="badge-processing flex items-center gap-1.5">
        <span className="status-dot-processing" /> Processing
      </span>
    )
  if (s === 'QUEUED')
    return (
      <span className="badge-queued flex items-center gap-1.5">
        <span className="status-dot-queued" /> Queued
      </span>
    )
  if (s === 'FAILED') return <span className="badge-failed">Failed</span>
  return <span className="badge-draft">{status}</span>
}

function StatCard({
  label,
  value,
  sub,
  icon: Icon,
  iconBg,
  iconColor,
  accent,
}: {
  label: string
  value: string | number
  sub?: string
  icon: React.ComponentType<{ className?: string }>
  iconBg: string
  iconColor: string
  accent?: string
}) {
  return (
    <div className="card p-5 flex flex-col gap-3">
      <div className="flex items-start justify-between">
        <p className="stat-label">{label}</p>
        <div className={`h-9 w-9 rounded-xl flex items-center justify-center ${iconBg}`}>
          <Icon className={`h-4.5 w-4.5 ${iconColor}`} />
        </div>
      </div>
      <div>
        <p className={`text-3xl font-bold tracking-tight ${accent ?? 'text-foreground'}`}>{value}</p>
        {sub && <p className="text-xs text-muted-foreground mt-1 leading-relaxed">{sub}</p>}
      </div>
    </div>
  )
}

export function HomePage() {
  const { navigate } = useRouterStore()
  const { user, getCurrentSessionItineraries } = useAuth()
  const queueStore = useQueueStore()
  const {
    queueLength,
    processingCount,
    maxConcurrent,
    maxQueueSize,
    items: queueItems,
    registeredQueueSize,
    anonymousQueueSize
  } = queueStore

  const [itineraries, setItineraries] = useState<ItineraryRecord[]>([])
  const [loadingItineraries, setLoadingItineraries] = useState(false)
  const client = useClient()

  const loadItineraries = async () => {
    if (!user) return
    setLoadingItineraries(true)
    try {
      // All users (registered and anonymous) fetch from backend API
      // Backend filters by current user ID from JWT
      const response = await client.listMyItineraries()
      const records: ItineraryRecord[] = response.itineraries.map((it) => ({
        id: it.id,
        accessToken: '', // Access tokens not needed when using JWT auth
        title: it.title,
        status: it.status,
        createdAt: it.createdAt,
      }))
      setItineraries(records)
    } catch (err) {
      console.error('Failed to load itineraries:', err)
      // Silently fail - user can navigate to My Itineraries for full view
    } finally {
      setLoadingItineraries(false)
    }
  }

  useEffect(() => {
    loadItineraries()
  }, [user])

  const completedCount = itineraries.filter((i) => i.status.toUpperCase() === 'COMPLETED').length
  const activeCount = itineraries.filter((i) =>
    ['QUEUED', 'PROCESSING'].includes(i.status.toUpperCase())
  ).length

  const recent = itineraries.slice(0, 5)

  return (
    <div className="page-container space-y-8 animate-fade-in">

      {/* ── Header ────────────────────────────────────────────────────── */}
      <div className="relative overflow-hidden rounded-2xl p-7 animate-fade-in-down">
        {/* gradient bg */}
        <div className="absolute inset-0 bg-gradient-to-br from-primary/90 via-purple-600/80 to-pink-500/70 rounded-2xl" />
        {/* subtle dot pattern */}
        <div
          className="absolute inset-0 opacity-20 rounded-2xl"
          style={{
            backgroundImage:
              'radial-gradient(circle, rgba(255,255,255,0.4) 1px, transparent 1px)',
            backgroundSize: '24px 24px',
          }}
        />
        <div className="relative flex items-center justify-between gap-4 flex-wrap">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <div className="h-8 w-8 rounded-lg bg-white/20 backdrop-blur flex items-center justify-center">
                <MapPin className="h-4 w-4 text-white" />
              </div>
              <span className="text-white/70 text-sm font-medium">ItineryViewer</span>
            </div>
            <h1 className="text-2xl font-bold text-white tracking-tight">
              Welcome back
            </h1>
            <p className="text-white/70 text-sm mt-1">
              {itineraries.length === 0
                ? 'Create your first trip to get started'
                : `You have ${itineraries.length} ${itineraries.length === 1 ? 'itinerary' : 'itineraries'}`}
            </p>
          </div>
          <button
            onClick={() => navigate({ page: 'create' })}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white text-primary font-semibold text-sm shadow-lg hover:bg-white/90 transition-all active:scale-[0.98]"
          >
            <Plus className="h-4 w-4" />
            New Itinerary
          </button>
        </div>
      </div>

      {/* ── Stats ─────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 stagger-children">
        <StatCard
          label="Total Trips"
          value={itineraries.length}
          sub="all time"
          icon={Route}
          iconBg="bg-primary/10"
          iconColor="text-primary"
        />
        <StatCard
          label="Completed"
          value={completedCount}
          sub={itineraries.length > 0 ? `${Math.round((completedCount / itineraries.length) * 100)}% success` : '—'}
          icon={CheckCircle2}
          iconBg="bg-emerald-500/10"
          iconColor="text-emerald-500"
          accent="text-emerald-500"
        />
        <StatCard
          label="Active"
          value={activeCount}
          sub={activeCount > 0 ? 'in progress' : 'all idle'}
          icon={activeCount > 0 ? Loader2 : Activity}
          iconBg="bg-blue-500/10"
          iconColor={activeCount > 0 ? 'text-blue-500 animate-spin' : 'text-blue-500'}
          accent="text-blue-500"
        />
      </div>

      {/* ── Main grid ─────────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Recent Itineraries */}
        <div className="lg:col-span-2 flex flex-col gap-4 animate-fade-in-up" style={{ animationDelay: '150ms' }}>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-foreground">Recent Itineraries</h2>
            {itineraries.length > 5 && (
              <button
                onClick={() => navigate({ page: 'my-itineraries' })}
                className="btn btn-ghost btn-sm group"
              >
                View all
                <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
              </button>
            )}
          </div>

          {recent.length === 0 ? (
            <div className="card flex flex-col items-center justify-center py-16 px-6 text-center gap-4">
              <div className="h-16 w-16 rounded-2xl bg-muted flex items-center justify-center">
                <MapPin className="h-7 w-7 text-muted-foreground" />
              </div>
              <div>
                <p className="font-semibold text-foreground mb-1">No trips yet</p>
                <p className="text-sm text-muted-foreground max-w-xs">
                  Create your first itinerary and start planning your next adventure.
                </p>
              </div>
              <button
                onClick={() => navigate({ page: 'create' })}
                className="btn btn-primary mt-1"
              >
                <Plus className="h-4 w-4" />
                Create Itinerary
              </button>
            </div>
          ) : (
            <div className="card divide-y divide-border overflow-hidden">
              {recent.map((it, index) => (
                <button
                  key={it.id}
                  className="w-full group flex items-center gap-4 px-5 py-4 hover:bg-muted/40 transition-colors text-left animate-fade-in-up"
                  onClick={() =>
                    it.status.toUpperCase() === 'COMPLETED'
                      ? navigate({ page: 'itinerary-detail', id: it.id, accessToken: it.accessToken })
                      : navigate({ page: 'itinerary-tracking', id: it.id, accessToken: it.accessToken })
                  }
                  style={{ animationDelay: `${200 + index * 50}ms` }}
                >
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/8 text-primary group-hover:bg-primary/15 transition-colors"
                    style={{ background: 'hsl(var(--primary) / 0.08)' }}>
                    <MapPin className="h-4.5 w-4.5" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-foreground truncate group-hover:text-primary transition-colors text-sm">
                      {it.title}
                    </p>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {new Date(it.createdAt).toLocaleDateString(undefined, {
                        month: 'short',
                        day: 'numeric',
                        year: 'numeric',
                      })}
                    </p>
                  </div>
                  <StatusBadge status={it.status} />
                  <ArrowRight className="h-4 w-4 text-muted-foreground/40 group-hover:text-primary group-hover:translate-x-0.5 transition-all shrink-0" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Right column */}
        <div className="flex flex-col gap-5 animate-slide-in-right" style={{ animationDelay: '200ms' }}>

          {/* Queue */}
          <div>
            <h2 className="text-lg font-bold text-foreground mb-3">Queue Status</h2>
            <div className="card">
              {queueItems.length === 0 && queueLength === 0 ? (
                <div className="flex flex-col items-center py-8 px-4 text-center gap-3">
                  <div className="h-12 w-12 rounded-xl bg-emerald-500/10 flex items-center justify-center">
                    <CheckCircle2 className="h-6 w-6 text-emerald-500" />
                  </div>
                  <p className="text-sm text-muted-foreground">All caught up!</p>
                </div>
              ) : (
                <div>
                  {/* Processing info */}
                  <div className="p-4 border-b border-border">
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-sm font-semibold text-foreground">Processing</span>
                      <span className="tabular-nums text-sm font-bold text-primary">{processingCount}/{maxConcurrent}</span>
                    </div>
                    <div className="progress-bar h-2">
                      <div
                        className="progress-fill"
                        style={{ width: `${maxConcurrent > 0 ? (processingCount / maxConcurrent) * 100 : 0}%` }}
                      />
                    </div>
                  </div>

                  {/* Backlog info */}
                  {queueLength > 0 && (
                    <div className="px-4 py-3 bg-muted/30 border-b border-border">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Backlog</span>
                        <span className="text-sm font-bold text-foreground tabular-nums">{queueLength}</span>
                      </div>
                      <div className="flex flex-wrap gap-2 text-xs">
                        {registeredQueueSize > 0 && (
                          <div className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-primary/10">
                            <div className="h-1.5 w-1.5 rounded-full bg-primary" />
                            <span className="text-primary font-medium">{registeredQueueSize} priority</span>
                          </div>
                        )}
                        {anonymousQueueSize > 0 && (
                          <div className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-muted">
                            <div className="h-1.5 w-1.5 rounded-full bg-muted-foreground" />
                            <span className="text-foreground">{anonymousQueueSize} standard</span>
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Queue items */}
                  {queueItems.length > 0 && (
                    <div>
                      {queueItems.slice(0, 4).map((item) => (
                        <div key={item.id} className="flex items-center gap-3 px-4 py-3 border-b border-border last:border-0 hover:bg-muted/20 transition-colors">
                          <div className={`h-2 w-2 shrink-0 rounded-full ${
                            item.status === 'PROCESSING' ? 'status-dot-processing' :
                            item.status === 'QUEUED' ? 'status-dot-queued' :
                            item.status === 'COMPLETED' ? 'status-dot-completed' : 'status-dot-failed'
                          }`} />
                          <p className="text-sm text-foreground truncate flex-1 min-w-0">{item.title}</p>
                          {item.queuePosition && (
                            <span className="text-xs text-muted-foreground font-mono shrink-0 px-1.5 py-0.5 rounded bg-muted">#{item.queuePosition}</span>
                          )}
                        </div>
                      ))}
                      {queueLength > 4 && (
                        <div className="px-4 py-2 bg-muted/20 text-center">
                          <span className="text-xs text-muted-foreground font-medium">+{queueLength - 4} more in queue</span>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Quick actions */}
          <div>
            <h2 className="text-lg font-bold text-foreground mb-3">Quick Actions</h2>
            <div className="flex flex-col gap-2">
              <button
                onClick={() => navigate({ page: 'create' })}
                className="card flex items-center gap-3 px-4 py-3.5 group hover:border-primary/30 transition-all text-left"
              >
                <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center group-hover:bg-primary/20 transition-colors shrink-0">
                  <Plus className="h-4 w-4 text-primary" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground">Create New</p>
                  <p className="text-xs text-muted-foreground">Start planning a trip</p>
                </div>
                <ArrowRight className="h-4 w-4 text-muted-foreground/40 group-hover:text-primary transition-colors shrink-0" />
              </button>

              <button
                onClick={() => navigate({ page: 'my-itineraries' })}
                className="card flex items-center gap-3 px-4 py-3.5 group hover:border-primary/30 transition-all text-left"
              >
                <div className="h-9 w-9 rounded-lg bg-muted flex items-center justify-center group-hover:bg-muted/80 transition-colors shrink-0">
                  <Navigation className="h-4 w-4 text-muted-foreground" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground">My Itineraries</p>
                  <p className="text-xs text-muted-foreground">Manage your trips</p>
                </div>
                <ArrowRight className="h-4 w-4 text-muted-foreground/40 group-hover:text-primary transition-colors shrink-0" />
              </button>
            </div>
          </div>

          {/* AI card */}
          <div className="relative overflow-hidden rounded-2xl p-5 text-white">
            <div className="absolute inset-0 bg-gradient-to-br from-primary via-purple-600 to-pink-500" />
            <div
              className="absolute inset-0 opacity-15"
              style={{
                backgroundImage:
                  'radial-gradient(circle, rgba(255,255,255,0.6) 1px, transparent 1px)',
                backgroundSize: '20px 20px',
              }}
            />
            <div className="relative flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <div className="h-8 w-8 rounded-lg bg-white/20 backdrop-blur flex items-center justify-center">
                  <Sparkles className="h-4 w-4 text-white" />
                </div>
                <span className="font-semibold text-sm">AI Powered</span>
              </div>
              <p className="text-xs text-white/75 leading-relaxed">
                AI-generated tips, route optimization, and personalized recommendations — available in any itinerary detail view.
              </p>
            </div>
          </div>

        </div>
      </div>
    </div>
  )
}
