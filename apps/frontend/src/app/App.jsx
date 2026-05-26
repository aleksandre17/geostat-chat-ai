import { useState, useEffect } from 'react';
import ChatWidget from '../components/chat/ChatWidget.jsx';
import CurationAdmin from '../components/admin/CurationAdmin.jsx';
import '../components/admin/CurationAdmin.css';

export default function App() {
    const [view, setView] = useState('chat');

    useEffect(() => {
        const hash = window.location.hash.slice(1);
        if (hash === 'admin') setView('admin');

        const handleHash = () => {
            const h = window.location.hash.slice(1);
            setView(h === 'admin' ? 'admin' : 'chat');
        };
        window.addEventListener('hashchange', handleHash);
        return () => window.removeEventListener('hashchange', handleHash);
    }, []);

    if (view === 'admin') {
        return (
            <div className="admin-layout">
                <header className="admin-header">
                    <a href="#" className="back-link">← Chat</a>
                    <span>Curation Admin</span>
                </header>
                <CurationAdmin />
            </div>
        );
    }

    return <ChatWidget />;
}
