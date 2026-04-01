import { create } from 'zustand'

export type Route =
  | { page: 'home' }
  | { page: 'create' }
  | { page: 'my-itineraries' }
  | { page: 'itinerary-tracking'; id: string; accessToken: string }
  | { page: 'itinerary-detail'; id: string; accessToken: string }
  | { page: 'admin-dashboard' }
  | { page: 'session-management' }
  | { page: 'saga-detail'; itineraryId: string }

interface RouterState {
  route: Route
  history: Route[]
  navigate: (r: Route) => void
  back: () => void
}

export const useRouterStore = create<RouterState>((set, get) => ({
  route: { page: 'home' },
  history: [],
  navigate: (r) =>
    set((s) => ({ route: r, history: [...s.history, s.route] })),
  back: () =>
    set((s) => {
      const prev = s.history[s.history.length - 1]
      if (!prev) return s
      return { route: prev, history: s.history.slice(0, -1) }
    }),
}))
