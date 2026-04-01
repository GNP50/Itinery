/**
 * QueueMetrics Component
 * Displays separate metrics for registered and anonymous queues
 */

import React from 'react'
import { Users, UserCheck, Clock, Activity, TrendingUp } from 'lucide-react'
import { useQueueStatistics, useQueueStore, useQueueUtilization } from '../../store/queueStore'

interface QueueMetricsProps {
  /** Show detailed breakdown with percentages */
  detailed?: boolean
  
  /** Custom CSS class */
  className?: string
}

/**
 * Component that displays queue metrics with separate counts for
 * registered and anonymous users.
 * 
 * FEATURES:
 * - Real-time updates from queue store
 * - Visual breakdown with icons
 * - Optional detailed view with percentages
 * - Responsive design
 */
export function QueueMetrics({ detailed = false, className = '' }: QueueMetricsProps) {
  const stats = useQueueStatistics()
  const { lastUpdated } = useQueueStore()
  
  if (stats.isEmpty) {
    return (
      <div className={`rounded-xl bg-white p-5 shadow-sm border border-gray-200 ${className}`}>
        <div className="text-center py-4">
          <Clock className="h-8 w-8 text-gray-400 mx-auto mb-2" />
          <p className="text-sm text-gray-500">No items in queue</p>
        </div>
      </div>
    )
  }

  return (
    <div className={`rounded-xl bg-white p-5 shadow-sm border border-gray-200 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-900">Queue Status</h3>
        {lastUpdated && (
          <span className="text-xs text-gray-500">
            Updated {formatTimeAgo(lastUpdated)}
          </span>
        )}
      </div>

      {/* Metrics Grid - 5 cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        {/* Total */}
        <MetricCard
          icon={<Clock className="h-5 w-5" />}
          label="Total Queue"
          value={stats.total}
          color="blue"
          percentage={100}
          detailed={detailed}
        />

        {/* Registered */}
        <MetricCard
          icon={<UserCheck className="h-5 w-5" />}
          label="Registered"
          value={stats.registered}
          color="green"
          percentage={stats.registeredPercentage}
          detailed={detailed}
        />

        {/* Anonymous */}
        <MetricCard
          icon={<Users className="h-5 w-5" />}
          label="Anonymous"
          value={stats.anonymous}
          color="yellow"
          percentage={stats.anonymousPercentage}
          detailed={detailed}
        />

        {/* Queue Capacity */}
        <MetricCard
          icon={<Activity className="h-5 w-5" />}
          label="Queue Capacity"
          value={`${stats.total}/${useQueueStore.getState().maxQueueSize}`}
          color="purple"
          detailed={detailed}
        />

        {/* Processing Slots */}
        <MetricCard
          icon={<TrendingUp className="h-5 w-5" />}
          label="Processing"
          value={`${useQueueStore.getState().processingCount}/${useQueueStore.getState().maxConcurrent}`}
          color="indigo"
          detailed={detailed}
        />
      </div>

      {/* Visual Breakdown */}
      {detailed && stats.total > 0 && (
        <div className="mt-4">
          <div className="flex h-2 rounded-full overflow-hidden bg-gray-100">
            {stats.registered > 0 && (
              <div
                className="bg-green-500"
                style={{ width: `${stats.registeredPercentage}%` }}
                title={`Registered: ${stats.registeredPercentage.toFixed(1)}%`}
              />
            )}
            {stats.anonymous > 0 && (
              <div
                className="bg-yellow-500"
                style={{ width: `${stats.anonymousPercentage}%` }}
                title={`Anonymous: ${stats.anonymousPercentage.toFixed(1)}%`}
              />
            )}
          </div>
          <div className="flex justify-between mt-2 text-xs text-gray-600">
            <span>High Priority: {stats.registeredPercentage.toFixed(1)}%</span>
            <span>Low Priority: {stats.anonymousPercentage.toFixed(1)}%</span>
          </div>
        </div>
      )}

      {/* Queue Utilization Bar */}
      {detailed && (
        <div className="mt-4">
          <div className="flex justify-between mb-2 text-xs text-gray-600">
            <span>Queue Utilization</span>
            <span>{useQueueUtilization().toFixed(1)}%</span>
          </div>
          <div className="h-2 rounded-full overflow-hidden bg-gray-100">
            <div
              className={`h-full transition-all ${
                useQueueUtilization() > 90 ? 'bg-red-500' :
                useQueueUtilization() > 70 ? 'bg-yellow-500' :
                'bg-green-500'
              }`}
              style={{ width: `${Math.min(useQueueUtilization(), 100)}%` }}
            />
          </div>
        </div>
      )}
    </div>
  )
}

// ============================================================================
// Sub-components
// ============================================================================

interface MetricCardProps {
  icon: React.ReactNode
  label: string
  value: number | string
  color: 'blue' | 'green' | 'yellow' | 'purple' | 'indigo'
  percentage?: number
  detailed?: boolean
}

function MetricCard({ icon, label, value, color, percentage, detailed }: MetricCardProps) {
  const colorClasses = {
    blue: 'bg-blue-100 text-blue-600',
    green: 'bg-green-100 text-green-600',
    yellow: 'bg-yellow-100 text-yellow-600',
    purple: 'bg-purple-100 text-purple-600',
    indigo: 'bg-indigo-100 text-indigo-600',
  }

  return (
    <div className="flex items-center gap-3 p-3 rounded-lg border border-gray-200">
      <div className={`flex h-10 w-10 items-center justify-center rounded-full ${colorClasses[color]}`}>
        {icon}
      </div>
      <div className="flex-1">
        <p className="text-xs text-gray-600">{label}</p>
        <div className="flex items-baseline gap-2">
          <p className="text-2xl font-bold text-gray-900">{value}</p>
          {detailed && percentage !== undefined && percentage < 100 && (
            <span className="text-xs text-gray-500">({percentage.toFixed(0)}%)</span>
          )}
        </div>
      </div>
    </div>
  )
}

// ============================================================================
// Utilities
// ============================================================================

function formatTimeAgo(date: Date): string {
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000)
  
  if (seconds < 10) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  
  const hours = Math.floor(minutes / 60)
  return `${hours}h ago`
}

