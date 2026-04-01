import React, { useState, useEffect } from 'react'
import { Trash2, Edit2, Eye, Clock, Loader2, MapPin, Plus, RefreshCw, Copy } from 'lucide-react'
import { toast } from 'react-hot-toast'
import { useRouterStore } from '../store/routerStore'
import { useClient } from '../contexts/ClientContext'
import { useAuth } from '../contexts/AuthContext'
import { ConfirmDialog } from '../components/common/ConfirmDialog'
import { EditItineraryModal } from '../components/itinerary/EditItineraryModal'
import { mapResponseToItinerary } from '../utils/mappers'
import type { Itinerary } from '../types/itinerary'
import type { ItineraryStatusResponse, ItinerarySummary } from '../api/client'

type FilterTab = 'all' | 'queued' | 'processing' | 'completed' | 'failed'

// Local type for displaying itineraries
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
  if (s === 'PROCESSING') return <span className="badge-processing flex items-center gap-1">
    <Loader2 className="h-3 w-3 animate-spin" /> Processing
  </span>
  if (s === 'QUEUED') return <span className="badge-queued flex items-center gap-1">
    <Clock className="h-3 w-3" /> In Queue
  </span>
  if (s === 'FAILED') return <span className="badge-failed">Failed</span>
  return <span className="badge-draft">{status}</span>
}

export function MyItinerariesPage() {
  const { navigate } = useRouterStore()
  const { user } = useAuth()
  const client = useClient()

  const [itineraries, setItineraries] = useState<ItineraryRecord[]>([])
  const [statuses, setStatuses] = useState<Record<string, ItineraryStatusResponse>>({})
  const [loadingStatuses, setLoadingStatuses] = useState(false)
  const [loadingItineraries, setLoadingItineraries] = useState(true)
  const [filter, setFilter] = useState<FilterTab>('all')
  const [deleteTarget, setDeleteTarget] = useState<ItineraryRecord | null>(null)
  const [editTarget, setEditTarget] = useState<Itinerary | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)

  // Load itineraries based on user type
  useEffect(() => {
    loadItineraries()
  }, [user])

  const loadItineraries = async () => {
    setLoadingItineraries(true)
    try {
      // All users (registered and anonymous) fetch from backend API
      // Backend filters by current user ID from JWT
      const response = await client.listMyItineraries()
      const records: ItineraryRecord[] = response.itineraries.map((it: ItinerarySummary) => ({
        id: it.id,
        accessToken: '', // Access tokens not needed when using JWT auth
        title: it.title,
        status: it.status,
        createdAt: it.createdAt,
      }))
      setItineraries(records)
    } catch (err) {
      console.error('Failed to load itineraries:', err)
      toast.error('Failed to load itineraries')
    } finally {
      setLoadingItineraries(false)
    }
  }


  const loadStatuses = async () => {
    if (itineraries.length === 0) return
    setLoadingStatuses(true)
    try {
      const results = await Promise.allSettled(
        itineraries.map((it) => client.getItineraryStatus(it.id, it.accessToken))
      )
      const map: Record<string, ItineraryStatusResponse> = {}
      results.forEach((r, i) => {
        if (r.status === 'fulfilled') {
          map[itineraries[i].id] = r.value
        }
      })
      setStatuses(map)
    } finally {
      setLoadingStatuses(false)
    }
  }

  useEffect(() => {
    loadStatuses()
  }, [itineraries.length])

  const getStatus = (id: string) => statuses[id]?.status ?? itineraries.find((i) => i.id === id)?.status ?? ''

  const filtered = itineraries.filter((it) => {
    if (filter === 'all') return true
    const s = getStatus(it.id).toUpperCase()
    if (filter === 'queued') return s === 'QUEUED'
    if (filter === 'processing') return s === 'PROCESSING'
    if (filter === 'completed') return s === 'COMPLETED'
    if (filter === 'failed') return s === 'FAILED'
    return true
  })

  const handleDelete = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    try {
      await client.deleteItinerary(deleteTarget.id, deleteTarget.accessToken)

      // Reload itineraries from backend
      await loadItineraries()

      toast.success('Itinerary deleted')
    } catch {
      toast.error('Failed to delete itinerary')
    } finally {
      setDeleting(false)
      setDeleteTarget(null)
    }
  }

  const handleEdit = async (record: ItineraryRecord) => {
    try {
      const res = await client.getItinerary(record.id, record.accessToken)
      setEditTarget(mapResponseToItinerary(res))
      setEditOpen(true)
    } catch {
      toast.error('Failed to load itinerary')
    }
  }

  const handleClone = async (record: ItineraryRecord) => {
    try {
      toast.loading('Cloning itinerary...', { id: 'clone' })
      const response = await client.cloneItinerary(record.id, record.accessToken)
      toast.success('Itinerary cloned successfully!', { id: 'clone' })

      // Reload itineraries to show the new one in the list
      await loadItineraries()
    } catch (error: any) {
      console.error('Clone error:', error)
      if (error?.response?.status === 429) {
        const msg = error?.response?.data?.message || 'Rate limit exceeded'
        toast.error(`⏱️ ${msg}. Please wait before cloning again.`, { id: 'clone', duration: 5000 })
      } else {
        toast.error('Failed to clone itinerary', { id: 'clone' })
      }
    }
  }

  const tabs: { key: FilterTab; label: string }[] = [
    { key: 'all', label: 'All' },
    { key: 'queued', label: 'In Queue' },
    { key: 'processing', label: 'Processing' },
    { key: 'completed', label: 'Completed' },
    { key: 'failed', label: 'Failed' },
  ]

  return (
    <div className="page-container animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-6 animate-fade-in-down">
        <div>
          <h1 className="section-title">My Itineraries</h1>
          <p className="section-subtitle">{itineraries.length} total</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={loadStatuses}
            disabled={loadingStatuses}
            className="btn btn-ghost btn-sm"
            title="Refresh statuses"
          >
            <RefreshCw className={`h-4 w-4 ${loadingStatuses ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => navigate({ page: 'create' })}
            className="btn btn-primary"
          >
            <Plus className="h-4 w-4" />
            New
          </button>
        </div>
      </div>

      {/* Filter tabs */}
      <div className="tab-bar mb-6">
        {tabs.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setFilter(key)}
            className={`tab-item ${filter === key ? 'tab-item-active' : ''}`}
          >
            {label}
            {key === 'all' && itineraries.length > 0 && (
              <span className="ml-1.5 rounded-full bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                {itineraries.length}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Empty state */}
      {filtered.length === 0 && (
        <div className="card animate-scale-in">
          <div className="empty-state">
            <div className="empty-state-icon">
              <MapPin className="h-8 w-8" />
            </div>
            <p className="empty-state-title">
              {filter === 'all' ? 'No itineraries yet' : `No ${filter} itineraries`}
            </p>
            <p className="empty-state-body">
              {filter === 'all'
                ? 'Create your first itinerary to start planning your adventure.'
                : `You don't have any ${filter} itineraries right now.`}
            </p>
            {filter === 'all' && (
              <button
                onClick={() => navigate({ page: 'create' })}
                className="btn btn-primary mt-6"
              >
                <Plus className="h-4 w-4" />
                Create Itinerary
              </button>
            )}
          </div>
        </div>
      )}

      {/* Itinerary list */}
      {filtered.length > 0 && (
        <div className="space-y-3">
          {filtered.map((it) => {
            const status = getStatus(it.id)
            const statusData = statuses[it.id]
            const isCompleted = status.toUpperCase() === 'COMPLETED'
            
            return (
              <div
                key={it.id}
                className="card animate-fade-in-up"
              >
                <div className="flex items-start justify-between gap-3 sm:items-center">
                  {/* Title and metadata */}
                  <div className="flex-1 min-w-0">
                    <h3 className="card-title truncate">{it.title}</h3>
                    <div className="flex items-center gap-2 text-sm text-muted-foreground mt-1">
                      <StatusBadge status={status} />
                      <span>·</span>
                      <span>{new Date(it.createdAt).toLocaleDateString()}</span>
                      {statusData?.queuePosition && status.toUpperCase() === 'QUEUED' && (
                        <>
                          <span>·</span>
                          <span className="inline-flex items-center gap-1 text-primary font-medium">
                            Queue: #{statusData.queuePosition}
                            {statusData.estimatedCompletion && (
                              <span className="text-muted-foreground font-normal">
                                · Est. {new Date(statusData.estimatedCompletion).toLocaleTimeString([], { 
                                  hour: '2-digit', 
                                  minute: '2-digit' 
                                })}
                              </span>
                            )}
                          </span>
                        </>
                      )}
                    </div>
                    
                    {/* Progress bar */}
                    {statusData?.progressPercent !== undefined && status.toUpperCase() !== 'COMPLETED' && (
                      <div className="progress-bar mt-2">
                        <div
                          className={`progress-fill ${status.toUpperCase() === 'FAILED' ? 'bg-destructive' : status.toUpperCase() === 'COMPLETED' ? 'progress-fill-success' : ''}`}
                          style={{ width: `${statusData.progressPercent}%` }}
                        />
                      </div>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-1.5 flex-shrink-0 self-start sm:self-center">
                    {isCompleted && (
                      <button
                        onClick={() => navigate({ page: 'itinerary-detail', id: it.id, accessToken: it.accessToken })}
                        className="btn btn-primary btn-sm"
                      >
                        <Eye className="h-4 w-4" />
                        <span className="hidden sm:inline">View</span>
                      </button>
                    )}
                    {!isCompleted && (
                      <button
                        onClick={() => navigate({ page: 'itinerary-tracking', id: it.id, accessToken: it.accessToken })}
                        className="btn btn-ghost btn-sm"
                      >
                        <Clock className="h-4 w-4" />
                        <span className="hidden sm:inline">Track</span>
                      </button>
                    )}
                    <button
                      onClick={() => handleClone(it)}
                      className="btn-icon btn-ghost hover:text-primary hover:bg-primary/10"
                      title="Clone itinerary"
                    >
                      <Copy className="h-4 w-4" />
                    </button>
                    <button
                      onClick={() => handleEdit(it)}
                      className="btn-icon btn-ghost"
                      title="Edit itinerary"
                    >
                      <Edit2 className="h-4 w-4" />
                    </button>
                    <button
                      onClick={() => setDeleteTarget(it)}
                      className="btn-icon btn-ghost hover:text-destructive hover:bg-destructive/10"
                      title="Delete itinerary"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Delete confirmation */}
      <ConfirmDialog
        isOpen={deleteTarget !== null}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
        title="Delete Itinerary"
        message={`Are you sure you want to delete "${deleteTarget?.title}"? This action cannot be undone.`}
        confirmText={deleting ? 'Deleting…' : 'Delete'}
        variant="danger"
      />

      {/* Edit modal */}
      {editTarget && (
        <EditItineraryModal
          isOpen={editOpen}
          onClose={() => { setEditOpen(false); setEditTarget(null) }}
          itinerary={editTarget}
          onSaved={(updated) => {
            setEditTarget(updated)
          }}
        />
      )}
    </div>
  )
}


