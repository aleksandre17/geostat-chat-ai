import React, { useState, useRef } from 'react';
import { Mic, Square, Loader2 } from 'lucide-react';
import { motion } from 'framer-motion';

import { apiBaseUrl } from '../../config/api';

export default function VoiceInputButton({ onTranscript, language = 'ka-GE', voiceT = {} }) {
    const [isRecording, setIsRecording] = useState(false);
    const [isProcessing, setIsProcessing] = useState(false);
    const mediaRecorderRef = useRef(null);
    const chunksRef = useRef([]);

    const startRecording = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: { echoCancellation: true, noiseSuppression: true },
            });

            const mediaRecorder = new MediaRecorder(stream, {
                mimeType: 'audio/webm; codecs=opus',
            });

            chunksRef.current = [];

            mediaRecorder.ondataavailable = (e) => {
                if (e.data.size > 0) chunksRef.current.push(e.data);
            };

            mediaRecorder.onstop = async () => {
                const audioBlob = new Blob(chunksRef.current, { type: 'audio/webm' });
                stream.getTracks().forEach((t) => t.stop());
                await transcribe(audioBlob);
            };

            mediaRecorderRef.current = mediaRecorder;
            mediaRecorder.start();
            setIsRecording(true);
        } catch (error) {
            console.error('Microphone error:', error);
            alert(voiceT.micError || 'მიკროფონზე წვდომა ვერ მოხერხდა');
        }
    };

    const stopRecording = () => {
        if (mediaRecorderRef.current && isRecording) {
            mediaRecorderRef.current.stop();
            setIsRecording(false);
        }
    };

    const transcribe = async (audioBlob) => {
        setIsProcessing(true);
        try {
            const formData = new FormData();
            formData.append('file', audioBlob, 'audio.webm');
            formData.append('language', language);

            const response = await fetch(`${apiBaseUrl}/api/transcribe`, {
                method: 'POST',
                body: formData,
            });

            if (!response.ok) throw new Error('Transcription failed');

            const data = await response.json();
            if (data.transcript?.trim()) {
                onTranscript(data.transcript);
            } else {
                alert(voiceT.noSpeech || 'ხმა ვერ აღიქვა. სცადეთ ხელახლა.');
            }
        } catch (error) {
            console.error('Transcription error:', error);
            alert(voiceT.transcribeError || 'ტრანსკრიფცია ვერ მოხერხდა');
        } finally {
            setIsProcessing(false);
        }
    };

    const handleClick = () => {
        if (isRecording) stopRecording();
        else if (!isProcessing) startRecording();
    };

    return (
        <button
            type="button"
            onClick={handleClick}
            disabled={isProcessing}
            className={`voice-btn ${isRecording ? 'recording' : ''}`}
            title={isRecording ? (voiceT.stop || 'Stop recording') : (voiceT.start || 'Start recording')}
        >
            {isProcessing ? (
                <motion.div
                    animate={{ rotate: 360 }}
                    transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                >
                    <Loader2 size={16} />
                </motion.div>
            ) : isRecording ? (
                <motion.div
                    animate={{ scale: [1, 1.2, 1] }}
                    transition={{ duration: 0.8, repeat: Infinity }}
                >
                    <Square size={14} />
                </motion.div>
            ) : (
                <Mic size={16} />
            )}
        </button>
    );
}
