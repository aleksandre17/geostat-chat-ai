import { useState, useEffect } from 'react';

const TIERS = [
    { name: 'xs', maxW: 639 },
    { name: 'sm', maxW: 799 },
    { name: 'md', maxW: 1049 },
    { name: 'lg', maxW: 1389 },
    { name: 'xl', maxW: Infinity },
];

function getTier(width) {
    return TIERS.find((t) => width <= t.maxW)?.name ?? 'lg';
}

/** Parent embed sends GEOSTAT_WIDGET_SIZE; falls back to window dimensions. */
export function useChatSize() {
    const [size, setSize] = useState(() => ({
        width: window.innerWidth,
        height: window.innerHeight,
        tier: getTier(window.innerWidth),
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

    return size;
}
