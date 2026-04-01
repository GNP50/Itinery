import React from 'react'
import { useAuth } from '../../contexts/AuthContext'
import { useRouterStore } from '../../store/routerStore'
import { User, ChevronRight, Settings } from 'lucide-react'

/**
 * Compact session switcher button for the sidebar.
 * Navigates to the session management page when clicked.
 */
export function SessionSwitcher() {
  const { user, currentAnonymousSession, anonymousSessions } = useAuth()
  const { navigate } = useRouterStore()

  // Only show for anonymous users
  if (!user || user.userType !== 'ANONYMOUS') {
    return null
  }

  const currentLabel = currentAnonymousSession?.label || 
    `Session ${currentAnonymousSession?.id.slice(5, 13) || ''}`

  return (
    <button
      onClick={() => navigate({ page: 'session-management' })}
      className="w-full flex items-center justify-between gap-2 px-3 py-2.5 rounded-xl
                 text-sm text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-foreground
                 transition-all border border-sidebar-border/50 hover:border-sidebar-border
                 group"
      title="Manage guest sessions"
    >
      <div className="flex items-center gap-2 min-w-0 flex-1">
        <div className="h-7 w-7 rounded-full bg-sidebar-accent flex items-center justify-center flex-shrink-0">
          <User className="h-4 w-4 text-sidebar-foreground/60" />
        </div>
        <div className="min-w-0 flex-1 text-left">
          <p className="truncate text-xs font-medium leading-tight">{currentLabel}</p>
          <p className="text-[10px] text-sidebar-foreground/40 leading-tight">
            {anonymousSessions.length} session{anonymousSessions.length !== 1 ? 's' : ''}
          </p>
        </div>
      </div>
      <div className="flex items-center gap-1 flex-shrink-0">
        <Settings className="h-3.5 w-3.5 text-sidebar-foreground/40 group-hover:text-sidebar-foreground transition-colors" />
        <ChevronRight className="h-3.5 w-3.5 text-sidebar-foreground/40 group-hover:translate-x-0.5 transition-transform" />
      </div>
    </button>
  )
}
