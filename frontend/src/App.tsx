import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import CurrentCollectionProvider from './context/CurrentCollectionProvider'
import LoginRegisterPage from './pages/LoginRegisterPage'
import CollectionPage from './pages/CollectionPage'
import PlayHistoryPage from './pages/PlayHistoryPage'
import ScanPage from './pages/ScanPage'
import StatsSummaryPage from './pages/StatsSummaryPage'
import HomeRoute from './routing/HomeRoute'
import ProtectedRoute from './routing/ProtectedRoute'
import './App.css'

// Map browser paths to different component pages, withholding access to the collection page until authenticated and redirecting unknown paths to login.
function App()
{
    const { user, isAuthenticated } = useAuth0()
    const accountKey = isAuthenticated ? `${user?.iss ?? ''}:${user?.sub ?? ''}` : 'signed-out'

    return (
        <CurrentCollectionProvider key={accountKey}>
            <Routes>
                <Route path="/" element={<HomeRoute />} />
                <Route path="/login" element={<LoginRegisterPage mode="login" />} />
                <Route path="/register" element={<LoginRegisterPage mode="register" />} />
                <Route
                    path="/collection"
                    element={<ProtectedRoute><CollectionPage /></ProtectedRoute>}
                />
                <Route
                    path="/collection/:gameId"
                    element={<ProtectedRoute><PlayHistoryPage /></ProtectedRoute>}
                />
                <Route
                    path="/stats"
                    element={<ProtectedRoute><StatsSummaryPage /></ProtectedRoute>}
                />
                <Route
                    path="/scan"
                    element={<ProtectedRoute><ScanPage /></ProtectedRoute>}
                />
                <Route
                    path="/scan/:token"
                    element={<ProtectedRoute><ScanPage /></ProtectedRoute>}
                />
                <Route path="*" element={<Navigate to="/login" replace />} />
            </Routes>
        </CurrentCollectionProvider>
    )
}

export default App
