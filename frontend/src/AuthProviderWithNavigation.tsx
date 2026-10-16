import { Auth0Provider, type AppState } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiAudience } from './api/apiHelpers'

function AuthProviderWithNavigation({ children }: { children: ReactNode })
{
    const navigate = useNavigate()

    return (
        <Auth0Provider
            domain={import.meta.env.VITE_AUTH0_DOMAIN}
            clientId={import.meta.env.VITE_AUTH0_CLIENT_ID}
            cacheLocation="localstorage"
            useRefreshTokens
            useRefreshTokensFallback={false}
            authorizationParams={{
                redirect_uri: `${window.location.origin}/`,
                audience: apiAudience,
                scope: 'openid profile email read:games write:games',
            }}
            onRedirectCallback={(appState: AppState | undefined) =>
                navigate(appState?.returnTo ?? '/collection', { replace: true })
            }
        >
            {children}
        </Auth0Provider>
    )
}

export default AuthProviderWithNavigation