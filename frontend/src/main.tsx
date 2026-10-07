import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import AuthProviderWithNavigation from './AuthProviderWithNavigation.tsx'

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <BrowserRouter>
            <AuthProviderWithNavigation>
                <App />
            </AuthProviderWithNavigation>
        </BrowserRouter>
    </StrictMode>,
)
