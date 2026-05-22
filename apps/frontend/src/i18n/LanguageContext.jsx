import React, { createContext, useContext, useState } from 'react';
import { translations } from './translations';

const LanguageContext = createContext(null);

export function LanguageProvider({ children }) {
    const urlLang = new URLSearchParams(window.location.search).get('lang');
    const [lang, setLang] = useState(urlLang === 'en' ? 'en' : 'ka');
    const t = translations[lang];
    const toggleLang = () => setLang((l) => (l === 'ka' ? 'en' : 'ka'));

    return (
        <LanguageContext.Provider value={{ lang, t, toggleLang }}>
            {children}
        </LanguageContext.Provider>
    );
}

export function useLang() {
    return useContext(LanguageContext);
}