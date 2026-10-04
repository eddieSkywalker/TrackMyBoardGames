import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'

// Shown after sign-in at /collection; currently displays the Auth0 profile and sign-out action.
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

export default CollectionPage