import { useState, useRef, useCallback } from 'react';
import { sendChatMessage } from '../services/chatApi.js';

/**
 * Chat session state: messages, sessionId, streaming send.
 * @param {{ t: object }} options
 */
export function useChat({ t, lang = 'ka' }) {
    const [messages, setMessages] = useState([]);
    const [sessionId, setSessionId] = useState('');
    const [isTyping, setIsTyping] = useState(false);
    const [showWelcome, setShowWelcome] = useState(true);
    const streamStartedRef = useRef(false);
    const messageIdRef = useRef(0);

    const nextMessageId = () => {
        messageIdRef.current += 1;
        return messageIdRef.current;
    };

    const appendBotResponse = useCallback((finalData) => {
        if (finalData.sessionId) setSessionId(finalData.sessionId);
        setMessages((prev) => [
            ...prev,
            {
                id: nextMessageId(),
                text: finalData.intro || t.chat.fallback,
                isBot: true,
                timestamp: new Date(),
                responseData: finalData,
            },
        ]);
    }, [t.chat.fallback]);

    const sendMessage = useCallback(async (text) => {
        if (!text?.trim()) return;

        const userMsgId = nextMessageId();
        const botMsgId = nextMessageId();

        setMessages((prev) => [...prev, { id: userMsgId, text, isBot: false, timestamp: new Date() }]);
        setIsTyping(true);
        setShowWelcome(false);
        streamStartedRef.current = false;

        try {
            await sendChatMessage({
                text,
                sessionId,
                locale: lang,
                onToken: (streamingIntro) => {
                    if (!streamStartedRef.current) {
                        streamStartedRef.current = true;
                        setMessages((prev) => [
                            ...prev,
                            {
                                id: botMsgId,
                                text: '',
                                isBot: true,
                                timestamp: new Date(),
                                responseData: null,
                                streaming: true,
                            },
                        ]);
                    }
                    setMessages((prev) =>
                        prev.map((m) =>
                            m.id === botMsgId && m.isBot ? { ...m, text: streamingIntro } : m,
                        ),
                    );
                },
                onComplete: (finalData) => {
                    if (finalData.sessionId) setSessionId(finalData.sessionId);
                    if (streamStartedRef.current) {
                        setMessages((prev) =>
                            prev.map((m) =>
                                m.id === botMsgId && m.isBot
                                    ? {
                                          ...m,
                                          text: finalData.intro || m.text || t.chat.fallback,
                                          responseData: finalData,
                                          streaming: false,
                                      }
                                    : m,
                            ),
                        );
                    } else {
                        appendBotResponse(finalData);
                    }
                },
            });
        } catch (error) {
            console.error('Chat error:', error);
            setMessages((prev) => [
                ...prev,
                {
                    id: nextMessageId(),
                    text: t.chat.error,
                    isBot: true,
                    timestamp: new Date(),
                    responseData: null,
                    isError: true,
                },
            ]);
        } finally {
            setIsTyping(false);
        }
    }, [sessionId, lang, t.chat.error, t.chat.fallback, appendBotResponse]);

    const startNewConversation = useCallback(() => {
        setSessionId('');
        setMessages([]);
        setShowWelcome(true);
    }, []);

    return {
        messages,
        sessionId,
        isTyping,
        showWelcome,
        sendMessage,
        startNewConversation,
    };
}
