import { useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'

// Shown after sign-in at /collection; displays the profile and can verify the Spring API token.
function CollectionPage() {
  const { user, logout, getAccessTokenSilently } = useAuth0()
  const [apiSubject, setApiSubject] = useState<string | null>(null)
  const [apiError, setApiError] = useState<string | null>(null)
  const [isCheckingApi, setIsCheckingApi] = useState(false)

  async function checkBackendConnection() {
    setIsCheckingApi(true)
    setApiSubject(null)
    setApiError(null)

    try {
      const accessToken = await getAccessTokenSilently({
        authorizationParams: {
          audience: 'https://trackmyboardgames-api',
          scope: 'read:profile',
        },
      })
      const response = await fetch('http://localhost:8080/api/me', {
        headers: { Authorization: `Bearer ${accessToken}` },
      })

      if (!response.ok) {
        throw new Error(`The API returned HTTP ${response.status}.`)
      }

      const result: { subject: string } = await response.json()
      setApiSubject(result.subject)
    } catch (error) {
      setApiError(error instanceof Error ? error.message : 'The API request failed.')
    } finally {
      setIsCheckingApi(false)
    }
  }

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
        <div className="api-check">
          <button
            className="auth-button"
            type="button"
            onClick={checkBackendConnection}
            disabled={isCheckingApi}
          >
            {isCheckingApi ? 'Checking backend...' : 'Test backend connection'}
          </button>
          {apiSubject && <p className="api-result" role="status">Spring received Auth0 subject: {apiSubject}</p>}
          {apiError && <p className="api-error" role="alert">{apiError}</p>}
        </div>
      </section>
    </main>
  )
}

export default CollectionPage