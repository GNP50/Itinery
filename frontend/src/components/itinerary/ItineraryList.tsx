import { MapPin, Clock, Share2, Edit, Trash2, Copy } from 'lucide-react'
import { Itinerary } from '../../types/itinerary'

interface ItineraryListProps {
  itineraries: Itinerary[]
  onEdit?: (id: string) => void
  onDelete?: (id: string) => void
  onClone?: (id: string) => void
}

export function ItineraryList({ itineraries, onEdit, onDelete, onClone }: ItineraryListProps) {
  if (itineraries.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <MapPin className="h-16 w-16 text-gray-300 mb-4" />
        <h3 className="text-xl font-semibold text-gray-900">No itineraries yet</h3>
        <p className="text-gray-500 mt-2">Create your first travel itinerary to get started</p>
        <a
          href="/create"
          className="mt-6 rounded-lg bg-blue-600 px-6 py-2.5 font-medium text-white hover:bg-blue-700"
        >
          Create Itinerary
        </a>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      {itineraries.map((itinerary) => (
        <div
          key={itinerary.id}
          className="group relative overflow-hidden rounded-xl bg-white shadow-lg transition-all hover:shadow-xl border border-gray-200"
        >
          {/* Status Badge */}
          <div className="absolute top-4 right-4 z-10">
            <span className={`
              rounded-full px-3 py-1 text-xs font-medium
              ${itinerary.status === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                itinerary.status === 'PROCESSING' ? 'bg-blue-100 text-blue-700' :
                itinerary.status === 'FAILED' ? 'bg-red-100 text-red-700' :
                itinerary.status === 'QUEUED' ? 'bg-yellow-100 text-yellow-700' :
                'bg-gray-100 text-gray-700'}
            `}>
              {itinerary.status}
            </span>
          </div>

          <div className="p-6">
            <a href={`/itineraries/${itinerary.id}`} className="block">
              <h3 className="text-lg font-bold text-gray-900 mb-2 line-clamp-1">
                {itinerary.title}
              </h3>
              <p className="text-gray-600 text-sm line-clamp-2 mb-4">
                {itinerary.description}
              </p>

              <div className="flex items-center gap-4 text-sm text-gray-500 mb-4">
                <div className="flex items-center gap-1">
                  <MapPin className="h-4 w-4" />
                  <span>{itinerary.steps.length} stops</span>
                </div>
                <div className="flex items-center gap-1">
                  <Clock className="h-4 w-4" />
                  <span>{itinerary.totalDurationMinutes ? Math.round(itinerary.totalDurationMinutes / 60) : 0}h</span>
                </div>
                <div className="flex items-center gap-1">
                  <span>{itinerary.totalDistanceKm || 0} km</span>
                </div>
              </div>

              {/* Progress Bar */}
              {itinerary.steps.length > 0 && (
                <div className="mb-4">
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-gray-500">Progress</span>
                    <span className="text-gray-700 font-medium">
                      {Math.round((itinerary.steps.filter(s => s.status === 'VISITED').length / itinerary.steps.length) * 100)}%
                    </span>
                  </div>
                  <div className="h-2 w-full rounded-full bg-gray-200 overflow-hidden">
                    <div
                      className="h-full bg-blue-600 transition-all duration-500"
                      style={{
                        width: `${(itinerary.steps.filter(s => s.status === 'VISITED').length / itinerary.steps.length) * 100}%`,
                      }}
                    />
                  </div>
                </div>
              )}
            </a>
          </div>

          {/* Action Buttons */}
          <div className="border-t bg-gray-50 p-3 flex items-center justify-end gap-2">
            <button className="p-2 rounded-lg hover:bg-gray-200 text-gray-600">
              <Share2 className="h-4 w-4" />
            </button>
            {onClone && (
              <button
                onClick={() => onClone(itinerary.id)}
                className="p-2 rounded-lg hover:bg-blue-100 text-blue-600"
                title="Clone itinerary"
              >
                <Copy className="h-4 w-4" />
              </button>
            )}
            {onEdit && (
              <button
                onClick={() => onEdit(itinerary.id)}
                className="p-2 rounded-lg hover:bg-gray-200 text-gray-600"
              >
                <Edit className="h-4 w-4" />
              </button>
            )}
            {onDelete && (
              <button
                onClick={() => onDelete(itinerary.id)}
                className="p-2 rounded-lg hover:bg-red-100 text-red-600"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
