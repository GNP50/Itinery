/**
 * Represents a saved anonymous session with its associated itineraries.
 */
export interface AnonymousSession {
  id: string                    // Unique session ID
  userId: string                // User UUID from JWT
  jwt: string                   // JWT token
  userType: 'ANONYMOUS'
  label?: string                // Optional user-defined label
  itineraries: SavedItinerary[] // List of itineraries created in this session
  createdAt: string             // ISO-8601 timestamp
  lastUsedAt: string            // ISO-8601 timestamp
}

/**
 * Itinerary reference saved within an anonymous session.
 */
export interface SavedItinerary {
  id: string          // Itinerary UUID
  accessToken: string // Access token for this specific itinerary
  title: string       // Human-readable title
  createdAt: string   // ISO-8601 timestamp
}

/**
 * Structure of session data persisted in localStorage.
 */
export interface SessionStorage {
  currentSessionId: string | null
  sessions: Record<string, AnonymousSession>
}
