import { apiBaseUrl } from '../config/api.js';

/** Normalize chat-api JSON (direct body or wrapped `response` field). */
export function parseChatPayload(rawData) {
    let finalData = rawData;
    if (typeof rawData === 'string') {
        try {
            finalData = JSON.parse(rawData);
        } catch {
            finalData = { message: rawData, links: [] };
        }
    }
    if (finalData.response) {
        if (typeof finalData.response === 'string') {
            try {
                finalData = JSON.parse(finalData.response);
            } catch {
                finalData = { message: finalData.response, links: [] };
            }
        } else {
            finalData = finalData.response;
        }
    }
    return finalData;
}

/**
 * @param {{ text: string, sessionId: string, locale?: string, onToken?: (chunk: string) => void, onComplete: (data: object) => void }} opts
 */
export async function sendChatMessage({ text, sessionId, locale, onToken, onComplete }) {
    const params = new URLSearchParams({ message: text, sessionId: sessionId || '' });
    if (locale) params.set('locale', locale);
    const streamUrl = `${apiBaseUrl}/api/chat/stream?${params.toString()}`;
    const streamRes = await fetch(streamUrl, { headers: { Accept: 'text/event-stream' } });

    if (streamRes.ok && streamRes.body) {
        const reader = streamRes.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let streamingIntro = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const parts = buffer.split('\n\n');
            buffer = parts.pop() || '';

            for (const part of parts) {
                const lines = part.split('\n');
                let event = 'message';
                let data = '';
                for (const line of lines) {
                    if (line.startsWith('event:')) event = line.slice(6).trim();
                    if (line.startsWith('data:')) data += line.slice(5).trim();
                }
                if (event === 'token' && data) {
                    streamingIntro = data;
                    onToken?.(streamingIntro);
                }
                if (event === 'complete' && data) {
                    onComplete(parseChatPayload(JSON.parse(data)));
                    return;
                }
            }
        }
    }

    const fallbackParams = new URLSearchParams({ message: text, sessionId: sessionId || '' });
    if (locale) fallbackParams.set('locale', locale);
    const response = await fetch(`${apiBaseUrl}/api/chat?${fallbackParams.toString()}`,
        { headers: { Accept: 'application/json' } },
    );
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    onComplete(parseChatPayload(await response.json()));
}

export async function sendChatFeedback({ turnId, sessionId, rating }) {
    const res = await fetch(`${apiBaseUrl}/api/chat/feedback`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ turnId, sessionId: sessionId || '', rating }),
    });
    return res.ok || res.status === 202;
}
