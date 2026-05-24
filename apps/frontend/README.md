# frontend (`apps/frontend`)

React chat widget (Vite 7) with embed support via `public/embed/widget.js`.

| | |
|---|---|
| Module id | `frontend` |
| Dev port | 5177 |
| Secrets / env | `ops/config/frontend/` |

## Source layout

```text
src/
├── main.jsx                 entry → app/App.jsx
├── app/
│   └── App.jsx              root shell
├── assets/
│   └── logo.svg
├── config/
│   ├── api.js               runtime API base URL (/config.json + VITE_API_URL)
│   └── iconMap.js           topic icon name → /icons/*.svg
├── services/
│   ├── chatApi.js           chat stream, sync fallback, feedback
│   └── speechApi.js         STT / TTS HTTP
├── hooks/
│   ├── useChat.js           session, messages, streaming send
│   └── useChatSize.js       embed viewport tier (xs…xl)
├── i18n/
│   ├── LanguageContext.jsx
│   └── translations.js
├── components/chat/
│   ├── ChatWidget.jsx       shell + welcome + message list
│   ├── ChatInput.jsx        textarea + voice + send
│   ├── ChatMessage.jsx      markdown, link cards, feedback
│   ├── LinkCard.jsx         single resource card
│   └── VoiceInputButton.jsx microphone → speechApi
└── index.css, chatTiers.css styling
```

### Concerns

| Layer | Role |
|-------|------|
| `components/` | presentation only |
| `hooks/` | reusable UI state/behavior |
| `services/` | HTTP to chat-api |
| `config/` | static maps + runtime config |
| `public/` | static assets (icons, embed loader, favicon) |

No shadcn/Tailwind scaffold — custom CSS only.

## Commands

```powershell
cd apps/frontend
npm run dev
npm run build
# or
..\..\tools\geostat.ps1 fe dev watch
```

Dockerfile: `apps/frontend/Dockerfile` (multi-stage: deps → development | builder → production).
