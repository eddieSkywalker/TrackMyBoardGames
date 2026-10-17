import { useAuth0 } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

// Client-side gate for signed-in screens.
function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading, error } = useAuth0()
  const location = useLocation()

  if (isLoading) {
    return <main className="auth-loading" role="status">Checking your session...</main>
  }

  if (!isAuthenticated || error) {
    return <Navigate to="/login" replace state={{ returnTo: location.pathname }} />
  }

  return children
}

export default ProtectedRoute