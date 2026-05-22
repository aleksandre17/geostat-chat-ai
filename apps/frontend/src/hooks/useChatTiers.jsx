// hooks/useChatSize.js
// Listens for postMessage from parent widget — receives PARENT PAGE viewport size.
//
// Viewport → Tier mapping:
// ─────────────────────────────────────────────
//  xs   < 640px   mobile / very narrow
//  sm   < 800px   small laptop / tablet
//  md   < 1050px  medium laptop (1024px)
//  lg   < 1390px  large laptop (1280–1366px)
//  xl   ≥ 1390px  desktop 1440px+

import { useState, useEffect } from 'react';

const TIERS = [
    { name: 'xs', maxW: 639  },
    { name: 'sm', maxW: 799  },
    { name: 'md', maxW: 1049 },
    { name: 'lg', maxW: 1389 },
    { name: 'xl', maxW: Infinity },
];

function getTier(width) {
    return TIERS.find((t) => width <= t.maxW)?.name ?? 'lg';
}

export function useChatSize() {
    // Fallback: use actual window size if no postMessage arrives
    const [size, setSize] = useState(() => ({
        width:  window.innerWidth,
        height: window.innerHeight,
        tier:   getTier(window.innerWidth),
    }));

    useEffect(() => {
        function handleMessage(e) {
            if (e.data?.type !== 'GEOSTAT_WIDGET_SIZE') return;
            const { width, height } = e.data;
            setSize({ width, height, tier: getTier(width) });
        }

        window.addEventListener('message', handleMessage);
        return () => window.removeEventListener('message', handleMessage);
    }, []);

    return size; // { width, height, tier: 'xs'|'sm'|'md'|'lg'|'xl' }
}