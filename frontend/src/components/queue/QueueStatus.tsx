import React from 'react'
import { Loader2, Clock, Play } from 'lucide-react'

interface QueueStatusProps {
  status: 'IDLE' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  queuePosition?: number
  estimatedCompletion?: string
  progress?: number
}

const STATUS_COLORS = {
  IDLE: 'text-gray-500 bg-gray-100',
  QUEUED: 'text-yellow-600 bg-yellow-100',
  PROCESSING: 'text-blue-600 bg-blue-100',
  COMPLETED: 'text-green-600 bg-green-100',
  FAILED: 'text-red-600 bg-red-100',
}

const STATUS_LABELS = {
  IDLE: 'Idle',
  QUEUED: 'In Queue',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
}

export function QueueStatus({ status, queuePosition, estimatedCompletion, progress }: QueueStatusProps) {
  return (
    <div className="rounded-xl bg-white p-5 shadow-sm border border-gray-200">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-900">Processing Status</h3>
        <span className={`rounded-full px-3 py-1 text-xs font-medium ${STATUS_COLORS[status]}`}>
          {STATUS_LABELS[status]}
        </span>
      </div>

      <div className="space-y-4">
        {status === 'QUEUED' && queuePosition !== undefined && (
          <div className="flex items-center gap-3 rounded-lg bg-yellow-50 p-3">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-yellow-100 text-yellow-600">
              <Clock className="h-4 w-4" />
            </div>
            <div>
              <p className="text-sm font-medium text-yellow-900">
                You are at position {queuePosition} in the queue
              </p>
              {estimatedCompletion && (
                <p className="text-xs text-yellow-700">
                  Estimated completion: {new Date(estimatedCompletion).toLocaleString()}
                </p>
              )}
            </div>
          </div>
        )}

        {status === 'PROCESSING' && (
          <div className="space-y-2">
            <div className="flex items-center gap-3 rounded-lg bg-blue-50 p-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-blue-600 animate-spin">
                <Loader2 className="h-4 w-4" />
              </div>
              <div>
                <p className="text-sm font-medium text-blue-900">Processing your itinerary</p>
                <p className="text-xs text-blue-700">Please wait while we generate your route...</p>
              </div>
            </div>
          </div>
        )}

        {status === 'COMPLETED' && (
          <div className="flex items-center gap-3 rounded-lg bg-green-50 p-3">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-green-100 text-green-600">
              <Play className="h-4 w-4" />
            </div>
            <p className="text-sm font-medium text-green-900">Your itinerary is ready!</p>
          </div>
        )}

        {status === 'FAILED' && (
          <div className="rounded-lg bg-red-50 p-3">
            <p className="text-sm font-medium text-red-900">Processing failed</p>
            <p className="text-xs text-red-700 mt-1">Please try again later</p>
          </div>
        )}

        {status === 'IDLE' && (
          <div className="text-center py-4">
            <p className="text-sm text-gray-500">No processing status available</p>
          </div>
        )}
      </div>
    </div>
  )
}
