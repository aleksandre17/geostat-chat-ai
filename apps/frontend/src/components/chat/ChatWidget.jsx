import React, { useState, useEffect, useRef } from 'react';
import ChatMessage from './ChatMessage.jsx';
import ChatInput from './ChatInput.jsx';
import logoSvg from '../../assets/logo.svg';
import { useLang } from '../../i18n/LanguageContext.jsx';
import { useChatSize } from '../../hooks/useChatSize.js';
import { useChat } from '../../hooks/useChat.js';

export default function ChatWidget() {
    const { lang, t, toggleLang } = useLang();
    const { tier } = useChatSize();
    const { messages, sessionId, isTyping, showWelcome, sendMessage, startNewConversation } = useChat({ t, lang });
    const [inputValue, setInputValue] = useState('');
    const messagesEndRef = useRef(null);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isTyping]);

    const handleSend = () => {
        sendMessage(inputValue);
        setInputValue('');
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div className={`chat-container tier-${tier}`}>
            <div className="chat-header">
                <div className="logo-container">
                    <img src={logoSvg} alt="GEOSTAT Logo" />
                </div>
                <div className="header-text">
                    <h1>{t.chat.title}</h1>
                    <p>{t.chat.subtitle}</p>
                </div>
                <button
                    type="button"
                    className="chat-lang-switcher"
                    onClick={toggleLang}
                    title={lang === 'ka' ? 'Switch to English' : 'ქართულზე გადართვა'}
                >
                    <span className={lang === 'ka' ? 'lang-active' : ''}>KA</span>
                    <span className="lang-divider">|</span>
                    <span className={lang === 'en' ? 'lang-active' : ''}>EN</span>
                </button>
                <div className="status-indicator">
                    <span className="status-dot" />
                    <span className="status-label">{t.chat.online}</span>
                </div>
                {messages.length > 0 && (
                    <button
                        type="button"
                        className="new-conversation-btn"
                        onClick={startNewConversation}
                        title={t.chat.newConversation}
                    >
                        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                            <path d="M19 11H7.83l4.88-4.88c.39-.39.39-1.03 0-1.42-.39-.39-1.02-.39-1.41 0l-6.59 6.59c-.39.39-.39 1.02 0 1.41l6.59 6.59c.39.39 1.02.39 1.41 0 .39-.39.39-1.02 0-1.41L7.83 13H19c.55 0 1-.45 1-1s-.45-1-1-1z" />
                        </svg>
                    </button>
                )}
            </div>

            <div className="chat-messages">
                {showWelcome && messages.length === 0 && (
                    <div className="welcome-message">
                        <div className="welcome-icon">
                            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z" />
                            </svg>
                        </div>
                        <h2>{t.chat.welcomeHeading}</h2>
                        <p>{t.chat.welcomeBody}</p>
                        <div className="quick-actions">
                            {t.quickActions.map((item) => (
                                <button
                                    key={item.id}
                                    type="button"
                                    className="quick-action"
                                    onClick={() => sendMessage(item.query)}
                                >
                                    {item.text}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {messages.map((message) => (
                    <ChatMessage
                        key={message.id}
                        tier={tier}
                        message={message}
                        isBot={message.isBot}
                        responseData={message.responseData}
                        sessionId={sessionId}
                    />
                ))}

                {isTyping && (
                    <div className="message model">
                        <div className="message-avatar model-avatar">
                            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z" />
                            </svg>
                        </div>
                        <div className="typing-indicator">
                            <span />
                            <span />
                            <span />
                        </div>
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            <ChatInput
                inputValue={inputValue}
                onInputChange={setInputValue}
                onSend={handleSend}
                onKeyDown={handleKeyDown}
                isTyping={isTyping}
                lang={lang}
                voiceT={t.voice}
                placeholder={t.chat.placeholder}
            />

            <div className="chat-footer">
                <a href="https://geostat.ge" target="_blank" rel="noopener noreferrer">
                    geostat.ge
                </a>
                {' • '}
                {t.chat.footer}
            </div>
        </div>
    );
}
