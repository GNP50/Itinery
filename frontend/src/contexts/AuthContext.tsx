import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import type { AnonymousSession, SavedItinerary } from '../types/session'
import {
  loadSessions,
  saveSession as saveSess,
  createAnonymousSession,
  switchToSession,
  getCurrentSession,
  deleteSession,
  clearAllAnonymousSessions,
  migrateLegacySession,
  addItineraryToSession,
  removeItineraryFromSession,
  updateItineraryInSession,
} from '../utils/sessionStorage'

// ─── Types ───────────────────────────────────────────────────────────────────

export interface AuthUser {
  userId: string
  userType: 'REGISTERED' | 'ANONYMOUS' | 'ADMIN'
  sessionId?: string // For anonymous users only
}

interface AuthContextType {
  user: AuthUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, name: string) => Promise<void>
  createAnonymous: () => Promise<void>
  logout: () => void
  isAuthenticated: boolean

  // Multi-session management (anonymous only)
  anonymousSessions: AnonymousSession[]
  currentAnonymousSession: AnonymousSession | null
  switchAnonymousSession: (sessionId: string) => void
  deleteAnonymousSession: (sessionId: string) => void
  clearAllAnonymous: () => void

  // Itinerary management in current session
  addItineraryToCurrentSession: (itinerary: SavedItinerary) => void
  removeItineraryFromCurrentSession: (itineraryId: string) => void
  updateItineraryInCurrentSession: (itineraryId: string, updates: Partial<SavedItinerary>) => void
  getCurrentSessionItineraries: () => SavedItinerary[]
}

// ─── Storage helpers for REGISTERED/ADMIN ─────────────────────────────────────

const REGISTERED_TOKEN_KEY = 'itinerary_registered_token'
const REGISTERED_USER_KEY  = 'itinerary_registered_user'

function saveRegisteredSession(token: string, user: AuthUser) {
  localStorage.setItem(REGISTERED_TOKEN_KEY, token)
  localStorage.setItem(REGISTERED_USER_KEY, JSON.stringify(user))
}

function clearRegisteredSession() {
  localStorage.removeItem(REGISTERED_TOKEN_KEY)
  localStorage.removeItem(REGISTERED_USER_KEY)
}

export function getStoredToken(): string | null {
  // Check for registered user token first
  const registeredToken = localStorage.getItem(REGISTERED_TOKEN_KEY)
  if (registeredToken) return registeredToken

  // Fall back to current anonymous session JWT
  const session = getCurrentSession()
  return session?.jwt || null
}

function loadStoredRegisteredUser(): AuthUser | null {
  const raw = localStorage.getItem(REGISTERED_USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

// ─── Context ──────────────────────────────────────────────────────────────────

const AuthContext = createContext<AuthContextType | null>(null)

// ─── Provider ─────────────────────────────────────────────────────────────────

interface AuthProviderProps {
  children: React.ReactNode
  baseURL: string
}

export function AuthProvider({ children, baseURL }: AuthProviderProps) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [anonymousSessions, setAnonymousSessions] = useState<AnonymousSession[]>([])
  const [currentAnonymousSession, setCurrentAnonymousSession] = useState<AnonymousSession | null>(null)

  // Restore session from storage on mount
  useEffect(() => {
    // Migrate legacy single-token storage
    migrateLegacySession()

    // Check for registered user
    const registeredUser = loadStoredRegisteredUser()
    if (registeredUser && (registeredUser.userType === 'REGISTERED' || registeredUser.userType === 'ADMIN')) {
      setUser(registeredUser)
      setLoading(false)
      return
    }

    // Otherwise, load anonymous sessions
    const storage = loadSessions()
    const sessions = Object.values(storage.sessions)
    setAnonymousSessions(sessions)

    // Restore current anonymous session if any
    const current = getCurrentSession()
    if (current) {
      setCurrentAnonymousSession(current)
      setUser({
        userId: current.userId,
        userType: 'ANONYMOUS',
        sessionId: current.id,
      })
    }

    setLoading(false)
  }, [])

  const authPost = useCallback(
    async (path: string, body?: unknown): Promise<{ userId: string; accessToken: string; userType: string }> => {
      const res = await fetch(`${baseURL}${path}`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    body ? JSON.stringify(body) : undefined,
      })
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }))
        throw new Error(err.message ?? 'Authentication failed')
      }
      return res.json()
    },
    [baseURL]
  )

  const login = useCallback(
    async (email: string, password: string) => {
      const data = await authPost('/api/v1/auth/login', { email, password })
      const authUser: AuthUser = { userId: data.userId, userType: data.userType as AuthUser['userType'] }

      // Don't clear anonymous sessions - just hide them while admin is signed in
      // They'll be restored on logout
      setAnonymousSessions([])
      setCurrentAnonymousSession(null)

      saveRegisteredSession(data.accessToken, authUser)
      setUser(authUser)
    },
    [authPost]
  )

  const register = useCallback(
    async (email: string, password: string, name: string) => {
      const data = await authPost('/api/v1/auth/register', { email, password, name })
      const authUser: AuthUser = { userId: data.userId, userType: data.userType as AuthUser['userType'] }

      // When registering a new account, clear anonymous sessions (user is transitioning permanently)
      clearAllAnonymousSessions()
      setAnonymousSessions([])
      setCurrentAnonymousSession(null)

      saveRegisteredSession(data.accessToken, authUser)
      setUser(authUser)
    },
    [authPost]
  )

  const createAnonymous = useCallback(async () => {
    const data = await authPost('/api/v1/auth/anonymous')
    const session = createAnonymousSession(data.userId, data.accessToken)

    saveSess(session)

    // Reload all sessions
    const storage = loadSessions()
    const sessions = Object.values(storage.sessions)
    setAnonymousSessions(sessions)
    setCurrentAnonymousSession(session)

    setUser({
      userId: session.userId,
      userType: 'ANONYMOUS',
      sessionId: session.id,
    })
  }, [authPost])

  const switchAnonymousSession = useCallback((sessionId: string) => {
    const session = switchToSession(sessionId)
    if (session) {
      setCurrentAnonymousSession(session)
      setUser({
        userId: session.userId,
        userType: 'ANONYMOUS',
        sessionId: session.id,
      })
      
      // Reload sessions to update list
      const storage = loadSessions()
      setAnonymousSessions(Object.values(storage.sessions))
    }
  }, [])

  const deleteAnonymousSession = useCallback((sessionId: string) => {
    deleteSession(sessionId)

    // Reload sessions
    const storage = loadSessions()
    const sessions = Object.values(storage.sessions)
    setAnonymousSessions(sessions)

    // If we deleted the current session, clear user
    if (currentAnonymousSession?.id === sessionId) {
      setCurrentAnonymousSession(null)
      setUser(null)
    }
  }, [currentAnonymousSession])

  const clearAllAnonymous = useCallback(() => {
    clearAllAnonymousSessions()
    setAnonymousSessions([])
    setCurrentAnonymousSession(null)
    setUser(null)
  }, [])

  const addItineraryToCurrentSession = useCallback((itinerary: SavedItinerary) => {
    if (!currentAnonymousSession) return
    
    addItineraryToSession(currentAnonymousSession.id, itinerary)
    
    // Reload current session
    const session = getCurrentSession()
    if (session) {
      setCurrentAnonymousSession(session)
      // Update in the list too
      const storage = loadSessions()
      setAnonymousSessions(Object.values(storage.sessions))
    }
  }, [currentAnonymousSession])

  const removeItineraryFromCurrentSession = useCallback((itineraryId: string) => {
    if (!currentAnonymousSession) return
    
    removeItineraryFromSession(currentAnonymousSession.id, itineraryId)
    
    // Reload current session
    const session = getCurrentSession()
    if (session) {
      setCurrentAnonymousSession(session)
      // Update in the list too
      const storage = loadSessions()
      setAnonymousSessions(Object.values(storage.sessions))
    }
  }, [currentAnonymousSession])

  const updateItineraryInCurrentSession = useCallback((itineraryId: string, updates: Partial<SavedItinerary>) => {
    if (!currentAnonymousSession) return
    
    updateItineraryInSession(currentAnonymousSession.id, itineraryId, updates)
    
    // Reload current session
    const session = getCurrentSession()
    if (session) {
      setCurrentAnonymousSession(session)
      // Update in the list too
      const storage = loadSessions()
      setAnonymousSessions(Object.values(storage.sessions))
    }
  }, [currentAnonymousSession])

  const getCurrentSessionItineraries = useCallback(() => {
    return currentAnonymousSession?.itineraries || []
  }, [currentAnonymousSession])

  const logout = useCallback(() => {
    if (user?.userType === 'REGISTERED' || user?.userType === 'ADMIN') {
      clearRegisteredSession()

      // Restore anonymous sessions after admin logout
      const storage = loadSessions()
      const sessions = Object.values(storage.sessions)
      setAnonymousSessions(sessions)

      // Restore current anonymous session if any
      const current = getCurrentSession()
      if (current) {
        setCurrentAnonymousSession(current)
        setUser({
          userId: current.userId,
          userType: 'ANONYMOUS',
          sessionId: current.id,
        })
        return
      }
    } else {
      // For anonymous, just clear current session reference
      // (keep sessions saved for future use)
      setCurrentAnonymousSession(null)
    }
    setUser(null)
  }, [user])

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login,
        register,
        createAnonymous,
        logout,
        isAuthenticated: user !== null,
        anonymousSessions,
        currentAnonymousSession,
        switchAnonymousSession,
        deleteAnonymousSession,
        clearAllAnonymous,
        addItineraryToCurrentSession,
        removeItineraryFromCurrentSession,
        updateItineraryInCurrentSession,
        getCurrentSessionItineraries,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
