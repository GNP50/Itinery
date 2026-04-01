import React from 'react'
import { Settings, Tag, Shield, Sparkles } from 'lucide-react'
import type { ItineraryPreferences } from '../../types/itinerary'

interface Props {
  preferences?: ItineraryPreferences
}

export function PreferencesCard({ preferences }: Props) {
  if (!preferences) return null

  const hasInterests = preferences.interests && preferences.interests.length > 0
  const hasSettings = preferences.avoidHighways !== undefined || preferences.generateAiTips !== undefined

  if (!hasInterests && !hasSettings) return null

  return (
    <div className="card card-body animate-fade-in">
      <div className="flex items-center gap-2 mb-4">
        <div className="h-8 w-8 rounded-lg bg-purple-500/10 flex items-center justify-center">
          <Settings className="h-4 w-4 text-purple-600 dark:text-purple-400" />
        </div>
        <h3 className="font-semibold">Travel Preferences</h3>
      </div>

      <div className="space-y-4">
        {/* Interests */}
        {hasInterests && (
          <div>
            <div className="flex items-center gap-2 mb-2">
              <Tag className="h-4 w-4 text-muted-foreground" />
              <p className="text-sm font-medium text-muted-foreground">Interests</p>
            </div>
            <div className="flex flex-wrap gap-2">
              {preferences.interests!.map((interest, idx) => (
                <span
                  key={idx}
                  className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-primary/10 text-primary border border-primary/20"
                >
                  {interest}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Route Settings */}
        {hasSettings && (
          <div className="space-y-2">
            {preferences.avoidHighways !== undefined && (
              <div className="flex items-center gap-2">
                <Shield className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm">
                  <span className="font-medium">Avoid Highways:</span>{' '}
                  <span className={preferences.avoidHighways ? 'text-green-600 dark:text-green-400' : 'text-muted-foreground'}>
                    {preferences.avoidHighways ? 'Yes' : 'No'}
                  </span>
                </span>
              </div>
            )}

            {preferences.generateAiTips !== undefined && (
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm">
                  <span className="font-medium">AI Tips:</span>{' '}
                  <span className={preferences.generateAiTips ? 'text-green-600 dark:text-green-400' : 'text-muted-foreground'}>
                    {preferences.generateAiTips ? 'Enabled' : 'Disabled'}
                  </span>
                </span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
