class GeostatChatWidget extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this.isOpen = false;
        this.lang    = this.getAttribute('lang') || 'ka';
        const base   = this.getAttribute('chat-src') || 'http://localhost:5173/';
        this.chatSrc = `${base}${base.includes('?') ? '&' : '?'}lang=${this.lang}`;
        this.chatWidth  = parseInt(this.getAttribute('width'))  || 380;
        this.chatHeight = parseInt(this.getAttribute('height')) || 600;
        this.position   = this.getAttribute('position') || 'bottom-right';
        this._render();
    }

    connectedCallback() {
        this._handleResize = () => this._updateSize();
        window.addEventListener('resize', this._handleResize);
        this._updateSize();
    }

    disconnectedCallback() {
        window.removeEventListener('resize', this._handleResize);
    }

    _lerp(a, b, t) {
        return a + (b - a) * Math.max(0, Math.min(1, t));
    }

    _updateSize() {
        const win = this.shadowRoot.querySelector('.chat-window');
        if (!win) return;
        const vw = window.innerWidth;

        if (vw <= 480) {
            win.style.removeProperty('width');
            win.style.removeProperty('height');
            return;
        }

        // Linear interpolation: 300px at 480px viewport → chatWidth at 1440px
        const MIN_W = 300;
        const MIN_H = Math.round(MIN_W * (this.chatHeight / this.chatWidth));
        const t = (vw - 480) / (1440 - 480);

        win.style.width  = Math.round(this._lerp(MIN_W, this.chatWidth,  t)) + 'px';
        win.style.height = Math.round(this._lerp(MIN_H, this.chatHeight, t)) + 'px';
    }

    _posStyle() {
        const map = {
            'bottom-right': 'bottom:30px; right:30px;',
            'bottom-left':  'bottom:30px; left:30px;',
        };
        return map[this.position] || 'bottom:30px; right:30px;';
    }

    _chatIcon() {
        return this.isOpen
            ? `<svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>`
            : `<svg viewBox="0 0 24 24"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/></svg>`;
    }

    _render() {
        this.shadowRoot.innerHTML = `
            <style>
                :host {
                    position: fixed;
                    z-index: 9999;
                    ${this._posStyle()}
                    display: flex;
                    flex-direction: column;
                    align-items: flex-end;
                    gap: 10px;
                }
                .chat-window {
                    border-radius: 16px;
                    overflow: hidden;
                    box-shadow: 0 8px 40px rgba(0,0,0,0.2);
                    opacity: 0;
                    pointer-events: none;
                    transform: translateY(20px) scale(0.95);
                    transition: opacity 0.3s ease, transform 0.3s ease;
                }
                .chat-window.open {
                    opacity: 1;
                    pointer-events: auto;
                    transform: translateY(0) scale(1);
                }
                iframe { width: 100%; height: 100%; border: none; display: block; }
                .toggle-btn {
                    width: 65px; height: 65px;
                    border-radius: 50%;
                    background: linear-gradient(135deg, #0099D8 0%, #0080be 100%);
                    border: none; cursor: pointer;
                    box-shadow: 0 4px 20px rgba(0,128,190,0.4);
                    display: flex; align-items: center; justify-content: center;
                    transition: all 0.3s ease; flex-shrink: 0;
                }
                .toggle-btn:hover { box-shadow: 0 6px 25px rgba(0,128,190,0.5); transform: scale(1.1); }
                .toggle-btn svg { width: 30px; height: 30px; fill: white; }
                @media (max-width: 600px) {
                    :host { bottom: 20px; right: 20px; }
                    .toggle-btn { width: 55px; height: 55px; }
                    .toggle-btn svg { width: 26px; height: 26px; }
                }
                @media (max-width: 480px) {
                    :host { bottom: 16px; right: 16px; }
                    .chat-window.open {
                        position: fixed; inset: 0;
                        width: 100% !important; height: 100% !important;
                        border-radius: 0; transform: none;
                    }
                }
            </style>
            <div class="chat-window${this.isOpen ? ' open' : ''}">
                <iframe src="${this.chatSrc}" loading="lazy" allow="microphone"></iframe>
            </div>
            <button class="toggle-btn" aria-label="ჩატის გახსნა">
                ${this._chatIcon()}
            </button>
        `;

        this.shadowRoot.querySelector('.toggle-btn')
            .addEventListener('click', () => this._toggle());

        this._updateSize();
    }

    _toggle() {
        this.isOpen = !this.isOpen;
        const win = this.shadowRoot.querySelector('.chat-window');
        const btn = this.shadowRoot.querySelector('.toggle-btn');
        if (win) win.classList.toggle('open', this.isOpen);
        if (btn) btn.innerHTML = this._chatIcon();
    }

    static get observedAttributes() {
        return ['chat-src', 'lang', 'position', 'width', 'height'];
    }

    attributeChangedCallback(name, _, val) {
        if (name === 'chat-src' || name === 'lang') {
            const base = this.getAttribute('chat-src') || 'http://localhost:5173/';
            const lg   = this.getAttribute('lang') || 'ka';
            this.chatSrc = `${base}${base.includes('?') ? '&' : '?'}lang=${lg}`;
        }
        if (name === 'position') this.position   = val;
        if (name === 'width')    this.chatWidth  = parseInt(val) || 380;
        if (name === 'height')   this.chatHeight = parseInt(val) || 600;
        this._render();
    }
}

customElements.define('geostat-chat-widget', GeostatChatWidget);
