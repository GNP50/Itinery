import React from 'react'
import { Sparkles, MapPin, Info } from 'lucide-react'
import { Step } from '../../types/step'

interface AiSuggestionCardProps {
  step: Step
  showTips?: boolean
}

export function AiSuggestionCard({ step, showTips = true }: AiSuggestionCardProps) {
  if (!step.aiDescription && !step.aiTips) return null

  return (
    <div className="rounded-xl bg-gradient-to-br from-indigo-50 to-purple-50 p-4 border border-indigo-100">
      <div className="flex items-center gap-2 mb-3">
        <div className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-600">
          <Sparkles className="h-3 w-3 text-white" />
        </div>
        <h4 className="text-sm font-semibold text-indigo-900">AI Insights</h4>
      </div>

      {step.aiDescription && (
        <div className="mb-3">
          <p className="text-sm text-gray-700 leading-relaxed">
            {step.aiDescription}
          </p>
        </div>
      )}

      {showTips && step.aiTips && (
        <div className="rounded-lg bg-white/60 p-3">
          <div className="flex items-center gap-2 text-xs font-medium text-indigo-700 mb-2">
            <Info className="h-3 w-3" />
            Tips from AI
          </div>
          <p className="text-sm text-indigo-900">{step.aiTips}</p>
        </div>
      )}

      {step.aiDescription && (
        <div className="mt-3 flex flex-wrap gap-2">
          {step.aiDescription.match(/\b(visit|see|do|explore|try)\b/i) && (
            <span className="rounded-full bg-indigo-100 px-2 py-1 text-xs text-indigo-700">
              Must-do
            </span>
          )}
          {step.aiDescription.match(/\b(restaurant|food|dine|eat)\b/i) && (
            <span className="rounded-full bg-orange-100 px-2 py-1 text-xs text-orange-700">
              Dining
            </span>
          )}
          {step.aiDescription.match(/\b(museum|gallery|art|history)\b/i) && (
            <span className="rounded-full bg-blue-100 px-2 py-1 text-xs text-blue-700">
              Cultural
            </span>
          )}
        </div>
      )}
    </div>
  )
}
