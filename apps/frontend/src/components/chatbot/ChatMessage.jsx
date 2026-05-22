import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Volume2, Square, ExternalLink } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import { useLang } from '../../i18n/LanguageContext';
import { useChatSize } from '../../hooks/useChatTiers.jsx'; // ← ახალი

import { apiBaseUrl } from '../../config/api';

// ============================================================
// ICON NAME MAP
// ============================================================
const ICON_NAME_MAP = {
    'erovnuli_angarishebi': 'erovnuli_angarishebi',
    'national_accounts': 'erovnuli_angarishebi',
    'economy': 'erovnuli_angarishebi',
    'gdp': 'erovnuli_angarishebi',
    'mosaxleoba_statistika': 'mosaxleoba_statistika',
    'population': 'mosaxleoba_statistika',
    'demographics': 'mosaxleoba_statistika',
    'dasaqmeba_xelpasi': 'dasaqmeba_xelpasi',
    'employment': 'dasaqmeba_xelpasi',
    'labor': 'dasaqmeba_xelpasi',
    'wages': 'dasaqmeba_xelpasi',
    'fasebis_statistika': 'fasebis_statistika',
    'prices': 'fasebis_statistika',
    'inflation': 'fasebis_statistika',
    'cpi': 'fasebis_statistika',
    'sagareovachroba': 'sagareovachroba',
    'trade': 'sagareovachroba',
    'export': 'sagareovachroba',
    'import': 'sagareovachroba',
    'biznes_registri': 'biznes_registri',
    'biznesSeqtori': 'biznesSeqtori',
    'business': 'biznesSeqtori',
    'enterprise': 'biznesSeqtori',
    'mrewveloba_enerketika_msh': 'mrewveloba_enerketika_msh',
    'industry': 'mrewveloba_enerketika_msh',
    'construction': 'mrewveloba_enerketika_msh',
    'energy': 'mrewveloba_enerketika_msh',
    'soflis_meurneoba_sasursato_usap': 'soflis_meurneoba_sasursato_usap',
    'agriculture': 'soflis_meurneoba_sasursato_usap',
    'farming': 'soflis_meurneoba_sasursato_usap',
    'turizmis_statistika': 'turizmis_statistika',
    'tourism': 'turizmis_statistika',
    'jadacva_socialuri_uzrunvelyopa': 'jadacva_socialuri_uzrunvelyopa',
    'healthcare': 'jadacva_socialuri_uzrunvelyopa',
    'health': 'jadacva_socialuri_uzrunvelyopa',
    'social': 'jadacva_socialuri_uzrunvelyopa',
    'cxovrebis_done': 'cxovrebis_done',
    'living_standards': 'cxovrebis_done',
    'poverty': 'cxovrebis_done',
    'ganatleba_mecnier_sportI_kultura': 'ganatleba_mecnier_sportI_kultura',
    'education': 'ganatleba_mecnier_sportI_kultura',
    'garemosStatistika': 'garemosStatistika',
    'environment': 'garemosStatistika',
    'samartaldargvevisStatistika': 'samartaldargvevisStatistika',
    'crime': 'samartaldargvevisStatistika',
    'regionaluri_statistika': 'regionaluri_statistika',
    'regions': 'regionaluri_statistika',
    'momxsaxurebis_statistika': 'momxsaxurebis_statistika',
    'services': 'momxsaxurebis_statistika',
    'saxemlmwipo_finansebis_stat': 'saxemlmwipo_finansebis_stat',
    'government_finance': 'saxemlmwipo_finansebis_stat',
    'fiscal': 'saxemlmwipo_finansebis_stat',
    'pirdapiri_ucxouri_invisticiebi': 'pirdapiri_ucxouri_invisticiebi',
    'fdi': 'pirdapiri_ucxouri_invisticiebi',
    'investment': 'pirdapiri_ucxouri_invisticiebi',
    'sainpormacio_sakomunikacia': 'sainpormacio_sakomunikacia',
    'ict': 'sainpormacio_sakomunikacia',
    'mravalindekat_gamokvlelva': 'mravalindekat_gamokvlelva',
    'surveys': 'mravalindekat_gamokvlelva',
    'publication': 'publication',
    'publications': 'publication',
    'news': 'news',
    'metadata': 'metadata',
    'methodology': 'methodology',
    'contact': 'contact',
    'infographic': 'infographic',
    'video': 'video',
    'quest': 'quest',
    'general': 'erovnuli_angarishebi',
    'portal': 'erovnuli_angarishebi',
    'stats': 'erovnuli_angarishebi',
    'statistics': 'erovnuli_angarishebi',
};

const getIconUrl = (iconName) => {
    if (!iconName) return null;
    const normalizedName = iconName.toLowerCase().replace(/[-\s]/g, '_');
    const mappedName = ICON_NAME_MAP[normalizedName] || ICON_NAME_MAP[iconName] || iconName;
    return `/icons/${mappedName}.svg`;
};

// ============================================================
// LINK CARD — tier-aware
// ============================================================
function LinkCard({ url, title, type, icon, emoji, explanation, tier }) {
    const iconUrl = getIconUrl(icon);

    // xs tier-ზე icon პატარა, title შეკუმშული
    const isCompact = tier === 'xs' || tier === 'sm';

    console.log(`Rendering LinkCard: ${title} (type: ${type}, icon: ${icon}, tier: ${tier})`);

    return (
        <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className={`link-card ${isCompact ? 'link-card--compact' : ''}`}
        >
            <div className="link-card-icon">
                {iconUrl ? (
                    <img
                        src={iconUrl}
                        alt=""
                        className="link-card-svg"
                        onError={(e) => {
                            e.target.style.display = 'none';
                            e.target.parentElement.innerHTML = `<span class="link-card-emoji">${emoji || '📊'}</span>`;
                        }}
                    />
                ) : (
                    <span className="link-card-emoji">{emoji || '📊'}</span>
                )}
            </div>

            <div className="link-card-content">
                <div className="link-card-header">
                    <span className="link-card-title">{title}</span>
                    {!isCompact && <ExternalLink size={14} className="link-card-external" />}
                </div>
                {explanation && !isCompact && (
                    <span className="link-card-explanation">{explanation}</span>
                )}
                <span className="link-card-domain">
                    {type ? type.charAt(0).toUpperCase() + type.slice(1) : 'GeoStat'}
                </span>
            </div>

            <span className="link-card-arrow">→</span>
        </a>
    );
}

// ============================================================
// MAIN CHAT MESSAGE COMPONENT
// ============================================================
export default function ChatMessage({ message, isBot, responseData, tier }) {
    const { t } = useLang();
    const [isPlaying, setIsPlaying] = useState(false);

    console.log('Rendering ChatMessage:', { message, isBot, responseData, tier });
    const handleTextToSpeech = async () => {
        if (isPlaying) { setIsPlaying(false); return; }

        const textToRead = isBot && responseData?.intro ? responseData.intro : message.text;

        try {
            setIsPlaying(true);
            const response = await fetch(`${apiBaseUrl}/api/tts/synthesize`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text: textToRead }),
            });

            if (!response.ok) throw new Error('TTS failed');

            const audioBlob = await response.blob();
            const audioUrl  = URL.createObjectURL(audioBlob);
            const audio     = new Audio(audioUrl);

            audio.onended = () => { setIsPlaying(false); URL.revokeObjectURL(audioUrl); };
            audio.onerror = () => { setIsPlaying(false); URL.revokeObjectURL(audioUrl); };

            await audio.play();
        } catch (error) {
            console.error('TTS error:', error);
            setIsPlaying(false);
        }
    };

    const hasLinks   = isBot && responseData?.items && responseData.items.length > 0;
    const displayText = isBot && responseData?.intro ? responseData.intro : message.text;
    const isKa        = responseData?.language !== 'en';

    const topicIcon    = responseData?.topicIcon;
    const topicEmoji   = responseData?.topicEmoji || '📊';
    const topicIconUrl = getIconUrl(topicIcon);
    const headerLabel  = t.chat.resources;

    // xs tier-ზე TTS ღილაკი იმალება — ადგილი ძალიან ვიწროა
    const showTts = isBot && tier !== 'xs';

    return (
        <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
            className={`message ${isBot ? 'model' : 'user'}`}
        >
            {/* Avatar — xs-ზე პატარა */}
            <div className={`message-avatar ${isBot ? 'model-avatar' : 'user-avatar'}`}>
                {isBot ? (
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                        <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 3c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm7 13H5v-.23c0-.62.28-1.2.76-1.58C7.47 15.82 9.64 15 12 15s4.53.82 6.24 2.19c.48.38.76.97.76 1.58V19z"/>
                    </svg>
                ) : (
                    <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                    </svg>
                )}
            </div>

            {/* Content Bubble */}
            <div className="message-content-wrapper">
                <div className={`message-content ${isBot ? 'model-content' : 'user-content'} ${message.isError ? 'error-message' : ''}`}>
                    {isBot ? (
                        <>
                            <ReactMarkdown
                                remarkPlugins={[remarkGfm]}
                                rehypePlugins={[rehypeRaw]}
                                components={{
                                    a: ({ href, children }) => (
                                        <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
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
                                                onError={(e) => { e.target.style.display = 'none'; }}
                                            />
                                        ) : (
                                            <span className="link-cards-icon">{topicEmoji}</span>
                                        )}
                                        {/* xs-ზე header label იმალება */}
                                        {tier !== 'xs' && (
                                            <span className="link-cards-label">{headerLabel}</span>
                                        )}
                                    </div>

                                    {responseData.topics?.length > 1 && (
                                        <div className="secondary-topics">
                                            {responseData.topics.slice(1).map((t) => (
                                                <span key={t} className="secondary-topic-badge">{t}</span>
                                            ))}
                                        </div>
                                    )}

                                    {responseData.items.map((item, i) => {
                                        const link       = item.link;
                                        const linkType   = (link.type || '').toLowerCase();
                                        const useTopicIcon = linkType === 'statistics' || linkType === 'stats' || linkType === 'portal';
                                        const iconToUse  = useTopicIcon ? topicIcon : link.icon;

                                        return (
                                            <LinkCard
                                                key={i}
                                                url={link.url}
                                                title={isKa ? link.titleKa : link.titleEn}
                                                type={link.type}
                                                icon={iconToUse}
                                                emoji={link.emoji}
                                                explanation={item.explanation}
                                                tier={tier}
                                            />
                                        );
                                    })}
                                </div>
                            )}
                        </>
                    ) : (
                        <p>{message.text}</p>
                    )}
                </div>

                {/* TTS — xs tier-ზე იმალება */}
                {/*{showTts && (*/}
                {/*    <button*/}
                {/*        onClick={handleTextToSpeech}*/}
                {/*        className={`tts-btn ${isPlaying ? 'playing' : ''}`}*/}
                {/*        title={isPlaying ? 'Stop' : 'Listen'}*/}
                {/*    >*/}
                {/*        {isPlaying ? <Square size={14} /> : <Volume2 size={14} />}*/}
                {/*    </button>*/}
                {/*)}*/}
            </div>
        </motion.div>
    );
}