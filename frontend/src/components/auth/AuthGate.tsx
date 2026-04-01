import React, { useState } from 'react'
import { useAuth } from '../../contexts/AuthContext'
import { LoginForm } from './LoginForm'
import { RegisterForm } from './RegisterForm'
import { MapPin, UserCircle, Ghost } from 'lucide-react'

type Mode = 'choose' | 'login' | 'register'

interface AuthGateProps {
  /** When true, renders without the full-screen wrapper (use inside a modal) */
  embedded?: boolean
  /** Called after a successful auth action in embedded mode */
  onDone?: () => void
}

/**
 * Auth gate: full-screen on first load, or embedded inside a modal for opt-in sign-in.
 */
export function AuthGate({ embedded = false, onDone }: AuthGateProps = {}) {
  const { createAnonymous } = useAuth()
  const [mode, setMode]       = useState<Mode>('choose')
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState<string | null>(null)

  const handleAnonymous = async () => {
    setError(null)
    setLoading(true)
    try {
      await createAnonymous()
      onDone?.()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to start guest session')
    } finally {
      setLoading(false)
    }
  }

  const card = (
    <div className="bg-white rounded-2xl shadow-lg p-6">
      {mode === 'choose' && (
        <div className="space-y-4">
          <h2 className="text-xl font-semibold text-gray-800 text-center mb-6">
            {embedded ? 'Sign in to your account' : 'How would you like to continue?'}
          </h2>

          {/* Registered account option */}
          <button
            onClick={() => setMode('login')}
            className="w-full flex items-center gap-4 p-4 border-2 border-blue-200 rounded-xl hover:border-blue-500 hover:bg-blue-50 transition-colors text-left"
          >
            <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0">
              <UserCircle className="w-6 h-6 text-blue-600" />
            </div>
            <div>
              <p className="font-medium text-gray-900">Sign in / Register</p>
              <p className="text-sm text-gray-500">Save your itineraries across sessions</p>
            </div>
          </button>

          {/* Guest option — hidden in embedded mode since they're already a guest */}
          {!embedded && (
            <button
              onClick={handleAnonymous}
              disabled={loading}
              className="w-full flex items-center gap-4 p-4 border-2 border-gray-200 rounded-xl hover:border-gray-400 hover:bg-gray-50 transition-colors text-left disabled:opacity-50"
            >
              <div className="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center flex-shrink-0">
                <Ghost className="w-6 h-6 text-gray-500" />
              </div>
              <div>
                <p className="font-medium text-gray-900">Continue as guest</p>
                <p className="text-sm text-gray-500">
                  No account needed — save the link to return later
                </p>
              </div>
            </button>
          )}

          {error && (
            <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{error}</p>
          )}
        </div>
      )}

      {mode === 'login' && (
        <div>
          <div className="flex items-center gap-2 mb-6">
            <button
              onClick={() => setMode('choose')}
              className="text-gray-400 hover:text-gray-600 transition-colors"
            >
              ←
            </button>
            <h2 className="text-xl font-semibold text-gray-800">Sign in</h2>
          </div>
          <LoginForm onSwitchToRegister={() => setMode('register')} onSuccess={onDone} />
        </div>
      )}

      {mode === 'register' && (
        <div>
          <div className="flex items-center gap-2 mb-6">
            <button
              onClick={() => setMode('choose')}
              className="text-gray-400 hover:text-gray-600 transition-colors"
            >
              ←
            </button>
            <h2 className="text-xl font-semibold text-gray-800">Create account</h2>
          </div>
          <RegisterForm onSwitchToLogin={() => setMode('login')} onSuccess={onDone} />
        </div>
      )}
    </div>
  )

  if (embedded) {
    return card
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-blue-600 text-white mb-4">
            <MapPin className="w-8 h-8" />
          </div>
          <h1 className="text-3xl font-bold text-gray-900">ItineryViewer</h1>
          <p className="mt-2 text-gray-600">Plan and visualise your travel itineraries</p>
        </div>
        {card}
      </div>
    </div>
  )
}
