import { Navigate, Route, Routes } from 'react-router-dom'
import LoginRegisterPage from './pages/LoginRegisterPage'
import CollectionPage from './pages/CollectionPage'
import HomeRoute from './routing/HomeRoute'
import ProtectedRoute from './routing/ProtectedRoute'
import './App.css'

// Map browser paths to different component pages, withholding access to the collection page until authenticated and redirecting unknown paths to login.
function App()
{
    return (
        <Routes>
            <Route path="/" element={<HomeRoute />} />
            <Route path="/login" element={<LoginRegisterPage mode="login" />} />
            <Route path="/register" element={<LoginRegisterPage mode="register" />} />
            <Route
                path="/collection"
                element={<ProtectedRoute><CollectionPage /></ProtectedRoute>}
            />
            <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
    )
}

export default App
