// ChatWidget.jsx  (მხოლოდ შეცვლილი ნაწილები — დანარჩენი იგივეა)

import React, { useState, useRef, useEffect } from 'react';
import ChatMessage from './ChatMessage';
import VoiceInputButton from './VoiceInputButton';
import logoSvg from './logo.svg';
import { useLang } from '../../i18n/LanguageContext';
import { useChatSize } from '../../hooks/useChatTiers.jsx'; // ← ახალი

import { apiBaseUrl } from '../../config/api';
const VOICE_LANG_MAP = { ka: 'ka-GE', en: 'en-US' };

export default function ChatWidget() {
    const { lang, t } = useLang();
    const { tier }    = useChatSize(); // ← 'xs' | 'sm' | 'md' | 'lg'
    console.log(`Current chat tier: ${tier} (lang: ${lang})`);

    const [messages, setMessages]     = useState([]);
    const [inputValue, setInputValue] = useState('');
    const [isTyping, setIsTyping]     = useState(false);
    const [showWelcome, setShowWelcome] = useState(true);
    const [sessionId, setSessionId]   = useState('');
    const messagesEndRef = useRef(null);
    const inputRef       = useRef(null);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isTyping]);

    useEffect(() => {
        inputRef.current?.focus();
    }, []);

    const handleTextareaInput = (e) => {
        setInputValue(e.target.value);
        e.target.style.height = 'auto';
        e.target.style.height = Math.min(e.target.scrollHeight, 100) + 'px';
    };

    const sendMessage = async (text) => {
        if (!text.trim()) return;
        setMessages((prev) => [...prev, { id: Date.now(), text, isBot: false, timestamp: new Date() }]);
        setInputValue('');
        setIsTyping(true);
        setShowWelcome(false);
        if (inputRef.current) inputRef.current.style.height = 'auto';

        try {
            const response = await fetch(
                `${apiBaseUrl}/api/chat?message=${encodeURIComponent(text)}&sessionId=${sessionId}`,
                { headers: { Accept: 'application/json' } }
            );
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const rawData = await response.json();
            let finalData = rawData;
            if (typeof rawData === 'string') {
                try { finalData = JSON.parse(rawData); }
                catch { finalData = { message: rawData, links: [] }; }
            }
            if (finalData.response) {
                if (typeof finalData.response === 'string') {
                    try { finalData = JSON.parse(finalData.response); }
                    catch { finalData = { message: finalData.response, links: [] }; }
                } else {
                    finalData = finalData.response;
                }
            }
            if (finalData.sessionId) setSessionId(finalData.sessionId);
            setMessages((prev) => [...prev, {
                id: Date.now(),
                text: finalData.intro || t.chat.fallback,
                isBot: true,
                timestamp: new Date(),
                responseData: finalData,
            }]);
        } catch (error) {
            console.error('Chat error:', error);
            setMessages((prev) => [...prev, {
                id: Date.now(),
                text: t.chat.error,
                isBot: true,
                timestamp: new Date(),
                responseData: null,
                isError: true,
            }]);
        } finally {
            setIsTyping(false);
        }
    };

    const startNewConversation = () => {
        setSessionId('');
        setMessages([]);
        setShowWelcome(true);
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage(inputValue);
        }
    };

    // tier class root-ზე → CSS ამას გამოიყენებს
    return (
        <div className={`chat-container tier-${tier}`}>
            {/* Header */}
            <div className="chat-header">
                <div className="logo-container">
                    <img src={logoSvg} alt="GEOSTAT Logo" />
                </div>
                <div className="header-text">
                    <h1>{t.chat.title}</h1>
                    <p>{t.chat.subtitle}</p>
                </div>
                <div className="status-indicator">
                    <span className="status-dot"></span>
                    <span className="status-label">{t.chat.online}</span>
                </div>
                {messages.length > 0 && (
                    <button className="new-conversation-btn" onClick={startNewConversation} title={t.chat.newConversation}>
                        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                            <path d="M19 11H7.83l4.88-4.88c.39-.39.39-1.03 0-1.42-.39-.39-1.02-.39-1.41 0l-6.59 6.59c-.39.39-.39 1.02 0 1.41l6.59 6.59c.39.39 1.02.39 1.41 0 .39-.39.39-1.02 0-1.41L7.83 13H19c.55 0 1-.45 1-1s-.45-1-1-1z"/>
                        </svg>
                    </button>
                )}
            </div>

            {/* Messages */}
            <div className="chat-messages">
                {showWelcome && messages.length === 0 && (
                    <div className="welcome-message">
                        <div className="welcome-icon">
                            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/>
                            </svg>
                        </div>
                        <h2>{t.chat.welcomeHeading}</h2>
                        <p>{t.chat.welcomeBody}</p>
                        <div className="quick-actions">
                            {t.quickActions.map((item) => (
                                <button key={item.id} className="quick-action" onClick={() => sendMessage(item.query)}>
                                    {item.text}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {messages.map((message) => (
                    <ChatMessage
                        tier={tier}
                        key={message.id}
                        message={message}
                        isBot={message.isBot}
                        responseData={message.responseData}
                        isError={message.isError}
                    />
                ))}

                {isTyping && (
                    <div className="message model">
                        <div className="message-avatar model-avatar">
                            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z"/>
                            </svg>
                        </div>
                        <div className="typing-indicator">
                            <span></span><span></span><span></span>
                        </div>
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div className="chat-input-container">
                <div className="chat-input-wrapper">
                    <textarea
                        ref={inputRef}
                        className="chat-input"
                        value={inputValue}
                        onChange={handleTextareaInput}
                        onKeyDown={handleKeyDown}
                        placeholder={t.chat.placeholder}
                        rows="1"
                        disabled={isTyping}
                    />
                    {/*<VoiceInputButton*/}
                    {/*    onTranscript={(text) => setInputValue(text)}*/}
                    {/*    language={VOICE_LANG_MAP[lang]}*/}
                    {/*    voiceT={t.voice}*/}
                    {/*/>*/}
                    <button
                        className="send-button"
                        onClick={() => sendMessage(inputValue)}
                        disabled={!inputValue.trim() || isTyping}
                    >
                        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                        </svg>
                    </button>
                </div>
            </div>

            {/* Footer */}
            <div className="chat-footer">
                <a href="https://geostat.ge" target="_blank" rel="noopener noreferrer">geostat.ge</a>
                {' • '}{t.chat.footer}
            </div>
        </div>
    );
}