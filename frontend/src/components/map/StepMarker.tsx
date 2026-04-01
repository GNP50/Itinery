import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { Marker, Popup } from 'react-leaflet'
import { divIcon } from 'leaflet'
import {
  Circle,
  CheckCircle,
  SkipForward,
  MapPin
} from 'lucide-react'

interface StepMarkerProps {
  step: {
    id: string
    stepOrder: number
    placeName: string
    latitude: number
    longitude: number
    status: string
  }
  onClick?: () => void
}

const getStatusIcon = (status: string): { icon: React.ElementType; color: string } => {
  switch (status) {
    case 'PENDING':
      return { icon: Circle, color: '#94a3b8' } // gray-400
    case 'CURRENT':
      return { icon: MapPin, color: '#3b82f6' } // blue-500
    case 'VISITED':
      return { icon: CheckCircle, color: '#22c55e' } // green-500
    case 'SKIPPED':
      return { icon: SkipForward, color: '#6b7280' } // gray-500
    default:
      return { icon: Circle, color: '#94a3b8' }
  }
}

export function StepMarker({ step, onClick }: StepMarkerProps) {
  const { icon: StatusIcon, color } = getStatusIcon(step.status)

  const icon = divIcon({
    html: renderToStaticMarkup(
      <div
        style={{
          width: 32,
          height: 32,
          backgroundColor: color,
          color: 'white',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: '50%',
          border: '2px solid white',
          boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
        }}
      >
        <StatusIcon className="w-5 h-5" />
      </div>
    ),
    className: 'marker-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  })

  return (
    <Marker
      position={[step.latitude, step.longitude]}
      icon={icon}
      eventHandlers={{
        click: onClick,
      }}
    >
      <Popup>
        <div className="flex items-center gap-2">
          <StatusIcon className="h-5 w-5 text-current" />
          <div>
            <p className="font-semibold text-gray-900">{step.placeName}</p>
            <p className="text-sm text-gray-500">
              {step.status === 'CURRENT' && '📍 You are here'}
              {step.status === 'VISITED' && '✅ Completed'}
              {step.status === 'SKIPPED' && '⏭️ Skipped'}
              {step.status === 'PENDING' && '⏳ Pending'}
            </p>
          </div>
        </div>
      </Popup>
    </Marker>
  )
}
