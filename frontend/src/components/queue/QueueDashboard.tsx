import React, { useState, useEffect } from 'react'
import { Loader2, Clock, RefreshCw, XCircle } from 'lucide-react'
import { QueueStatus } from './QueueStatus'

interface QueueItem {
  id: string
  title: string
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  queuePosition?: number
  progress: number
  estimatedCompletion?: string
}

interface QueueDashboardProps {
  queueLength?: number
  processingCount?: number
  items?: QueueItem[]
}

export function QueueDashboard({
  queueLength = 0,
  processingCount = 0,
  items = [],
}: QueueDashboardProps) {
  const [refreshing, setRefreshing] = useState(false)

  const handleRefresh = () => {
    setRefreshing(true)
    setTimeout(() => setRefreshing(false), 1000)
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'QUEUED': return 'bg-yellow-100 text-yellow-700'
      case 'PROCESSING': return 'bg-blue-100 text-blue-700'
      case 'COMPLETED': return 'bg-green-100 text-green-700'
      case 'FAILED': return 'bg-red-100 text-red-700'
      default: return 'bg-gray-100 text-gray-700'
    }
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'QUEUED': return <Clock className="h-4 w-4" />
      case 'PROCESSING': return <Loader2 className="h-4 w-4 animate-spin" />
      case 'COMPLETED': return <RefreshCw className="h-4 w-4" />
      case 'FAILED': return <XCircle className="h-4 w-4" />
      default: return null
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-900">Queue Dashboard</h2>
        <button
          onClick={handleRefresh}
          disabled={refreshing}
          className="flex items-center gap-2 rounded-lg bg-gray-100 px-3 py-2 text-sm font-medium hover:bg-gray-200 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="rounded-xl bg-white p-4 shadow-sm border border-gray-200">
          <p className="text-sm text-gray-500">Queue Length</p>
          <p className="text-2xl font-bold text-gray-900">{queueLength}</p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow-sm border border-gray-200">
          <p className="text-sm text-gray-500">Processing</p>
          <p className="text-2xl font-bold text-blue-600">{processingCount}</p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow-sm border border-gray-200">
          <p className="text-sm text-gray-500">Completed</p>
          <p className="text-2xl font-bold text-green-600">
            {items.filter((i) => i.status === 'COMPLETED').length}
          </p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow-sm border border-gray-200">
          <p className="text-sm text-gray-500">Failed</p>
          <p className="text-2xl font-bold text-red-600">
            {items.filter((i) => i.status === 'FAILED').length}
          </p>
        </div>
      </div>

      {/* Queue Items */}
      <div className="rounded-xl bg-white shadow-sm border border-gray-200 overflow-hidden">
        {items.length === 0 ? (
          <div className="p-8 text-center">
            <div className="mx-auto h-16 w-16 rounded-full bg-gray-100 flex items-center justify-center mb-4">
              <Clock className="h-8 w-8 text-gray-400" />
            </div>
            <p className="text-gray-500">No items in the queue</p>
          </div>
        ) : (
          <div className="divide-y">
            {items.map((item) => (
              <div key={item.id} className="p-4 hover:bg-gray-50">
                <div className="flex items-start gap-4">
                  <div className={`flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg ${getStatusColor(item.status)}`}>
                    {getStatusIcon(item.status)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-2">
                      <h4 className="font-medium text-gray-900 truncate">{item.title}</h4>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${getStatusColor(item.status)}`}>
                        {item.status}
                      </span>
                    </div>
                    {item.queuePosition && (
                      <p className="text-xs text-gray-500 mb-2">
                        Position: {item.queuePosition}
                      </p>
                    )}
                    <div className="h-2 w-full rounded-full bg-gray-200 overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all duration-300 ${
                          item.status === 'PROCESSING' ? 'bg-blue-600' :
                          item.status === 'COMPLETED' ? 'bg-green-600' :
                          item.status === 'FAILED' ? 'bg-red-600' :
                          'bg-yellow-600'
                        }`}
                        style={{ width: `${item.progress}%` }}
                      />
                    </div>
                  </div>
                  {item.estimatedCompletion && (
                    <div className="flex items-center gap-2 text-xs text-gray-500">
                      <Clock className="h-3 w-3" />
                      <span>
                        Est: {new Date(item.estimatedCompletion).toLocaleTimeString()}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
