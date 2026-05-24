import { apiBaseUrl } from '../config/api.js';

export async function transcribeAudio(blob, language = 'ka-GE') {
    const formData = new FormData();
    formData.append('file', blob, 'audio.webm');
    formData.append('language', language);

    const response = await fetch(`${apiBaseUrl}/api/transcribe`, {
        method: 'POST',
        body: formData,
    });
    if (!response.ok) throw new Error(`Transcribe HTTP ${response.status}`);
    const data = await response.json();
    return data.transcript?.trim() || data.text?.trim() || '';
}

export async function synthesizeSpeech(text) {
    const response = await fetch(`${apiBaseUrl}/api/tts/synthesize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
    });
    if (!response.ok) throw new Error('TTS failed');
    return response.blob();
}
