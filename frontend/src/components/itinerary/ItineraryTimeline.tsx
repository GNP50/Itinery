import React from 'react'
import { MapPin, Clock, ChevronRight } from 'lucide-react'
import { Step } from '../../types/step'

interface ItineraryTimelineProps {
  steps: Step[]
  currentStepIndex?: number
  onSelectStep?: (step: Step) => void
}

export function ItineraryTimeline({ steps, currentStepIndex, onSelectStep }: ItineraryTimelineProps) {
  const currentStep = steps[currentStepIndex || 0]

  return (
    <div className="relative pl-8 md:pl-4">
      {/* Timeline line */}
      <div className="absolute left-[15px] top-0 bottom-0 w-0.5 bg-gray-200" />

      {steps.map((step, index) => {
        const isCurrent = step.status === 'CURRENT'
        const isVisited = step.status === 'VISITED'
        const isSkipped = step.status === 'SKIPPED'
        const isPending = step.status === 'PENDING'

        return (
          <div key={step.id} className="relative mb-8">
            {/* Timeline dot */}
            <div
              className={`
                absolute left-0 top-1.5 h-8 w-8 -translate-x-1/2 rounded-full border-4
                flex items-center justify-center text-xs font-bold shadow-sm
                ${isVisited
                  ? 'border-green-500 bg-green-600 text-white'
                  : isCurrent
                  ? 'border-blue-500 bg-blue-600 text-white animate-pulse'
                  : isSkipped
                  ? 'border-gray-400 bg-gray-500 text-white'
                  : 'border-gray-300 bg-white text-gray-500'
                }
              `}
            >
              {index + 1}
            </div>

            {/* Content */}
            <div
              className={`
                rounded-lg border bg-white p-4 transition-all
                ${isCurrent ? 'border-blue-300 bg-blue-50/50 shadow-md' : 'border-gray-200 shadow-sm hover:shadow-md'}
              `}
              onClick={() => onSelectStep?.(step)}
            >
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900">{step.placeName}</h3>
                  <div className="flex items-center gap-2 mt-1 text-sm text-gray-500">
                    <MapPin className="h-4 w-4" />
                    <span>{step.city}{step.region ? `, ${step.region}` : ''}, {step.country}</span>
                  </div>
                  {step.notes && (
                    <p className="mt-2 text-sm text-gray-600 italic">"{step.notes}"</p>
                  )}
                </div>
                <div className="flex flex-col items-end gap-2">
                  <span className={`
                    rounded-full px-2.5 py-1 text-xs font-medium
                    ${isVisited ? 'bg-green-100 text-green-700' :
                      isCurrent ? 'bg-blue-100 text-blue-700' :
                      isSkipped ? 'bg-gray-100 text-gray-700' :
                      'bg-gray-100 text-gray-600'}
                  `}>
                    {step.status}
                  </span>
                  {step.durationFromPrevMin && (
                    <div className="flex items-center gap-3 text-xs text-gray-500">
                      <div className="flex items-center gap-1">
                        <Clock className="h-3 w-3" />
                        <span>{step.durationFromPrevMin} min</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <span>{step.distanceFromPrevKm} km</span>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* AI Description */}
              {step.aiDescription && (
                <div className="mt-3 rounded-lg bg-gray-50 p-3">
                  <p className="text-sm text-gray-700 leading-relaxed">{step.aiDescription}</p>
                </div>
              )}

              {/* AI Tips */}
              {step.aiTips && (
                <div className="mt-2 rounded-lg bg-gradient-to-r from-indigo-50 to-purple-50 p-3">
                  <p className="text-sm text-indigo-900">{step.aiTips}</p>
                </div>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
