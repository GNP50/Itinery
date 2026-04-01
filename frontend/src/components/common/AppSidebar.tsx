import React, { useState, useEffect } from 'react'
import {
  MapPin,
  Home,
  Plus,
  List,
  Clock,
  LogOut,
  LogIn,
  User,
  PanelLeftClose,
  PanelLeftOpen,
  Sparkles,
  LayoutDashboard,
  Settings,
} from 'lucide-react'
import { useAuth } from '../../contexts/AuthContext'
import { useQueueStore } from '../../store/queueStore'
import { useRouterStore, Route } from '../../store/routerStore'
import { SessionSwitcher } from './SessionSwitcher'

// ─── Nav config ───────────────────────────────────────────────────────────────

const navSections = [
  {
    title: '',
    items: [{ page: 'home' as const, label: 'Dashboard', icon: Home }],
  },
  {
    title: 'Itineraries',
    items: [
      { page: 'my-itineraries' as const, label: 'My Itineraries', icon: List },
      { page: 'create' as const, label: 'New Itinerary', icon: Plus },
    ],
  },
]

// ─── Context ──────────────────────────────────────────────────────────────────

const SidebarContext = React.createContext<{
  isCollapsed: boolean
  toggleSidebar: () => void
} | null>(null)

function useSidebar() {
  const ctx = React.useContext(SidebarContext)
  if (!ctx) throw new Error('useSidebar must be used within SidebarProvider')
  return ctx
}

function SidebarProvider({ children }: { children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'b') {
        e.preventDefault()
        setCollapsed((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <SidebarContext.Provider value={{ isCollapsed: collapsed, toggleSidebar: () => setCollapsed((v) => !v) }}>
      {children}
    </SidebarContext.Provider>
  )
}

function WhenExpanded({ children }: { children: React.ReactNode }) {
  const { isCollapsed } = useSidebar()
  if (isCollapsed) return null
  return <>{children}</>
}

// ─── Layout primitives ────────────────────────────────────────────────────────

function Sidebar({ children }: { children: React.ReactNode }) {
  const { isCollapsed } = useSidebar()
  const w = isCollapsed ? 'w-[4.5rem]' : 'w-60'

  return (
    <div className={`shrink-0 ${w} transition-[width] duration-200 ease-linear`}>
      <div
        className={`
          fixed inset-y-0 left-0 z-50
          hidden md:flex flex-col
          ${w} transition-[width] duration-200 ease-linear
          bg-sidebar border-r border-sidebar-border
          overflow-hidden
        `}
      >
        {children}
      </div>
    </div>
  )
}

function SidebarInset({ children }: { children: React.ReactNode }) {
  return (
    <main className="flex-1 min-w-0 flex flex-col min-h-screen overflow-x-hidden px-4 sm:px-6 lg:px-8 py-6">
      {children}
    </main>
  )
}

// ─── Nav item ─────────────────────────────────────────────────────────────────

function NavItem({
  icon: Icon,
  label,
  isActive,
  onClick,
  badge,
  sublabel,
  danger,
  disabled,
}: {
  icon: React.ComponentType<{ className?: string }>
  label: string
  isActive?: boolean
  onClick?: () => void
  badge?: number
  sublabel?: string
  danger?: boolean
  disabled?: boolean
}) {
  const { isCollapsed } = useSidebar()

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={isCollapsed ? label : undefined}
      className={`
        w-full flex items-center gap-3 rounded-xl text-sm font-medium
        transition-all duration-150 outline-none
        disabled:pointer-events-none disabled:opacity-40
        ${isCollapsed ? 'justify-center px-0 py-3' : 'px-3 py-2.5'}
        ${danger
          ? 'text-red-500 hover:bg-red-500/10 hover:text-red-500'
          : isActive
            ? 'bg-sidebar-primary/12 text-sidebar-primary shadow-[inset_0_0_0_1px_hsl(var(--sidebar-primary)/0.15)]'
            : 'text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-foreground'
        }
      `}
      style={isActive && !danger ? { background: 'hsl(var(--sidebar-primary) / 0.12)' } : undefined}
    >
      <div className="relative shrink-0">
        <Icon className={`h-[18px] w-[18px] ${isActive && !danger ? 'text-sidebar-primary' : ''}`} />
        {badge !== undefined && badge > 0 && (
          <span className="absolute -top-1.5 -right-1.5 min-w-[16px] h-4 px-0.5 flex items-center justify-center rounded-full bg-sidebar-primary text-[9px] font-bold text-sidebar-primary-foreground leading-none">
            {badge > 99 ? '99+' : badge}
          </span>
        )}
      </div>

      {!isCollapsed && (
        <div className="flex-1 min-w-0 text-left">
          <span className="block truncate">{label}</span>
          {sublabel && (
            <span className="block text-[10px] text-sidebar-foreground/45 mt-0.5 truncate font-normal">
              {sublabel}
            </span>
          )}
        </div>
      )}
    </button>
  )
}

// ─── Section label ────────────────────────────────────────────────────────────

function SectionLabel({ title }: { title: string }) {
  return (
    <p className="px-3 mb-1 mt-1 text-[10px] font-semibold uppercase tracking-widest text-sidebar-foreground/40 select-none">
      {title}
    </p>
  )
}

// ─── Divider ──────────────────────────────────────────────────────────────────

function SidebarDivider() {
  return <div className="mx-3 my-2 border-t border-sidebar-border/60" />
}

// ─── AppSidebar ───────────────────────────────────────────────────────────────

export function AppSidebar({ onSignIn, children }: { onSignIn: () => void; children?: React.ReactNode }) {
  const { user, logout, anonymousSessions } = useAuth()
  const { navigate, route } = useRouterStore()
  const { queueLength, processingCount } = useQueueStore()
  const totalActive = queueLength + processingCount

  return (
    <SidebarProvider>
      <SidebarContent
        onSignIn={onSignIn}
        user={user}
        logout={logout}
        navigate={navigate}
        route={route}
        totalActive={totalActive}
        anonymousSessions={anonymousSessions}
      />
      {children && <SidebarInset>{children}</SidebarInset>}
    </SidebarProvider>
  )
}

function SidebarContent({
  onSignIn,
  user,
  logout,
  navigate,
  route,
  totalActive,
  anonymousSessions,
}: {
  onSignIn: () => void
  user: ReturnType<typeof useAuth>['user']
  logout: () => void
  navigate: (r: Route) => void
  route: Route
  totalActive: number
  anonymousSessions: ReturnType<typeof useAuth>['anonymousSessions']
}) {
  const { isCollapsed, toggleSidebar } = useSidebar()

  return (
    <Sidebar>
      {/* ── Header ── */}
      <div className={`flex items-center border-b border-sidebar-border/60 ${isCollapsed ? 'flex-col gap-2 px-0 py-3' : 'gap-3 px-4 py-3.5'}`}>
        <button
          onClick={() => navigate({ page: 'home' })}
          title="Dashboard"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-sidebar-primary text-sidebar-primary-foreground shadow-md hover:opacity-90 transition-opacity"
        >
          <MapPin className="h-[18px] w-[18px]" />
        </button>

        <WhenExpanded>
          <span className="flex-1 font-bold text-[13px] text-sidebar-foreground tracking-tight truncate">
            ItineryViewer
          </span>
          <button
            onClick={toggleSidebar}
            className="shrink-0 flex h-7 w-7 items-center justify-center rounded-lg text-sidebar-foreground/40 hover:text-sidebar-foreground hover:bg-sidebar-accent transition-colors"
            title="Collapse sidebar (Cmd+B)"
          >
            <PanelLeftClose className="h-4 w-4" />
          </button>
        </WhenExpanded>

        {isCollapsed && (
          <button
            onClick={toggleSidebar}
            className="flex h-7 w-7 items-center justify-center rounded-lg text-sidebar-foreground/40 hover:text-sidebar-foreground hover:bg-sidebar-accent transition-colors"
            title="Expand sidebar (Cmd+B)"
          >
            <PanelLeftOpen className="h-4 w-4" />
          </button>
        )}
      </div>

      {/* ── Nav ── */}
      <div className="flex-1 overflow-y-auto overflow-x-hidden py-3 px-2 space-y-0.5">

        {navSections.map((section, si) => (
          <div key={section.title || si}>
            {section.title && (
              <WhenExpanded>
                <SectionLabel title={section.title} />
              </WhenExpanded>
            )}
            {section.items.map(({ page, label, icon }) => (
              <NavItem
                key={page}
                icon={icon}
                label={label}
                isActive={route.page === page}
                onClick={() => navigate({ page } as Route)}
              />
            ))}
            {si < navSections.length - 1 && <SidebarDivider />}
          </div>
        ))}

        {user?.userType === 'ADMIN' && (
          <>
            <SidebarDivider />
            <WhenExpanded>
              <SectionLabel title="Admin" />
            </WhenExpanded>
            <NavItem
              icon={LayoutDashboard}
              label="Admin Dashboard"
              isActive={route.page === 'admin-dashboard'}
              onClick={() => navigate({ page: 'admin-dashboard' })}
            />
          </>
        )}

        {user?.userType === 'ANONYMOUS' && (
          <>
            <SidebarDivider />
            <WhenExpanded>
              <SectionLabel title="Sessions" />
            </WhenExpanded>
            <NavItem
              icon={Settings}
              label="Manage Sessions"
              sublabel={`${anonymousSessions.length} active`}
              isActive={route.page === 'session-management'}
              onClick={() => navigate({ page: 'session-management' })}
            />
          </>
        )}

        {totalActive > 0 && (
          <>
            <SidebarDivider />
            <WhenExpanded>
              <SectionLabel title="Queue" />
            </WhenExpanded>
            <NavItem
              icon={Clock}
              label="Active Queue"
              sublabel={`${totalActive} ${totalActive === 1 ? 'item' : 'items'} processing`}
              isActive={route.page === 'my-itineraries'}
              badge={totalActive}
              onClick={() => navigate({ page: 'my-itineraries' })}
            />
          </>
        )}

        {/* AI hint */}
        <WhenExpanded>
          <div className="mt-3 mx-1">
            <div className="rounded-xl p-3 border border-sidebar-primary/20" style={{ background: 'hsl(var(--sidebar-primary) / 0.06)' }}>
              <div className="flex items-center gap-2 mb-1.5">
                <div className="flex h-6 w-6 items-center justify-center rounded-lg bg-sidebar-primary/20 shrink-0">
                  <Sparkles className="h-3.5 w-3.5 text-sidebar-primary" />
                </div>
                <span className="text-xs font-semibold text-sidebar-foreground">AI Assistant</span>
              </div>
              <p className="text-[11px] text-sidebar-foreground/55 leading-relaxed">
                AI tips and chat available inside any completed itinerary.
              </p>
            </div>
          </div>
        </WhenExpanded>
      </div>

      {/* ── Footer ── */}
      <div className="border-t border-sidebar-border/60 px-2 py-3 space-y-2">

        {/* Session Switcher for anonymous users */}
        {user?.userType === 'ANONYMOUS' && !isCollapsed && (
          <div className="px-1 mb-2">
            <SessionSwitcher />
          </div>
        )}

        {/* User chip */}
        <div className={`flex items-center gap-3 rounded-xl px-3 py-2.5 ${isCollapsed ? 'justify-center px-0' : ''}`}>
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-sidebar-accent ring-2 ring-sidebar-border">
            <User className="h-[15px] w-[15px] text-sidebar-foreground/60" />
          </div>
          <WhenExpanded>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-sidebar-foreground truncate leading-tight">
                {user?.userType === 'ANONYMOUS' ? 'Guest User' : 'Signed In'}
              </p>
              <p className="text-[10px] text-sidebar-foreground/45 truncate mt-0.5 leading-tight">
                {user?.userType === 'ANONYMOUS'
                  ? 'Anonymous session'
                  : (user?.userId.slice(0, 14) ?? '') + '…'}
              </p>
            </div>
          </WhenExpanded>
        </div>

        {user?.userType === 'ANONYMOUS' ? (
          <NavItem
            icon={LogIn}
            label="Sign in"
            sublabel="Save your itineraries"
            onClick={onSignIn}
          />
        ) : (
          <NavItem
            icon={LogOut}
            label="Sign out"
            onClick={logout}
            danger
          />
        )}
      </div>
    </Sidebar>
  )
}
