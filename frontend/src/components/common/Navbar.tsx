import React, { useState } from 'react'
import { MapPin, Plus, List, Home, Menu, X, LogIn, LogOut, User } from 'lucide-react'
import { useAuth } from '../../contexts/AuthContext'
import { useQueueStore } from '../../store/queueStore'
import { useRouterStore } from '../../store/routerStore'
import { SessionSwitcher } from './SessionSwitcher'

interface NavbarProps {
  onSignIn: () => void
}

export function Navbar({ onSignIn }: NavbarProps) {
  const { user, logout } = useAuth()
  const { navigate, route } = useRouterStore()
  const { queueLength, processingCount } = useQueueStore()
  const [menuOpen, setMenuOpen] = useState(false)

  const isActive = (page: string) => route.page === page

  const navLinks = [
    { page: 'home', label: 'Home', icon: Home },
    { page: 'create', label: 'New Itinerary', icon: Plus },
    { page: 'my-itineraries', label: 'My Itineraries', icon: List },
  ]

  const totalActive = queueLength + processingCount

  return (
    <nav
      className="sticky top-0 bg-background border-b border-border"
      style={{ zIndex: 'var(--z-navbar)', boxShadow: 'var(--shadow-nav)' }}
    >
      <div className="container mx-auto px-4 max-w-7xl">
        <div className="flex h-14 items-center justify-between gap-4">
          {/* Logo */}
          <button
            onClick={() => navigate({ page: 'home' })}
            className="flex items-center gap-2 font-bold text-foreground hover:text-primary transition-colors flex-shrink-0"
          >
            <MapPin className="h-5 w-5 text-primary" />
            <span className="hidden sm:inline">ItineryViewer</span>
          </button>

          {/* Desktop Nav */}
          <div className="hidden md:flex items-center gap-1 flex-1 justify-center">
            {navLinks.map(({ page, label, icon: Icon }) => (
              <button
                key={page}
                onClick={() => navigate({ page: page as any })}
                className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive(page)
                    ? 'sidebar-nav-item-active'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                }`}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </div>

          {/* Right side */}
          <div className="flex items-center gap-2 flex-shrink-0">
            {/* Queue badge */}
            {totalActive > 0 && (
              <button
                onClick={() => navigate({ page: 'my-itineraries' })}
                className="flex items-center gap-1.5 rounded-full bg-muted px-3 py-1 text-xs font-medium text-primary hover:bg-muted/80 transition-colors"
                title="Itineraries in queue or processing"
              >
                <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
                {totalActive} active
              </button>
            )}

            {/* Session switcher or user chip */}
            <div className="hidden sm:flex items-center gap-2">
              {user?.userType === 'ANONYMOUS' ? (
                <SessionSwitcher />
              ) : user ? (
                <div className="flex items-center gap-1.5 rounded-lg bg-muted px-3 py-1.5 text-xs font-medium text-muted-foreground">
                  <User className="h-3.5 w-3.5" />
                  {user.userId.slice(0, 8) + '…'}
                </div>
              ) : null}
            </div>

            {/* Auth button */}
            {user?.userType === 'ANONYMOUS' ? (
              <button
                onClick={onSignIn}
                className="btn btn-secondary btn-sm hidden sm:flex"
              >
                <LogIn className="h-3.5 w-3.5" />
                Sign in
              </button>
            ) : (
              <button
                onClick={logout}
                className="btn btn-ghost btn-sm hidden sm:flex"
                title="Sign out"
              >
                <LogOut className="h-3.5 w-3.5" />
              </button>
            )}

            {/* Mobile menu toggle */}
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="btn-icon btn-ghost md:hidden"
            >
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>

        {/* Mobile menu */}
        {menuOpen && (
          <div className="md:hidden border-t border-border py-2 space-y-1 animate-slide-in">
            {navLinks.map(({ page, label, icon: Icon }) => (
              <button
                key={page}
                onClick={() => {
                  navigate({ page: page as any })
                  setMenuOpen(false)
                }}
                className={`w-full flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive(page)
                    ? 'sidebar-nav-item-active'
                    : 'text-muted-foreground hover:bg-muted'
                }`}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
            <div className="pt-2 border-t border-border flex items-center justify-between px-3">
              <span className="text-xs text-muted-foreground">
                {user?.userType === 'ANONYMOUS' ? 'Guest session' : `User ${user?.userId.slice(0, 8)}…`}
              </span>
              {user?.userType === 'ANONYMOUS' ? (
                <button onClick={onSignIn} className="btn btn-primary btn-sm">
                  Sign in
                </button>
              ) : (
                <button onClick={logout} className="btn btn-ghost btn-sm">
                  Sign out
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </nav>
  )
}
