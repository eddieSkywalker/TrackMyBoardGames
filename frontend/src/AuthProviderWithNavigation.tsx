import { Auth0Provider, type AppState } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'

function AuthProviderWithNavigation({ children }: { children: ReactNode }) {
  const navigate = useNavigate()

  return (
    <Auth0Provider
      domain={import.meta.env.VITE_AUTH0_DOMAIN}
      clientId={import.meta.env.VITE_AUTH0_CLIENT_ID}
      authorizationParams={{ redirect_uri: `${window.location.origin}/` }}
      onRedirectCallback={(appState: AppState | undefined) =>
        navigate(appState?.returnTo ?? '/collection', { replace: true })
      }
    >
      {children}
    </Auth0Provider>
  )
}

export default AuthProviderWithNavigation