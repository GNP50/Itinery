import React, { useState } from 'react'
import { Navigation, RefreshCw, Sparkles, AlertCircle } from 'lucide-react'
import { toast } from 'react-hot-toast'

interface OptimizedStep {
  originalIndex: number
  newIndex: number
  placeName: string
}

interface AiRouteOptimizerProps {
  steps: Array<{ id: string; placeName: string }>
  onOptimize: () => Promise<{ optimizedOrder: OptimizedStep[]; reasoning: string }>
  disabled?: boolean
}

export function AiRouteOptimizer({ steps, onOptimize, disabled = false }: AiRouteOptimizerProps) {
  const [isOptimizing, setIsOptimizing] = useState(false)
  const [result, setResult] = useState<{ optimizedOrder: OptimizedStep[]; reasoning: string } | null>(null)

  const handleOptimize = async () => {
    setIsOptimizing(true)
    setResult(null)

    try {
      const result = await onOptimize()
      setResult(result)
      toast.success('Route optimized! Updated order shown below.')
    } catch (error) {
      toast.error('Failed to optimize route')
    } finally {
      setIsOptimizing(false)
    }
  }

  if (steps.length < 2) return null

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <div className="p-4 border-b bg-gray-50/50">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-100">
              <Navigation className="h-4 w-4 text-purple-600" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">AI Route Optimization</h3>
              <p className="text-xs text-gray-500">
                Optimize your stop order for minimum travel time
              </p>
            </div>
          </div>
          <button
            onClick={handleOptimize}
            disabled={isOptimizing || disabled}
            className="flex items-center gap-2 rounded-lg bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${isOptimizing ? 'animate-spin' : ''}`} />
            {isOptimizing ? 'Optimizing...' : 'Optimize'}
          </button>
        </div>
      </div>

      {result && (
        <div className="p-4">
          <div className="mb-4 rounded-lg bg-green-50 p-3">
            <div className="flex items-center gap-2 text-green-700 mb-2">
              <Sparkles className="h-4 w-4" />
              <span className="font-medium">Optimization Complete</span>
            </div>
            <p className="text-sm text-green-800">{result.reasoning}</p>
          </div>

          <div className="space-y-2">
            <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
              New Order
            </h4>
            {result.optimizedOrder.map((step, index) => (
              <div
                key={index}
                className="flex items-center gap-3 rounded-lg bg-gray-50 p-3"
              >
                <div className="flex h-6 w-6 items-center justify-center rounded-full bg-purple-600 text-white text-xs font-bold">
                  {step.newIndex + 1}
                </div>
                <span className="flex-1 font-medium text-gray-900">{step.placeName}</span>
                {step.originalIndex !== step.newIndex && (
                  <div className="flex items-center gap-1 text-xs text-purple-600">
                    <span className="text-gray-400">was #{step.originalIndex + 1}</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {!result && !isOptimizing && (
        <div className="border-t p-4">
          <div className="flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-gray-400 mt-0.5" />
            <div className="flex-1">
              <h4 className="text-sm font-medium text-gray-900">How it works</h4>
              <p className="text-xs text-gray-500 mt-1">
                Our AI analyzes all your stops and calculates the most efficient route based on travel time between each location.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
