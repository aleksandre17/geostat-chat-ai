import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import './chatTiers.css';
import App from './app/App.jsx';
import { LanguageProvider } from './i18n/LanguageContext.jsx';
import { loadRuntimeConfig } from './config/api.js';

loadRuntimeConfig().then(() => {
    createRoot(document.getElementById('root')).render(
        <StrictMode>
            <LanguageProvider>
                <App />
            </LanguageProvider>
        </StrictMode>,
    );
});
