import React, { useState } from 'react'
import { ArrowLeft, Edit2, Eye, RefreshCw } from 'lucide-react'
import { toast } from 'react-hot-toast'
import { useRouterStore } from '../store/routerStore'
import { useQueueStore } from '../store/queueStore'
import { useItineraryStore } from '../store/itineraryStore'
import { useItineraryPolling } from '../hooks/useItineraryPolling'
import { QueueStatus } from '../components/queue/QueueStatus'
import { EditItineraryModal } from '../components/itinerary/EditItineraryModal'
import { useClient } from '../contexts/ClientContext'
import { useAuth } from '../contexts/AuthContext'
import { mapResponseToItinerary } from '../utils/mappers'
import type { Itinerary } from '../types/itinerary'

interface Props {
  id: string
  accessToken: string
}

function StatusBadge({ status }: { status: string }) {
  const s = status.toUpperCase()
  if (s === 'COMPLETED') return <span className="badge-completed text-sm px-3 py-1">Completed</span>
  if (s === 'PROCESSING') return <span className="badge-processing text-sm px-3 py-1">Processing</span>
  if (s === 'QUEUED') return <span className="badge-queued text-sm px-3 py-1">In Queue</span>
  if (s === 'FAILED') return <span className="badge-failed text-sm px-3 py-1">Failed</span>
  return <span className="badge-draft text-sm px-3 py-1">{status}</span>
}

export function ItineraryTrackingPage({ id, accessToken }: Props) {
  const { navigate, back } = useRouterStore()
  const { queueLength, processingCount, maxConcurrent, registeredQueueSize, anonymousQueueSize } = useQueueStore()
  const { itineraries } = useItineraryStore()
  const { user } = useAuth()
  const client = useClient()

  const record = itineraries.find((it) => it.id === id)
  const [editOpen, setEditOpen] = useState(false)
  const [fullItinerary, setFullItinerary] = useState<Itinerary | null>(null)

  const { status } = useItineraryPolling(id, accessToken, { intervalMs: 5000 })

  // Show toast when completed
  const [completedToastShown, setCompletedToastShown] = useState(false)
  React.useEffect(() => {
    if (status?.status === 'COMPLETED' && !completedToastShown) {
      toast.success('Your itinerary is ready! 🎉')
      setCompletedToastShown(true)
    }
  }, [status?.status])

  // Load full itinerary for edit modal
  const loadForEdit = async () => {
    if (fullItinerary) { setEditOpen(true); return }
    try {
      const res = await client.getItinerary(id, accessToken)
      setFullItinerary(mapResponseToItinerary(res))
      setEditOpen(true)
    } catch {
      toast.error('Failed to load itinerary for editing')
    }
  }

  const currentStatus = (status?.status ?? record?.status ?? 'QUEUED') as any
  const queuePosition = status?.queuePosition
  const estimatedCompletion = status?.estimatedCompletion
  const progress = status?.progressPercent ?? 0
  const title = record?.title ?? 'Itinerary'

  return (
    <div className="page-container max-w-2xl animate-fade-in">
      <button onClick={back} className="btn btn-ghost btn-sm mb-6 -ml-2 group animate-fade-in-down">
        <ArrowLeft className="h-4 w-4 group-hover:-translate-x-1 transition-transform" />
        Back
      </button>

      {/* Header card */}
      <div className="card mb-6 animate-fade-in-up" style={{ animationDelay: '50ms' }}>
        <div className="card-body">
          <div className="flex items-start justify-between gap-4 mb-4">
            <div className="min-w-0 flex-1">
              <h1 className="text-xl font-bold text-foreground truncate">{title}</h1>
              <p className="text-sm text-muted-foreground mt-1">Processing Status</p>
            </div>
            <StatusBadge status={currentStatus} />
          </div>

          {/* Progress bar */}
          <div className="progress-bar mb-2">
            <div
              className={`progress-fill ${
                currentStatus === 'COMPLETED' ? 'progress-fill-success' :
                currentStatus === 'FAILED' ? 'bg-destructive' :
                ''
              }`}
              style={{ width: `${progress}%` }}
            />
          </div>
          <p className="text-xs text-muted-foreground">{progress}% complete</p>
        </div>
      </div>

      {/* Queue status widget */}
      <div className="mb-6 animate-fade-in-up" style={{ animationDelay: '100ms' }}>
        <QueueStatus
          status={currentStatus === 'QUEUED' ? 'QUEUED' :
                  currentStatus === 'PROCESSING' ? 'PROCESSING' :
                  currentStatus === 'COMPLETED' ? 'COMPLETED' :
                  currentStatus === 'FAILED' ? 'FAILED' : 'IDLE'}
          queuePosition={queuePosition}
          estimatedCompletion={estimatedCompletion}
          progress={progress}
        />
      </div>

      {/* Global queue context */}
      {(queueLength > 0 || processingCount > 0) && (
        <div className="card card-body mb-6 flex items-center gap-4 text-sm text-muted-foreground animate-fade-in-up" style={{ animationDelay: '150ms' }}>
          <RefreshCw className="h-4 w-4 text-primary flex-shrink-0" />
          <span>
            System queue: <strong>{processingCount}/{maxConcurrent}</strong> processing
            {user?.userType === 'REGISTERED' || user?.userType === 'ADMIN' ? (
              <>, <strong>{registeredQueueSize}</strong> registered users waiting</>
            ) : (
              <>, <strong>{anonymousQueueSize}</strong> anonymous users waiting</>
            )}
            {' '}(total: <strong>{queueLength}</strong>)
          </span>
        </div>
      )}

      {/* Actions */}
      <div className="flex flex-col sm:flex-row gap-3 animate-fade-in-up" style={{ animationDelay: '200ms' }}>
        {currentStatus === 'COMPLETED' ? (
          <button
            onClick={() => navigate({ page: 'itinerary-detail', id, accessToken })}
            className="btn btn-primary btn-lg flex-1"
          >
            <Eye className="h-5 w-5" />
            View Itinerary
          </button>
        ) : (
          <button
            disabled
            className="btn btn-primary btn-lg flex-1 opacity-40 cursor-not-allowed"
          >
            <Eye className="h-5 w-5" />
            View when ready
          </button>
        )}

        <button
          onClick={loadForEdit}
          className="btn btn-secondary"
        >
          <Edit2 className="h-4 w-4" />
          Edit
        </button>
      </div>

      {/* Edit modal */}
      {fullItinerary && (
        <EditItineraryModal
          isOpen={editOpen}
          onClose={() => setEditOpen(false)}
          itinerary={fullItinerary}
          onSaved={(updated) => {
            setFullItinerary(updated)
            toast.success('Itinerary updated and re-queued')
          }}
        />
      )}
    </div>
  )
}
