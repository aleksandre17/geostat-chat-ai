import { ExternalLink } from 'lucide-react';
import { getIconUrl } from '../../config/iconMap.js';

export default function LinkCard({
    url,
    title,
    type,
    sourceType,
    snippet,
    relevanceScore,
    icon,
    emoji,
    explanation,
    tier,
    isKa,
}) {
    const iconUrl = getIconUrl(icon);
    const isCompact = tier === 'xs' || tier === 'sm';
    const isRag = sourceType === 'rag';
    const displayDescription = snippet || explanation;
    const typeLabel = isRag
        ? isKa
            ? 'ინდექსირებული წყარო'
            : 'Indexed source'
        : type
          ? type.charAt(0).toUpperCase() + type.slice(1)
          : 'GeoStat';

    return (
        <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className={`link-card ${isCompact ? 'link-card--compact' : ''} ${isRag ? 'link-card--rag' : ''}`}
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
                {displayDescription && (
                    <span
                        className={`link-card-snippet ${isCompact ? 'link-card-snippet--compact' : ''}`}
                    >
                        {displayDescription}
                    </span>
                )}
                {explanation && snippet && explanation !== snippet && !isCompact && (
                    <span className="link-card-explanation">{explanation}</span>
                )}
                <div className="link-card-meta">
                    <span className="link-card-domain">{typeLabel}</span>
                    {isRag && relevanceScore != null && !isCompact && (
                        <span className="link-card-score">
                            {Math.round(relevanceScore * 100)}%
                        </span>
                    )}
                </div>
            </div>

            <span className="link-card-arrow">→</span>
        </a>
    );
}
