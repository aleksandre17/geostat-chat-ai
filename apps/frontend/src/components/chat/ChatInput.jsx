import React, { useRef } from 'react';
import VoiceInputButton from './VoiceInputButton.jsx';

const VOICE_LANG = { ka: 'ka-GE', en: 'en-US' };

export default function ChatInput({
    inputValue,
    onInputChange,
    onSend,
    onKeyDown,
    isTyping,
    lang,
    voiceT,
    placeholder,
}) {
    const inputRef = useRef(null);

    const handleInput = (e) => {
        onInputChange(e.target.value);
        e.target.style.height = 'auto';
        e.target.style.height = `${Math.min(e.target.scrollHeight, 100)}px`;
    };

    return (
        <div className="chat-input-container">
            <div className="chat-input-wrapper">
                <textarea
                    ref={inputRef}
                    className="chat-input"
                    value={inputValue}
                    onChange={handleInput}
                    onKeyDown={onKeyDown}
                    placeholder={placeholder}
                    rows="1"
                    disabled={isTyping}
                />
                <VoiceInputButton
                    onTranscript={(text) => onInputChange(text)}
                    language={VOICE_LANG[lang] || VOICE_LANG.ka}
                    voiceT={voiceT}
                />
                <button
                    type="button"
                    className="send-button"
                    onClick={onSend}
                    disabled={!inputValue.trim() || isTyping}
                >
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                        <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
                    </svg>
                </button>
            </div>
        </div>
    );
}
