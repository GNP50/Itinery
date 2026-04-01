import React, { useState } from 'react'
import {
  Clock,
  Info,
  List,
  ChevronDown,
  ChevronUp,
  Navigation,
  Calendar,
  Eye,
  MessageSquare,
  MapPin,
} from 'lucide-react'
import type { ItineraryStep } from '../../types/itinerary'
import { PoiTableModal } from './PoiTableModal'
import { RouteMetricsCard } from './RouteMetricsCard'

interface StepCardProps {
  step: ItineraryStep
  index: number
  isExpanded?: boolean
  onExpand?: () => void
  onViewDetails?: (step: ItineraryStep) => void
  cumulativeDistance?: number
  cumulativeDuration?: number
}

export function StepCard({
  step,
  index,
  isExpanded,
  onExpand,
  onViewDetails,
  cumulativeDistance,
  cumulativeDuration,
}: StepCardProps) {
  const [expanded, setExpanded] = useState(isExpanded || false)
  const [showAllTips, setShowAllTips] = useState(false)
  const [isPoiModalOpen, setIsPoiModalOpen] = useState(false)

  const toggleExpand = () => {
    const newExpanded = !expanded
    setExpanded(newExpanded)
    onExpand?.()
  }

  const statusColor = {
    PENDING: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
    CURRENT: 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300',
    VISITED: 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300',
    SKIPPED: 'bg-gray-50 text-gray-500 dark:bg-gray-900 dark:text-gray-500',
  }

  // Parse AI tips
  let aiTips: string[] = []
  if (step.aiTips) {
    try {
      aiTips = JSON.parse(step.aiTips)
    } catch {
      aiTips = [step.aiTips]
    }
  }

  const tipsToShow = showAllTips ? aiTips : aiTips.slice(0, 3)
  const hasMoreTips = aiTips.length > 3

  return (
    <div className={`card ${expanded ? 'border-primary/30 bg-primary/5' : ''} transition-all animate-fade-in`}>
      {/* Header */}
      <div
        className="flex items-start gap-4 p-4 cursor-pointer group"
        onClick={toggleExpand}
      >
        <div
          className={`
          flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl text-sm font-bold transition-transform group-hover:scale-105
          ${
            step.status === 'VISITED'
              ? 'bg-green-600 text-white'
              : step.status === 'CURRENT'
              ? 'bg-blue-600 text-white'
              : step.status === 'SKIPPED'
              ? 'bg-gray-400 text-white'
              : 'bg-gray-200 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
          }
        `}
        >
          {index + 1}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0 flex-1">
              <h3 className="font-semibold text-foreground">{step.placeName}</h3>
              <p className="text-sm text-muted-foreground truncate">
                {[step.city, step.region, step.country].filter(Boolean).join(', ')}
              </p>
            </div>
            <span
              className={`rounded-full px-2.5 py-1 text-xs font-medium flex-shrink-0 ${
                statusColor[step.status as keyof typeof statusColor]
              }`}
            >
              {step.status}
            </span>
          </div>

          {(step.distanceFromPrevKm || step.durationFromPrevMin) && (
            <div className="mt-2 flex items-center gap-3 text-xs text-muted-foreground">
              {step.distanceFromPrevKm && (
                <div className="flex items-center gap-1">
                  <Navigation className="h-3 w-3" />
                  <span>{step.distanceFromPrevKm.toFixed(1)} km</span>
                </div>
              )}
              {step.durationFromPrevMin && (
                <div className="flex items-center gap-1">
                  <Clock className="h-3 w-3" />
                  <span>
                    {step.durationFromPrevMin >= 60
                      ? `${Math.floor(step.durationFromPrevMin / 60)}h ${Math.round(step.durationFromPrevMin % 60)}m`
                      : `${step.durationFromPrevMin}m`}
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Custom Preferences Badge */}
          {step.preferences && (
            <div className="mt-2">
              <span className="inline-flex items-center px-2 py-1 rounded text-xs bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300 font-medium">
                <svg className="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                Custom preferences
              </span>
              {step.preferences.interests && step.preferences.interests.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {step.preferences.interests.map((interest) => (
                    <span
                      key={interest}
                      className="px-2 py-0.5 rounded-full text-xs bg-purple-50 text-purple-700 dark:bg-purple-900/20 dark:text-purple-300"
                    >
                      {interest}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="mt-1">
          {expanded ? (
            <ChevronUp className="h-5 w-5 text-muted-foreground group-hover:text-foreground transition-colors" />
          ) : (
            <ChevronDown className="h-5 w-5 text-muted-foreground group-hover:text-foreground transition-colors" />
          )}
        </div>
      </div>

      {/* Expanded Content */}
      {expanded && (
        <div className="border-t border-border p-4 space-y-4 animate-fade-in">
          {/* Route Metrics Card */}
          {(step.distanceFromPrevKm || step.durationFromPrevMin) && (
            <RouteMetricsCard
              step={step}
              cumulativeDistance={cumulativeDistance}
              cumulativeDuration={cumulativeDuration}
              showCumulative={false}
            />
          )}

          {/* AI Description */}
          {step.aiDescription && (
            <div className="card card-body">
              <h4 className="flex items-center gap-2 text-sm font-semibold mb-2">
                <Info className="h-4 w-4 text-primary" />
                About This Place
              </h4>
              <p className="text-sm text-muted-foreground leading-relaxed">{step.aiDescription}</p>
            </div>
          )}

          {/* AI Tips */}
          {aiTips.length > 0 && (
            <div className="rounded-xl bg-gradient-to-br from-indigo-50 to-purple-50 dark:from-indigo-950/30 dark:to-purple-950/30 border border-indigo-200 dark:border-indigo-800 p-4">
              <h4 className="flex items-center gap-2 text-sm font-semibold mb-3 text-indigo-900 dark:text-indigo-100">
                <MessageSquare className="h-4 w-4" />
                AI Travel Tips
              </h4>
              <ul className="space-y-2">
                {tipsToShow.map((tip, idx) => (
                  <li key={idx} className="flex items-start gap-2 text-sm">
                    <span className="inline-flex items-center justify-center h-5 w-5 rounded-full bg-indigo-200 dark:bg-indigo-800 text-indigo-700 dark:text-indigo-200 text-xs font-semibold flex-shrink-0 mt-0.5">
                      {idx + 1}
                    </span>
                    <span className="text-indigo-900 dark:text-indigo-100">{tip}</span>
                  </li>
                ))}
              </ul>
              {hasMoreTips && (
                <button
                  onClick={() => setShowAllTips(!showAllTips)}
                  className="mt-3 text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
                >
                  {showAllTips ? 'Show less' : `Show ${aiTips.length - 3} more tips`}
                </button>
              )}
            </div>
          )}

          {/* Notes */}
          {step.notes && (
            <div className="card card-body">
              <h4 className="flex items-center gap-2 text-sm font-semibold mb-2">
                <List className="h-4 w-4 text-primary" />
                Your Notes
              </h4>
              <p className="text-sm text-muted-foreground italic leading-relaxed whitespace-pre-wrap">
                "{step.notes}"
              </p>
            </div>
          )}

          {/* POI Nearby Button */}
          {step.poiNearby && step.poiNearby.length > 0 && (
            <div className="rounded-xl border border-blue-200 dark:border-blue-800 bg-gradient-to-br from-blue-50 to-indigo-50 dark:from-blue-950/30 dark:to-indigo-950/30 p-4">
              <div className="flex items-center gap-2 mb-2">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 dark:bg-blue-500">
                  <MapPin className="h-4 w-4 text-white" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-blue-900 dark:text-blue-100">Nearby Points of Interest</h4>
                  <p className="text-xs text-blue-700 dark:text-blue-300">{step.poiNearby.length} locations found nearby</p>
                </div>
              </div>
              <button
                onClick={() => setIsPoiModalOpen(true)}
                className="mt-3 w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium bg-blue-600 hover:bg-blue-700 text-white transition-colors shadow-sm"
              >
                <MapPin className="h-4 w-4" />
                Explore All POIs
              </button>
            </div>
          )}

          {/* Arrival Date */}
          {step.arrivalDate && (
            <div className="flex items-center gap-3 p-3 rounded-lg bg-muted/50">
              <Calendar className="h-4 w-4 text-green-600 dark:text-green-400" />
              <div>
                <p className="text-xs text-muted-foreground">Planned Arrival</p>
                <p className="text-sm font-medium">
                  {new Date(step.arrivalDate).toLocaleDateString('en-US', {
                    weekday: 'short',
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </p>
              </div>
            </div>
          )}

          {/* View Details Button */}
          {onViewDetails && (
            <button
              onClick={(e) => {
                e.stopPropagation()
                onViewDetails(step)
              }}
              className="btn btn-secondary w-full"
            >
              <Eye className="h-4 w-4" />
              View Full Details
            </button>
          )}
        </div>
      )}

      {/* POI Table Modal */}
      {step.poiNearby && step.poiNearby.length > 0 && (
        <PoiTableModal
          isOpen={isPoiModalOpen}
          pois={step.poiNearby}
          stepLocation={{ lat: step.latitude, lon: step.longitude }}
          onClose={() => setIsPoiModalOpen(false)}
        />
      )}
    </div>
  )
}
