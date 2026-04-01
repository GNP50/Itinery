import React from 'react'
import { Loader2, CheckCircle, XCircle } from 'lucide-react'

interface ProcessingProgressProps {
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  currentStep?: number
  totalSteps?: number
  stepsStatus?: Record<string, 'PENDING' | 'COMPLETED' | 'FAILED'>
  processingMessage?: string
}

export function ProcessingProgress({
  status,
  currentStep = 1,
  totalSteps = 0,
  stepsStatus = {},
  processingMessage = 'Processing itinerary...',
}: ProcessingProgressProps) {
  const steps = Array.from({ length: totalSteps }, (_, i) => i + 1)

  const getStepStatusColor = (stepNumber: number) => {
    const stepStatus = stepsStatus[stepNumber.toString()]
    if (stepStatus === 'COMPLETED') return 'bg-green-500 text-white'
    if (stepStatus === 'FAILED') return 'bg-red-500 text-white'
    if (stepNumber === currentStep && status === 'PROCESSING') return 'bg-blue-600 text-white animate-pulse'
    if (stepNumber <= currentStep) return 'bg-green-500 text-white'
    return 'bg-gray-200 text-gray-400'
  }

  return (
    <div className="rounded-xl bg-white p-5 shadow-sm border border-gray-200">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-900">Processing Progress</h3>
        <div className="flex items-center gap-2">
          {status === 'PROCESSING' && (
            <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
          )}
          <span className={`text-sm font-medium ${
            status === 'PROCESSING' ? 'text-blue-600' :
            status === 'COMPLETED' ? 'text-green-600' :
            status === 'FAILED' ? 'text-red-600' :
            'text-gray-500'
          }`}>
            {status}
          </span>
        </div>
      </div>

      {status === 'PROCESSING' && (
        <div className="mb-4 text-sm text-gray-600 flex items-center gap-2">
          <div className="h-2 w-2 rounded-full bg-blue-600 animate-pulse" />
          {processingMessage}
        </div>
      )}

      <div className="space-y-3">
        {steps.map((step) => (
          <div key={step} className="flex items-center gap-3">
            <div
              className={`
                flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-sm font-bold
                ${getStepStatusColor(step)}
              `}
            >
              {step}
            </div>
            <div className="flex-1">
              <div className="h-2 w-full rounded-full bg-gray-200 overflow-hidden">
                <div
                  className="h-full bg-blue-600 transition-all duration-500"
                  style={{
                    width: step < currentStep ? '100%' : status === 'PROCESSING' && step === currentStep ? '50%' : '0%',
                  }}
                />
              </div>
            </div>
            {step === currentStep && status === 'PROCESSING' && (
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-gray-200 border-t-blue-600" />
            )}
          </div>
        ))}
      </div>

      {/* Step Status Legend */}
      <div className="mt-4 flex items-center justify-center gap-4 text-xs text-gray-500">
        <div className="flex items-center gap-1">
          <div className="h-2 w-2 rounded-full bg-green-500" />
          <span>Done</span>
        </div>
        <div className="flex items-center gap-1">
          <div className="h-2 w-2 rounded-full bg-blue-600" />
          <span>Current</span>
        </div>
        <div className="flex items-center gap-1">
          <div className="h-2 w-2 rounded-full bg-gray-200" />
          <span>Pending</span>
        </div>
      </div>
    </div>
  )
}
