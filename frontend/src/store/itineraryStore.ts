import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface ItineraryRecord {
  id: string
  accessToken: string
  title: string
  status: string
  createdAt: string
}

interface ItineraryStore {
  itineraries: ItineraryRecord[]
  addItinerary: (r: ItineraryRecord) => void
  updateStatus: (id: string, status: string) => void
  removeItinerary: (id: string) => void
}

export const useItineraryStore = create<ItineraryStore>()(
  persist(
    (set) => ({
      itineraries: [],
      addItinerary: (r) =>
        set((s) => ({ itineraries: [r, ...s.itineraries] })),
      updateStatus: (id, status) =>
        set((s) => ({
          itineraries: s.itineraries.map((it) =>
            it.id === id ? { ...it, status } : it
          ),
        })),
      removeItinerary: (id) =>
        set((s) => ({
          itineraries: s.itineraries.filter((it) => it.id !== id),
        })),
    }),
    { name: 'itinerary_records' }
  )
)
