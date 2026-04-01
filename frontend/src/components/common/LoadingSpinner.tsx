import React from 'react'

interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg'
  color?: string
  fullScreen?: boolean
}

export function LoadingSpinner({ size = 'md', color = 'text-blue-600', fullScreen = false }: LoadingSpinnerProps) {
  const sizeClasses = {
    sm: 'h-4 w-4 border-2',
    md: 'h-8 w-8 border-2',
    lg: 'h-12 w-12 border-4',
  }

  if (fullScreen) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-white/80 backdrop-blur-sm">
        <div className={`h-12 w-12 animate-spin rounded-full border-4 border-blue-600 border-t-transparent`} />
      </div>
    )
  }

  return (
    <div className={`animate-spin rounded-full ${sizeClasses[size]} border-t-transparent ${color}`} />
  )
}
