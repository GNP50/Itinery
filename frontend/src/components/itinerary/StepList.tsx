import React, { useState } from 'react'
import { Step } from '../../types/step'

interface StepListProps {
  steps: Step[]
  onChange: (steps: Step[]) => void
}

export function StepList({ steps, onChange }: StepListProps) {
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null)

  const handleDragStart = (index: number) => {
    setDraggedIndex(index)
  }

  const handleDragEnd = (overIndex: number | null) => {
    if (draggedIndex !== null && overIndex !== null && draggedIndex !== overIndex) {
      const newSteps = Array.from(steps)
      const [movedStep] = newSteps.splice(draggedIndex, 1)
      newSteps.splice(overIndex, 0, movedStep)
      onChange(newSteps)
    }
    setDraggedIndex(null)
  }

  return (
    <div>
      {steps.map((step, index) => (
        <div
          key={step.id}
          draggable
          onDragStart={() => handleDragStart(index)}
          onDragEnd={() => handleDragEnd(null)}
          onDragOver={(e) => {
            e.preventDefault()
            const target = e.target as HTMLElement
            const rect = target.getBoundingClientRect()
            const y = e.clientY - rect.top
            if (y < rect.height / 2) {
              handleDragEnd(index)
            } else {
              handleDragEnd(index + 1)
            }
          }}
          className={`mb-2 rounded-lg border border-gray-200 bg-white p-4 shadow-sm cursor-move transition-colors ${
            draggedIndex === index ? 'bg-blue-50 border-blue-300' : 'hover:border-gray-300'
          }`}
        >
          <div className="flex items-start gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-600 font-semibold">
              {index + 1}
            </div>
            <div className="flex-1">
              <h4 className="font-medium text-gray-900">{step.placeName}</h4>
              {step.city && (
                <p className="text-sm text-gray-500">
                  {step.city}
                  {step.province ? `, ${step.province}` : ''}
                  {step.country ? `, ${step.country}` : ''}
                </p>
              )}
              {step.notes && <p className="mt-1 text-sm text-gray-600 italic">{step.notes}</p>}
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
