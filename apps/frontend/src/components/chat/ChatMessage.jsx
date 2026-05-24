import React, { useState } from 'react';
import { motion } from 'framer-motion';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import { useLang } from '../../i18n/LanguageContext.jsx';
import { getIconUrl } from '../../config/iconMap.js';
import { sendChatFeedback } from '../../services/chatApi.js';
import LinkCard from './LinkCard.jsx';

export default function ChatMessage({ message, isBot, responseData, tier, sessionId }) {
    const { t } = useLang();
    const [feedbackSent, setFeedbackSent] = useState(null);

    const submitFeedback = async (rating) => {
        if (!responseData?.turnId || feedbackSent) return;
        try {
            const ok = await sendChatFeedback({
                turnId: responseData.turnId,
                sessionId: responseData.sessionId || sessionId || '',
                rating,
            });
            if (ok) setFeedbackSent(rating);
        } catch (err) {
            console.error('Feedback error:', err);
        }
    };

    const hasLinks = isBot && responseData?.items && responseData.items.length > 0;
    const displayText = isBot && responseData?.intro ? responseData.intro : message.text;
    const isKa = responseData?.language !== 'en';
    const topicIcon = responseData?.topicIcon;
    const topicIconUrl = getIconUrl(topicIcon);
    const headerLabel = t.chat.resources;
    const showGrounded =
        isBot && responseData?.grounded && (responseData?.sourceCount ?? 0) > 0;

    return (
        <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
            className={`message ${isBot ? 'model' : 'user'}`}
        >
            <div className={`message-avatar ${isBot ? 'model-avatar' : 'user-avatar'}`}>
                {isBot ? (
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                        <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z" />
                    </svg>
                ) : (
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                    </svg>
                )}
            </div>

            <div className="message-content-wrapper">
                <div
                    className={`message-content ${isBot ? 'model-content' : 'user-content'} ${message.isError ? 'error-message' : ''}`}
                >
                    {isBot ? (
                        <>
                            {showGrounded && (
                                <div className="chat-grounded-badge">
                                    {t.chat.groundedIn.replace('{n}', String(responseData.sourceCount))}
                                </div>
                            )}

                            <ReactMarkdown
                                remarkPlugins={[remarkGfm]}
                                rehypePlugins={[rehypeRaw]}
                                components={{
                                    a: ({ href, children }) => (
                                        <a href={href} target="_blank" rel="noopener noreferrer">
                                            {children}
                                        </a>
                                    ),
                                    p: ({ children }) => <p>{children}</p>,
                                }}
                            >
                                {displayText}
                            </ReactMarkdown>

                            {hasLinks && (
                                <div className="link-cards">
                                    <div className="link-cards-header">
                                        {topicIconUrl ? (
                                            <img
                                                src={topicIconUrl}
                                                alt=""
                                                className="link-cards-svg"
                                                onError={(e) => {
                                                    e.target.style.display = 'none';
                                                }}
                                            />
                                        ) : (
                                            <span className="link-cards-icon">📊</span>
                                        )}
                                        {tier !== 'xs' && (
                                            <span className="link-cards-label">{headerLabel}</span>
                                        )}
                                    </div>

                                    {responseData.topics?.length > 1 && (
                                        <div className="secondary-topics">
                                            {responseData.topics.slice(1).map((topic) => (
                                                <span key={topic} className="secondary-topic-badge">
                                                    {topic}
                                                </span>
                                            ))}
                                        </div>
                                    )}

                                    {responseData.items.map((item, i) => {
                                        const link = item.link;
                                        const linkType = (link.type || '').toLowerCase();
                                        const useTopicIcon =
                                            linkType === 'statistics' ||
                                            linkType === 'stats' ||
                                            linkType === 'portal';
                                        const iconToUse = useTopicIcon ? topicIcon : link.icon;

                                        return (
                                            <LinkCard
                                                key={i}
                                                url={link.url}
                                                title={isKa ? link.titleKa : link.titleEn}
                                                type={link.type}
                                                sourceType={link.sourceType}
                                                snippet={link.snippet}
                                                relevanceScore={link.relevanceScore}
                                                icon={iconToUse}
                                                emoji={link.emoji}
                                                explanation={item.explanation}
                                                tier={tier}
                                                isKa={isKa}
                                            />
                                        );
                                    })}
                                </div>
                            )}
                            {responseData?.turnId && (
                                <div className="chat-feedback">
                                    {feedbackSent ? (
                                        <span className="chat-feedback-thanks">{t.chat.feedbackThanks}</span>
                                    ) : (
                                        <>
                                            <button
                                                type="button"
                                                className="chat-feedback-btn"
                                                onClick={() => submitFeedback('up')}
                                                title={t.chat.feedbackUp}
                                            >
                                                👍
                                            </button>
                                            <button
                                                type="button"
                                                className="chat-feedback-btn"
                                                onClick={() => submitFeedback('down')}
                                                title={t.chat.feedbackDown}
                                            >
                                                👎
                                            </button>
                                        </>
                                    )}
                                </div>
                            )}
                        </>
                    ) : (
                        <p>{message.text}</p>
                    )}
                </div>
            </div>
        </motion.div>
    );
}
