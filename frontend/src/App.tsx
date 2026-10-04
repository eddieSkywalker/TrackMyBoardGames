import { useAuth0 } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import './App.css'

type AuthMode = 'login' | 'register'

function AuthPage({ mode }: { mode: AuthMode }) {
  const { isAuthenticated, isLoading, loginWithRedirect, error } = useAuth0()
  const location = useLocation()
  const isRegister = mode === 'register'
  const returnTo = (location.state as { returnTo?: string } | null)?.returnTo ?? '/collection'

  if (isLoading) {
    return <main className="auth-loading" role="status">Checking your session...</main>
  }

  if (isAuthenticated) {
    return <Navigate to="/collection" replace />
  }

  return (
    <main className="auth-layout">
      <section className="welcome-panel" aria-labelledby="brand-title">
        <Link className="wordmark" to="/login" aria-label="Track My Board Games home">
          <span className="wordmark-mark" aria-hidden="true">TB</span>
          <span>Track My Board Games</span>
        </Link>
        <div className="welcome-copy">
          <p className="eyebrow">A LITTLE MORE GAME NIGHT</p>
          <h1 id="brand-title">Keep track of the games that bring you together.</h1>
          <p>Build your collection, remember every play, and find your next table favorite.</p>
        </div>
        <p className="welcome-foot">YOUR SHELF, YOUR STORIES.</p>
      </section>

      <section className="form-panel" aria-labelledby="form-title">
        <div className="form-content">
          <p className="eyebrow form-eyebrow">WELCOME TO THE TABLE</p>
          <h2 id="form-title">{isRegister ? 'Create your account' : 'Sign in'}</h2>
          <p className="form-intro">
            {isRegister
              ? 'Make a home for your collection and play history.'
              : 'Pick up where your last game night left off.'}
          </p>

          {error && <p className="auth-error" role="alert">{error.message}</p>}

          <button
            className="auth-button"
            type="button"
            onClick={() =>
              loginWithRedirect({
                appState: { returnTo },
                ...(isRegister && { authorizationParams: { screen_hint: 'signup' } }),
              })
            }
          >
            {isRegister ? 'Continue to registration' : 'Continue to sign in'}
          </button>

          <p className="switch-mode">
            {isRegister ? 'Already have an account?' : 'New to the table?'}{' '}
            <Link to={isRegister ? '/login' : '/register'}>
              {isRegister ? 'Sign in' : 'Create an account'}
            </Link>
          </p>
          <p className="auth-note">You’ll continue securely with Auth0.</p>
        </div>
      </section>
    </main>
  )
}

function CollectionPage() {
  const { user, logout } = useAuth0()

  return (
    <main className="collection-page">
      <header className="collection-header">
        <Link className="wordmark collection-wordmark" to="/collection">
          <span className="wordmark-mark" aria-hidden="true">TB</span>
          <span>Track My Board Games</span>
        </Link>
        <button
          className="quiet-button"
          type="button"
          onClick={() => logout({ logoutParams: { returnTo: `${window.location.origin}/` } })}
        >
          Sign out
        </button>
      </header>
      <section className="collection-content">
        <p className="eyebrow">SIGNED IN</p>
        <h1>Welcome, {user?.name ?? user?.email ?? 'player'}.</h1>
        <p>Your account is connected. Your game collection will live here.</p>
      </section>
    </main>
  )
}

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth0()
  const location = useLocation()

  if (isLoading) {
    return <main className="auth-loading" role="status">Checking your session...</main>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ returnTo: location.pathname }} />
  }

  return children
}

function HomeRoute() {
  const { isAuthenticated, isLoading } = useAuth0()

  if (isLoading) {
    return <main className="auth-loading" role="status">Checking your session...</main>
  }

  return <Navigate to={isAuthenticated ? '/collection' : '/login'} replace />
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRoute />} />
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route
        path="/collection"
        element={<ProtectedRoute><CollectionPage /></ProtectedRoute>}
      />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}

export default App
