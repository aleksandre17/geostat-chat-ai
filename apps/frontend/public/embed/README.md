# Embed widget

Host sites load `widget.js` and use the `<geostat-chat-widget>` custom element.

## Configuration

| Mechanism | Example |
|-----------|---------|
| Attribute | `chat-src="https://chat.example.ge:5177"` |
| URL param (demo page) | `example.html?chat_src=https://chat.example.ge:5177&lang=ka` |
| Meta tag | `<meta name="geostat-chat-src" content="https://...">` |

Documented defaults for your team: `ops/config/frontend/embed.env.example` (not loaded at build time).

## Demo

Open `/embed/example.html` locally after `npm run dev`. Do not hardcode production IPs in this repo.
