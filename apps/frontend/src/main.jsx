import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './chatTiers.css'
import ChatWidget from './components/chatbot/ChatWidget'
import { LanguageProvider } from './i18n/LanguageContext'
import { loadRuntimeConfig } from './config/api'

function App() {
    return (
        <ChatWidget />
    );
}


loadRuntimeConfig().then(() => {
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <LanguageProvider>
        <App />
      </LanguageProvider>
    </StrictMode>,
  )
})