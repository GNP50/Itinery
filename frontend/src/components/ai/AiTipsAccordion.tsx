import React, { useState } from 'react'
import { ChevronDown, ChevronUp, MapPin, Clock, Info } from 'lucide-react'

interface AiTipsAccordionProps {
  tips: string[]
  title?: string
}

export function AiTipsAccordion({ tips, title = 'AI Tips' }: AiTipsAccordionProps) {
  const [isOpen, setIsOpen] = useState(false)

  if (!tips || tips.length === 0) return null

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex w-full items-center justify-between px-4 py-3 hover:bg-gray-50"
      >
        <div className="flex items-center gap-2">
          <div className="flex h-6 w-6 items-center justify-center rounded-full bg-purple-100">
            <MapPin className="h-3 w-3 text-purple-600" />
          </div>
          <span className="font-medium text-gray-900">{title}</span>
        </div>
        {isOpen ? (
          <ChevronUp className="h-4 w-4 text-gray-500" />
        ) : (
          <ChevronDown className="h-4 w-4 text-gray-500" />
        )}
      </button>

      {isOpen && (
        <div className="border-t bg-gray-50/50 px-4 pb-4 pt-3">
          <div className="space-y-3">
            {tips.map((tip, index) => (
              <div key={index} className="flex items-start gap-3">
                <div className="mt-1 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-purple-100 text-xs font-medium text-purple-700">
                  {index + 1}
                </div>
                <p className="text-sm text-gray-700 leading-relaxed">{tip}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
