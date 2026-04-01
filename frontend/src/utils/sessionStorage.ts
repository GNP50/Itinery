import type { AnonymousSession, SavedItinerary, SessionStorage } from '../types/session'

const SESSIONS_KEY = 'itinerary_sessions'
const SINGLE_TOKEN_KEY = 'itinerary_auth_token' // Legacy key for migration
const SINGLE_USER_KEY = 'itinerary_auth_user'   // Legacy key for migration

/**
 * Loads all sessions from localStorage.
 */
export function loadSessions(): SessionStorage {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    if (raw) {
      return JSON.parse(raw) as SessionStorage
    }
  } catch (err) {
    console.warn('Failed to load sessions from localStorage:', err)
  }

  // Return default empty structure
  return {
    currentSessionId: null,
    sessions: {},
  }
}

/**
 * Saves all sessions to localStorage.
 */
export function saveSessions(storage: SessionStorage): void {
  try {
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(storage))
  } catch (err) {
    console.error('Failed to save sessions to localStorage:', err)
  }
}

/**
 * Creates a new anonymous session.
 */
export function createAnonymousSession(userId: string, jwt: string): AnonymousSession {
  const now = new Date().toISOString()
  return {
    id: `anon_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
    userId,
    jwt,
    userType: 'ANONYMOUS',
    itineraries: [],
    createdAt: now,
    lastUsedAt: now,
  }
}

/**
 * Adds or updates a session in storage and sets it as current.
 */
export function saveSession(session: AnonymousSession): void {
  const storage = loadSessions()
  storage.sessions[session.id] = {
    ...session,
    lastUsedAt: new Date().toISOString(),
  }
  storage.currentSessionId = session.id
  saveSessions(storage)
}

/**
 * Switches to an existing session by ID.
 */
export function switchToSession(sessionId: string): AnonymousSession | null {
  const storage = loadSessions()
  const session = storage.sessions[sessionId]
  if (!session) return null

  storage.currentSessionId = sessionId
  storage.sessions[sessionId] = {
    ...session,
    lastUsedAt: new Date().toISOString(),
  }
  saveSessions(storage)
  return session
}

/**
 * Returns the currently active session, if any.
 */
export function getCurrentSession(): AnonymousSession | null {
  const storage = loadSessions()
  if (!storage.currentSessionId) return null
  return storage.sessions[storage.currentSessionId] || null
}

/**
 * Adds an itinerary to a session.
 */
export function addItineraryToSession(sessionId: string, itinerary: SavedItinerary): void {
  const storage = loadSessions()
  const session = storage.sessions[sessionId]
  if (!session) return

  // Avoid duplicates
  const exists = session.itineraries.some((it) => it.id === itinerary.id)
  if (!exists) {
    session.itineraries.push(itinerary)
    session.lastUsedAt = new Date().toISOString()
    saveSessions(storage)
  }
}

/**
 * Removes an itinerary from a session.
 */
export function removeItineraryFromSession(sessionId: string, itineraryId: string): void {
  const storage = loadSessions()
  const session = storage.sessions[sessionId]
  if (!session) return

  session.itineraries = session.itineraries.filter((it) => it.id !== itineraryId)
  saveSessions(storage)
}

/**
 * Updates an itinerary in a session.
 */
export function updateItineraryInSession(sessionId: string, itineraryId: string, updates: Partial<SavedItinerary>): void {
  const storage = loadSessions()
  const session = storage.sessions[sessionId]
  if (!session) return

  const idx = session.itineraries.findIndex((it) => it.id === itineraryId)
  if (idx >= 0) {
    session.itineraries[idx] = { ...session.itineraries[idx], ...updates }
    session.lastUsedAt = new Date().toISOString()
    saveSessions(storage)
  }
}

/**
 * Deletes a session by ID.
 */
export function deleteSession(sessionId: string): void {
  const storage = loadSessions()
  delete storage.sessions[sessionId]
  if (storage.currentSessionId === sessionId) {
    storage.currentSessionId = null
  }
  saveSessions(storage)
}

/**
 * Clears all anonymous sessions (keeps registered user sessions if any).
 */
export function clearAllAnonymousSessions(): void {
  const storage = loadSessions()
  Object.keys(storage.sessions).forEach((id) => {
    if (storage.sessions[id].userType === 'ANONYMOUS') {
      delete storage.sessions[id]
    }
  })
  storage.currentSessionId = null
  saveSessions(storage)
}

/**
 * Migrates legacy single-token storage to new multi-session structure.
 * Should be called once on app mount.
 */
export function migrateLegacySession(): void {
  const legacyToken = localStorage.getItem(SINGLE_TOKEN_KEY)
  const legacyUser = localStorage.getItem(SINGLE_USER_KEY)

  if (!legacyToken || !legacyUser) return

  try {
    const user = JSON.parse(legacyUser) as { userId: string; userType: string }
    if (user.userType === 'ANONYMOUS') {
      const session = createAnonymousSession(user.userId, legacyToken)
      saveSession(session)
      console.log('Migrated legacy anonymous session to new storage')
    }
  } catch (err) {
    console.warn('Failed to migrate legacy session:', err)
  } finally {
    // Clean up legacy keys
    localStorage.removeItem(SINGLE_TOKEN_KEY)
    localStorage.removeItem(SINGLE_USER_KEY)
  }
}
