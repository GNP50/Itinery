import React, { useState, useEffect, useMemo } from 'react'
import {
  ArrowLeft,
  Edit2,
  Navigation,
  MapPin,
  MessageSquare,
  List,
  Loader2,
  AlertCircle,
  GitBranch,
  Clock,
  LayoutList,
  LayoutGrid,
  GitCommit,
  Copy,
} from 'lucide-react'
import { toast } from 'react-hot-toast'
import { useRouterStore } from '../store/routerStore'
import { useClient } from '../contexts/ClientContext'
import { mapResponseToItinerary } from '../utils/mappers'
import { ItineraryDetail } from '../components/itinerary/ItineraryDetail'
import { ItinerarySummary } from '../components/itinerary/ItinerarySummary'
import { AiChatPanel } from '../components/ai/AiChatPanel'
import { EditItineraryModal } from '../components/itinerary/EditItineraryModal'
import { StepCard } from '../components/itinerary/StepCard'
import { StepDetailModal } from '../components/itinerary/StepDetailModal'
import { ItineraryTimeline } from '../components/itinerary/ItineraryTimeline'
import type { Itinerary, ItineraryStep, ViewMode } from '../types/itinerary'
import type { SagaInstanceDto } from '../api/client'
import { calculateCumulativeStats } from '../utils/routeUtils'

interface Props {
  id: string
  accessToken: string
}

type Tab = 'map' | 'steps' | 'chat' | 'saga'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

export function ItineraryDetailPage({ id, accessToken }: Props) {
  const { back } = useRouterStore()
  const client = useClient()

  const [itinerary, setItinerary] = useState<Itinerary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>('steps')
  const [viewMode, setViewMode] = useState<ViewMode>('overview')
  const [editOpen, setEditOpen] = useState(false)
  const [sagas, setSagas] = useState<SagaInstanceDto[]>([])
  const [sagasLoading, setSagasLoading] = useState(false)
  const [selectedStep, setSelectedStep] = useState<ItineraryStep | null>(null)
  const [stepModalOpen, setStepModalOpen] = useState(false)

  // Calculate cumulative stats for all steps
  const cumulativeStats = useMemo(
    () => (itinerary ? calculateCumulativeStats(itinerary.steps) : []),
    [itinerary]
  )

  useEffect(() => {
    console.log('[ItineraryDetailPage] Loading itinerary:', { id, accessToken })
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        console.log('[ItineraryDetailPage] Fetching itinerary from API...')
        const res = await client.getItinerary(id, accessToken)
        console.log('[ItineraryDetailPage] API response:', res)
        setItinerary(mapResponseToItinerary(res))
        
        // Load sagas for all users (not just admin)
        console.log('[ItineraryDetailPage] Loading sagas...')
        loadSagas()
      } catch (err) {
        console.error('[ItineraryDetailPage] Error loading itinerary:', err)
        setError('Failed to load itinerary')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [id, accessToken])

  const loadSagas = async () => {
    setSagasLoading(true)
    try {
      console.log('[ItineraryDetailPage] Fetching sagas for itinerary:', id)
      const sagaList = await client.getItinerarySagas(id, accessToken)
      console.log('[ItineraryDetailPage] Sagas response:', sagaList)
      setSagas(sagaList)
    } catch (err) {
      console.error('[ItineraryDetailPage] Error loading sagas:', err)
      toast.error('Failed to load saga information')
    } finally {
      setSagasLoading(false)
    }
  }

  const handleMarkVisited = async (stepId: string) => {
    if (!itinerary) return
    const stepIndex = itinerary.steps.findIndex((s) => s.id === stepId)
    if (stepIndex === -1) return

    // To mark a step as VISITED, set currentStepIndex to the NEXT step
    // Backend logic: i < targetIndex = VISITED, i == targetIndex = CURRENT
    const targetIndex = stepIndex + 1

    try {
      await client.updatePosition(id, accessToken, { currentStepIndex: targetIndex })
      setItinerary((prev) => {
        if (!prev) return prev
        return {
          ...prev,
          currentStepIndex: targetIndex,
          steps: prev.steps.map((s, i) => ({
            ...s,
            status:
              i < targetIndex ? 'VISITED' : i === targetIndex ? 'CURRENT' : 'PENDING',
          })),
        }
      })
      toast.success(`Marked "${itinerary.steps[stepIndex].placeName}" as visited`)
    } catch {
      toast.error('Failed to update position')
    }
  }

  const handleClone = async () => {
    if (!itinerary) return

    try {
      toast.loading('Cloning itinerary...', { id: 'clone' })
      const response = await client.cloneItinerary(id, accessToken)
      toast.success('Itinerary cloned successfully!', { id: 'clone' })

      // Navigate to my-itineraries page to see the cloned item in the list
      const { navigate } = useRouterStore.getState()
      navigate({ page: 'my-itineraries' })
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


  const handleAiChat = async (message: string, history: Message[]): Promise<string> => {
    const res = await client.chat({
      itineraryId: id,
      accessToken,
      message,
      conversation_history: history.map((m) => ({
        role: m.role,
        content: m.content,
      })),
    })
    return res.reply
  }

  const handleViewStepDetails = (step: ItineraryStep) => {
    setSelectedStep(step)
    setStepModalOpen(true)
  }

  const getCumulativeForStep = (stepId: string) => {
    return cumulativeStats.find((s) => s.stepId === stepId) || { cumulativeDistance: 0, cumulativeDuration: 0 }
  }

  if (loading) {
    return (
      <div className="page-container flex items-center justify-center py-20">
        <div className="flex flex-col items-center gap-4 text-muted-foreground animate-fade-in">
          <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center">
            <Loader2 className="h-6 w-6 text-primary animate-spin" />
          </div>
          <p className="text-lg font-medium">Loading itinerary…</p>
        </div>
      </div>
    )
  }

  if (error || !itinerary) {
    return (
      <div className="page-container">
        <button onClick={back} className="btn btn-ghost btn-sm mb-6">
          <ArrowLeft className="h-4 w-4" />
          Back
        </button>
        <div className="card card-body text-center py-12 animate-fade-in">
          <div className="h-16 w-16 rounded-2xl bg-red-100 flex items-center justify-center mx-auto mb-4">
            <AlertCircle className="h-8 w-8 text-red-600" />
          </div>
          <p className="text-lg font-semibold text-foreground mb-2">Oops!</p>
          <p className="text-muted-foreground">{error ?? 'Itinerary not found'}</p>
        </div>
      </div>
    )
  }

  const currentStep =
    itinerary.currentStepIndex !== undefined
      ? itinerary.steps[itinerary.currentStepIndex]
      : null
  const nextStep =
    itinerary.currentStepIndex !== undefined
      ? itinerary.steps[itinerary.currentStepIndex + 1]
      : null

  // Check if itinerary has valid coordinates
  const hasCoordinates = itinerary.steps.some(s => s.latitude !== null && s.longitude !== null)
  const isProcessed = itinerary.status === 'COMPLETED' || itinerary.status === 'FAILED'

  const tabs: { key: Tab; label: string; icon: React.ElementType }[] = [
    ...(hasCoordinates ? [{ key: 'map' as Tab, label: 'Map & Route', icon: Navigation }] : []),
    { key: 'steps' as Tab, label: 'Steps', icon: List },
    { key: 'chat' as Tab, label: 'AI Chat', icon: MessageSquare },
    { key: 'saga' as Tab, label: 'Saga Lifecycle', icon: GitBranch },
  ]

  return (
    <div className="page-container animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <button onClick={back} className="btn btn-ghost btn-sm group">
          <ArrowLeft className="h-4 w-4 group-hover:-translate-x-1 transition-transform" />
          Back
        </button>
        <div className="flex items-center gap-2">
          <button
            onClick={handleClone}
            className="btn btn-secondary btn-sm"
            title="Clone this itinerary"
          >
            <Copy className="h-4 w-4" />
            Clone
          </button>
          <button
            onClick={() => setEditOpen(true)}
            className="btn btn-secondary btn-sm"
          >
            <Edit2 className="h-4 w-4" />
            Edit
          </button>
        </div>
      </div>

      {/* Title */}
      <div className="mb-6 animate-fade-in-up">
        <div className="flex items-center gap-3 flex-wrap">
          <h1 className="text-3xl font-bold">{itinerary.title}</h1>
          {itinerary.status === 'COMPLETED' && (
            <span className="badge-completed">Completed</span>
          )}
          {itinerary.status === 'QUEUED' && (
            <span className="inline-flex items-center px-3 py-1 rounded-md text-xs font-medium bg-yellow-500/15 text-yellow-600 dark:text-yellow-400">
              <Clock className="h-3 w-3 mr-1" />
              Queued (Position #{itinerary.queuePosition})
            </span>
          )}
          {itinerary.status === 'PROCESSING' && (
            <span className="inline-flex items-center px-3 py-1 rounded-md text-xs font-medium bg-blue-500/15 text-blue-600 dark:text-blue-400">
              <Loader2 className="h-3 w-3 mr-1 animate-spin" />
              Processing
            </span>
          )}
        </div>
        {itinerary.description && (
          <p className="text-muted-foreground mt-2 max-w-2xl">{itinerary.description}</p>
        )}
      </div>

      {/* Queued/Processing Banner */}
      {!isProcessed && (
        <div className="mb-6 card card-body bg-gradient-to-r from-yellow-500/5 to-orange-500/5 border-yellow-500/20">
          <div className="flex items-start gap-4">
            <div className="h-10 w-10 rounded-xl bg-yellow-500/10 flex items-center justify-center flex-shrink-0">
              <Clock className="h-5 w-5 text-yellow-600" />
            </div>
            <div className="flex-1">
              <p className="font-semibold text-foreground mb-1">Itinerary is being processed</p>
              <p className="text-sm text-muted-foreground">
                {itinerary.status === 'QUEUED' 
                  ? `Your itinerary is in queue (position #${itinerary.queuePosition}). Geographic enrichment will start soon.`
                  : 'Your itinerary is being enriched with geographic data, AI descriptions, and route information.'}
              </p>
              {itinerary.estimatedCompletion && (
                <p className="text-xs text-muted-foreground mt-2">
                  Estimated completion: {new Date(itinerary.estimatedCompletion).toLocaleString()}
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Current Position Banner */}
      {currentStep && (
        <div className="mb-6 card card-body flex items-center gap-4 bg-gradient-to-r from-primary/5 to-purple-500/5 border-primary/20">
          <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
            <MapPin className="h-6 w-6 text-primary" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm text-muted-foreground">Current Location</p>
            <p className="font-bold text-lg">{currentStep.placeName}</p>
            {currentStep.city && <p className="text-sm text-muted-foreground">{currentStep.city}</p>}
          </div>
          {nextStep && (
            <div className="hidden sm:block text-right">
              <p className="text-xs text-muted-foreground">Next</p>
              <p className="font-medium text-primary">{nextStep.placeName}</p>
            </div>
          )}
        </div>
      )}

      {/* Tabs */}
      <div className="tab-bar mb-6">
        {tabs.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`tab-item flex items-center gap-2 ${activeTab === key ? 'tab-item-active' : ''}`}
          >
            <Icon className="h-4 w-4" />
            {label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      <div className="min-h-[400px]">
        {activeTab === 'map' && hasCoordinates && (
          <div key="map" className="animate-fade-in-up">
            <ItineraryDetail
              itinerary={itinerary}
              onMarkVisited={handleMarkVisited}
            />
          </div>
        )}

        {activeTab === 'steps' && (
          <div key="steps" className="space-y-6 animate-fade-in-up">
            <ItinerarySummary itinerary={itinerary} showAiEstimates />

            {/* View Mode Switcher */}
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-bold">Steps</h2>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setViewMode('overview')}
                  className={`btn btn-sm ${
                    viewMode === 'overview' ? 'btn-primary' : 'btn-ghost'
                  }`}
                  title="List view"
                >
                  <LayoutList className="h-4 w-4" />
                </button>
                <button
                  onClick={() => setViewMode('timeline')}
                  className={`btn btn-sm ${
                    viewMode === 'timeline' ? 'btn-primary' : 'btn-ghost'
                  }`}
                  title="Timeline view"
                >
                  <GitCommit className="h-4 w-4" />
                </button>
                <button
                  onClick={() => setViewMode('grid')}
                  className={`btn btn-sm ${
                    viewMode === 'grid' ? 'btn-primary' : 'btn-ghost'
                  }`}
                  title="Grid view"
                >
                  <LayoutGrid className="h-4 w-4" />
                </button>
              </div>
            </div>

            {/* Overview - List View */}
            {viewMode === 'overview' && (
              <div className="space-y-3">
                {itinerary.steps.map((step, idx) => {
                  const cumulative = getCumulativeForStep(step.id)
                  return (
                    <StepCard
                      key={step.id}
                      step={step}
                      index={idx}
                      onViewDetails={handleViewStepDetails}
                      cumulativeDistance={cumulative.cumulativeDistance}
                      cumulativeDuration={cumulative.cumulativeDuration}
                    />
                  )
                })}
              </div>
            )}

            {/* Timeline View */}
            {viewMode === 'timeline' && (
              <ItineraryTimeline
                steps={itinerary.steps}
                currentStepIndex={itinerary.currentStepIndex}
                onSelectStep={(step) => handleViewStepDetails(step)}
              />
            )}

            {/* Grid View */}
            {viewMode === 'grid' && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {itinerary.steps.map((step, idx) => {
                  const cumulative = getCumulativeForStep(step.id)
                  return (
                    <StepCard
                      key={step.id}
                      step={step}
                      index={idx}
                      onViewDetails={handleViewStepDetails}
                      cumulativeDistance={cumulative.cumulativeDistance}
                      cumulativeDuration={cumulative.cumulativeDuration}
                    />
                  )
                })}
              </div>
            )}
          </div>
        )}

        {activeTab === 'chat' && (
          <div key="chat" className="animate-fade-in-up">
            <AiChatPanel itineraryId={id} onSend={handleAiChat} inline />
          </div>
        )}

        {activeTab === 'saga' && (
          <div key="saga" className="animate-fade-in-up">
            <div className="card card-body">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-xl font-bold">Saga Lifecycle</h2>
                  <p className="text-sm text-muted-foreground mt-1">
                    Orchestration events for this itinerary
                  </p>
                </div>
                <button
                  onClick={loadSagas}
                  disabled={sagasLoading}
                  className="btn btn-secondary btn-sm"
                >
                  {sagasLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Refresh'}
                </button>
              </div>

              {sagasLoading && sagas.length === 0 ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="h-8 w-8 animate-spin text-primary" />
                </div>
              ) : sagas.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                  <GitBranch className="h-12 w-12 mx-auto mb-4 opacity-50" />
                  <p>No saga instances found</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {sagas.map((saga) => (
                    <div key={saga.id} className="border border-border rounded-xl p-4 hover:bg-muted/30 transition-colors">
                      <div className="flex items-start justify-between mb-3">
                        <div>
                          <div className="flex items-center gap-2 mb-1">
                            <GitBranch className="h-4 w-4 text-primary" />
                            <span className="font-mono text-sm text-muted-foreground">
                              {saga.id.slice(0, 8)}...
                            </span>
                          </div>
                          <p className="font-semibold">Saga Instance</p>
                          <p className="text-xs text-muted-foreground">
                            Itinerary: {saga.itineraryId.slice(0, 8)}...
                          </p>
                        </div>
                        <span className={`px-2 py-1 rounded-md text-xs font-medium ${
                          saga.currentState === 'COMPLETED' ? 'bg-green-500/15 text-green-600' :
                          saga.currentState === 'FAILED' ? 'bg-red-500/15 text-red-600' :
                          saga.currentState === 'COMPENSATING' ? 'bg-orange-500/15 text-orange-600' :
                          'bg-blue-500/15 text-blue-600'
                        }`}>
                          {saga.currentState}
                        </span>
                      </div>
                      
                      {saga.errorMessage && (
                        <div className="mt-2 p-3 rounded-lg bg-red-500/10 border border-red-500/20">
                          <p className="text-sm text-red-600 dark:text-red-400">{saga.errorMessage}</p>
                        </div>
                      )}

                      <div className="mt-3 grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <p className="text-muted-foreground">Started</p>
                          <p className="font-medium">
                            {saga.createdAt ? new Date(saga.createdAt).toLocaleString() : '—'}
                          </p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Updated</p>
                          <p className="font-medium">
                            {saga.updatedAt ? new Date(saga.updatedAt).toLocaleString() : '—'}
                          </p>
                        </div>
                      </div>

                      <div className="mt-3 grid grid-cols-3 gap-4 text-sm">
                        <div>
                          <p className="text-muted-foreground">Version</p>
                          <p className="font-medium">{saga.version}</p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Retries</p>
                          <p className="font-medium">{saga.retryCount} / {saga.maxRetries}</p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Completed Steps</p>
                          <p className="font-medium">{saga.completedSteps.length}</p>
                        </div>
                      </div>

                      {saga.completedSteps.length > 0 && (
                        <details className="mt-3">
                          <summary className="cursor-pointer text-sm text-primary hover:underline">
                            View completed steps ({saga.completedSteps.length})
                          </summary>
                          <div className="mt-2 p-3 rounded-lg bg-muted text-xs space-y-1">
                            {saga.completedSteps.map((step, idx) => (
                              <div key={idx} className="flex items-center gap-2">
                                <span className="text-green-600">✓</span>
                                <span>{step}</span>
                              </div>
                            ))}
                          </div>
                        </details>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Step Detail Modal */}
      <StepDetailModal
        step={selectedStep}
        isOpen={stepModalOpen}
        onClose={() => {
          setStepModalOpen(false)
          setSelectedStep(null)
        }}
        onMarkVisited={handleMarkVisited}
        cumulativeDistance={selectedStep ? getCumulativeForStep(selectedStep.id).cumulativeDistance : undefined}
        cumulativeDuration={selectedStep ? getCumulativeForStep(selectedStep.id).cumulativeDuration : undefined}
        totalDistance={itinerary.totalDistanceKm}
        totalDuration={itinerary.totalDurationMinutes}
      />

      {/* Edit Modal */}
      <EditItineraryModal
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        itinerary={itinerary}
        onSaved={(updated) => {
          setItinerary(updated)
          setEditOpen(false)
        }}
      />
    </div>
  )
}
