import { useAuth0 } from '@auth0/auth0-react'
import { Navigate } from 'react-router-dom'

// Handles the app's root address: wait for Auth0 to restore the session, then redirect to the right screen.
function HomeRoute() {
  const { isAuthenticated, isLoading, error } = useAuth0()

  if (isLoading) {
    return <main className="auth-loading" role="status">Checking your session...</main>
  }

  return <Navigate to={isAuthenticated && !error ? '/collection' : '/login'} replace />
}

export default HomeRoute