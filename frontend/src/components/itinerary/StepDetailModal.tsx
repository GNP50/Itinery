import React, { useState, useEffect } from 'react'
import { X, Info, MapPin, Navigation, Calendar, MessageSquare, CheckCircle2 } from 'lucide-react'
import type { ItineraryStep } from '../../types/itinerary'
import { PoiDetailGrid } from './PoiDetailGrid'
import { RouteMetricsCard } from './RouteMetricsCard'
import { LocationHierarchyCard } from './LocationHierarchyCard'
import { formatRouteGeometry } from '../../utils/routeUtils'

interface Props {
  step: ItineraryStep | null
  isOpen: boolean
  onClose: () => void
  onMarkVisited?: (stepId: string) => void
  cumulativeDistance?: number
  cumulativeDuration?: number
  totalDistance?: number
  totalDuration?: number
}

type Tab = 'overview' | 'pois' | 'route'

export function StepDetailModal({
  step,
  isOpen,
  onClose,
  onMarkVisited,
  cumulativeDistance,
  cumulativeDuration,
  totalDistance,
  totalDuration,
}: Props) {
  const [activeTab, setActiveTab] = useState<Tab>('overview')

  // Reset tab when modal opens
  useEffect(() => {
    if (isOpen) {
      setActiveTab('overview')
    }
  }, [isOpen])

  // Close on Escape key
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose()
      }
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [isOpen, onClose])

  if (!isOpen || !step) return null

  const hasPois = step.poiNearby && step.poiNearby.length > 0
  const hasRouteData = step.distanceFromPrevKm || step.durationFromPrevMin || step.routeGeometryFromPrev

  // Parse AI tips (stored as JSON string)
  let aiTips: string[] = []
  if (step.aiTips) {
    try {
      aiTips = JSON.parse(step.aiTips)
    } catch {
      // If not JSON, treat as single tip
      aiTips = [step.aiTips]
    }
  }

  const tabs: { key: Tab; label: string; icon: React.ElementType; disabled?: boolean }[] = [
    { key: 'overview', label: 'Overview', icon: Info },
    { key: 'pois', label: `POIs (${step.poiNearby?.length || 0})`, icon: MapPin, disabled: !hasPois },
    { key: 'route', label: 'Route Details', icon: Navigation, disabled: !hasRouteData },
  ]

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-panel max-w-4xl max-h-[90vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="modal-header flex-shrink-0">
          <div className="flex items-start justify-between">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-3 mb-2">
                <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
                  <span className="font-bold text-primary">{step.stepOrder}</span>
                </div>
                <div className="min-w-0 flex-1">
                  <h2 className="text-xl font-bold truncate">{step.placeName}</h2>
                  {step.city && (
                    <p className="text-sm text-muted-foreground">
                      {[step.city, step.region, step.country].filter(Boolean).join(', ')}
                    </p>
                  )}
                </div>
              </div>

              {/* Status badge */}
              {step.status && (
                <span
                  className={`inline-flex items-center px-3 py-1 rounded-md text-xs font-medium ${
                    step.status === 'VISITED'
                      ? 'bg-green-500/15 text-green-600'
                      : step.status === 'CURRENT'
                      ? 'bg-blue-500/15 text-blue-600'
                      : 'bg-muted text-muted-foreground'
                  }`}
                >
                  {step.status}
                </span>
              )}
            </div>

            <button
              onClick={onClose}
              className="btn btn-ghost btn-icon ml-4"
              aria-label="Close modal"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="border-b border-border flex-shrink-0">
          <div className="tab-bar">
            {tabs.map(({ key, label, icon: Icon, disabled }) => (
              <button
                key={key}
                onClick={() => !disabled && setActiveTab(key)}
                disabled={disabled}
                className={`tab-item flex items-center gap-2 ${
                  activeTab === key ? 'tab-item-active' : ''
                } ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div className="modal-body flex-1 overflow-y-auto scrollbar-thin">
          {/* Overview Tab */}
          {activeTab === 'overview' && (
            <div className="space-y-4 animate-fade-in">
              {/* AI Description */}
              {step.aiDescription && (
                <div className="card card-body">
                  <h3 className="font-semibold mb-2 flex items-center gap-2">
                    <Info className="h-4 w-4 text-primary" />
                    About This Place
                  </h3>
                  <p className="text-sm text-muted-foreground leading-relaxed">
                    {step.aiDescription}
                  </p>
                </div>
              )}

              {/* AI Tips */}
              {aiTips.length > 0 && (
                <div className="card card-body bg-gradient-to-br from-indigo-50 to-purple-50 dark:from-indigo-950/30 dark:to-purple-950/30 border-indigo-200 dark:border-indigo-800">
                  <h3 className="font-semibold mb-3 flex items-center gap-2 text-indigo-900 dark:text-indigo-100">
                    <MessageSquare className="h-4 w-4" />
                    AI Travel Tips
                  </h3>
                  <ul className="space-y-2">
                    {aiTips.map((tip, idx) => (
                      <li key={idx} className="flex items-start gap-2 text-sm">
                        <span className="inline-flex items-center justify-center h-5 w-5 rounded-full bg-indigo-200 dark:bg-indigo-800 text-indigo-700 dark:text-indigo-200 text-xs font-semibold flex-shrink-0 mt-0.5">
                          {idx + 1}
                        </span>
                        <span className="text-indigo-900 dark:text-indigo-100">{tip}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* User Notes */}
              {step.notes && (
                <div className="card card-body">
                  <h3 className="font-semibold mb-2 flex items-center gap-2">
                    <MessageSquare className="h-4 w-4 text-primary" />
                    Your Notes
                  </h3>
                  <p className="text-sm text-muted-foreground leading-relaxed whitespace-pre-wrap">
                    {step.notes}
                  </p>
                </div>
              )}

              {/* Location Details */}
              <LocationHierarchyCard step={step} />

              {/* Arrival Date */}
              {step.arrivalDate && (
                <div className="card card-body flex items-center gap-3">
                  <div className="h-10 w-10 rounded-lg bg-green-500/10 flex items-center justify-center">
                    <Calendar className="h-5 w-5 text-green-600 dark:text-green-400" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">Planned Arrival</p>
                    <p className="font-semibold">
                      {new Date(step.arrivalDate).toLocaleDateString('en-US', {
                        weekday: 'short',
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </p>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* POIs Tab */}
          {activeTab === 'pois' && hasPois && (
            <div className="animate-fade-in">
              <PoiDetailGrid
                pois={step.poiNearby!}
                stepLocation={{ lat: step.latitude, lon: step.longitude }}
              />
            </div>
          )}

          {/* Route Details Tab */}
          {activeTab === 'route' && (
            <div className="space-y-4 animate-fade-in">
              <RouteMetricsCard
                step={step}
                cumulativeDistance={cumulativeDistance}
                cumulativeDuration={cumulativeDuration}
                totalDistance={totalDistance}
                totalDuration={totalDuration}
                showCumulative={true}
              />

              {/* Route Geometry Info */}
              {step.routeGeometryFromPrev && (
                <div className="card card-body">
                  <h3 className="font-semibold mb-2 flex items-center gap-2">
                    <Navigation className="h-4 w-4 text-primary" />
                    Route Information
                  </h3>
                  <p className="text-sm text-muted-foreground">
                    {formatRouteGeometry(step.routeGeometryFromPrev.coordinates)}
                  </p>
                  <p className="text-xs text-muted-foreground mt-2">
                    Type: {step.routeGeometryFromPrev.type}
                  </p>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="modal-footer flex-shrink-0 flex items-center justify-between">
          <button onClick={onClose} className="btn btn-secondary">
            Close
          </button>

          {onMarkVisited && step.status !== 'VISITED' && (
            <button
              onClick={() => {
                onMarkVisited(step.id)
                onClose()
              }}
              className="btn btn-primary"
            >
              <CheckCircle2 className="h-4 w-4" />
              Mark as Visited
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
