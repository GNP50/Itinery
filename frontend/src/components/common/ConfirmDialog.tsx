import React from 'react'
import { AlertTriangle, X } from 'lucide-react'

interface ConfirmDialogProps {
  isOpen: boolean
  title: string
  message: string
  onConfirm: () => void
  onCancel: () => void
  confirmText?: string
  cancelText?: string
  variant?: 'default' | 'danger'
}

export function ConfirmDialog({
  isOpen,
  title,
  message,
  onConfirm,
  onCancel,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'default',
}: ConfirmDialogProps) {
  if (!isOpen) return null

  const variantClasses = {
    default: {
      icon: AlertTriangle,
      iconColor: 'text-yellow-600',
      confirmButton: 'bg-blue-600 hover:bg-blue-700',
    },
    danger: {
      icon: AlertTriangle,
      iconColor: 'text-red-600',
      confirmButton: 'bg-red-600 hover:bg-red-700',
    },
  }

  const { icon: Icon, iconColor, confirmButton } = variantClasses[variant]

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
      <div className="w-full max-w-md overflow-hidden rounded-xl bg-white shadow-2xl animate-slide-in">
        <div className="flex items-center justify-between border-b p-6">
          <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
          <button
            onClick={onCancel}
            className="rounded-lg p-1 hover:bg-gray-100 text-gray-500"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="p-6">
          <div className="flex items-start gap-4">
            <div className={`flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full ${variant === 'danger' ? 'bg-red-100' : 'bg-yellow-100'}`}>
              <Icon className={`h-6 w-6 ${iconColor}`} />
            </div>
            <div className="flex-1">
              <p className="text-gray-600 leading-relaxed">{message}</p>
            </div>
          </div>
        </div>
        <div className="border-t bg-gray-50 p-6">
          <div className="flex items-center justify-end gap-3">
            <button
              onClick={onCancel}
              className="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
            >
              {cancelText}
            </button>
            <button
              onClick={onConfirm}
              className={`rounded-lg px-4 py-2 text-sm font-medium text-white ${confirmButton}`}
            >
              {confirmText}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
