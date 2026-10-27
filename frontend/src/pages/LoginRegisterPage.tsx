import { useAuth0 } from '@auth0/auth0-react'
import { useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'

type AuthMode = 'login' | 'register'

// Handles sign-in and registration through Auth0; mode selects login or sign-up behavior.
function LoginRegisterPage({ mode }: { mode: AuthMode }) {
  const { isAuthenticated, isLoading, loginWithRedirect, error } = useAuth0()
  const location = useLocation()
  const [redirectError, setRedirectError] = useState<string | null>(null)
  const isRegister = mode === 'register'
  const returnTo = (location.state as { returnTo?: string } | null)?.returnTo ?? '/collection'

  if (isLoading) {
    return <main className="auth-loading" role="status">Checking your session...</main>
  }

  if (isAuthenticated && !error) {
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

          {(redirectError || error) && (
            <p className="auth-error" role="alert">
              {redirectError ?? error?.message}
            </p>
          )}

          <button
            className="auth-button"
            type="button"
            onClick={async () => {
              setRedirectError(null)
              try {
                await loginWithRedirect({
                  appState: { returnTo },
                  authorizationParams: {
                    ...(isRegister && !error && !redirectError ? { screen_hint: 'signup' } : {}),
                    ...(error || redirectError ? { prompt: 'login' } : {}),
                  },
                })
              } catch (cause) {
                setRedirectError(cause instanceof Error ? cause.message : 'Could not open sign in.')
              }
            }}
          >
            {error ? 'Return to sign in' : isRegister ? 'Continue to registration' : 'Continue to sign in'}
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

export default LoginRegisterPage