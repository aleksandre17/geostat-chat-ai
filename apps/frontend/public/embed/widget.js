class GeostatChatWidget extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });

        this.isOpen = false;
        this._initialized = false;

        this.lang = this.getAttribute('lang') || 'ka';
        const base = this.getAttribute('chat-src') || 'http://localhost:5173/';
        this.chatSrc = `${base}${base.includes('?') ? '&' : '?'}lang=${this.lang}`;

        this.chatWidth  = parseInt(this.getAttribute('width'))  || 500;
        this.chatHeight = parseInt(this.getAttribute('height')) || 700;
        this.position   = this.getAttribute('position') || 'bottom-right';

        this._currentW = 0;
        this._currentH = 0;

        this._render();
    }

    connectedCallback() {
        this._handleResize = () => this._updateSize();
        window.addEventListener('resize', this._handleResize);

        // პირველი ზომის აღება
        requestAnimationFrame(() => this._updateSize());
    }

    disconnectedCallback() {
        window.removeEventListener('resize', this._handleResize);
    }

    _lerp(a, b, t) {
        return a + (b - a) * Math.max(0, Math.min(1, t));
    }

    _postSizeToIframe() {
        const iframe = this.shadowRoot?.querySelector('iframe');
        if (!iframe?.contentWindow) return;

        iframe.contentWindow.postMessage({
            type: 'GEOSTAT_WIDGET_SIZE',
            width: window.innerWidth,
            height: window.innerHeight,
        }, '*');
    }

    _updateSize() {
        const win = this.shadowRoot.querySelector('.chat-window');
        if (!win) return;

        const vw = window.innerWidth;

        const MIN_W = 300;
        const MIN_H = Math.round(MIN_W * (this.chatHeight / this.chatWidth));
        const t = (vw - 480) / (1440 - 480);

        const w = Math.round(this._lerp(MIN_W, this.chatWidth, t));
        const h = Math.round(this._lerp(MIN_H, this.chatHeight, t));

        win.style.width  = w + 'px';
        win.style.height = h + 'px';

        // 🔥 FIRST TIME INIT
        if (!this._initialized) {
            this._initialized = true;

            // ჯერ გახადე ხილვადი
            win.style.visibility = 'visible';

            // 🔥 შემდეგ frame-ში დამალე
            requestAnimationFrame(() => {
                win.classList.add('initialized'); // display:none აქ
            });
        }

        if (w !== this._currentW || h !== this._currentH) {
            this._currentW = w;
            this._currentH = h;
            this._postSizeToIframe();
        }
    }

    _onIframeLoad() {
        this._postSizeToIframe();
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
                    visibility: hidden; /* 🔥 no flash */
                    border-radius: 16px;
                    overflow: hidden;
                    box-shadow: 0 8px 40px rgba(0,0,0,0.2);

                    opacity: 0;
                    pointer-events: none;
                    transform: translateY(20px) scale(0.95);
                    transition: opacity 0.3s ease, transform 0.3s ease;
                }

                /* 🔥 ზომის შემდეგ */
                .chat-window.initialized {
                    display: none;
                }

                /* 🔥 გახსნა */
                .chat-window.open {
                    display: block;
                    opacity: 1;
                    pointer-events: auto;
                    transform: translateY(0) scale(1);
                }

                iframe {
                    width: 100%;
                    height: 100%;
                    border: none;
                    display: block;
                }

                .toggle-btn {
                    width: 65px;
                    height: 65px;
                    border-radius: 50%;
                    background: linear-gradient(135deg, #0099D8 0%, #0080be 100%);
                    border: none;
                    cursor: pointer;
                    box-shadow: 0 4px 20px rgba(0,128,190,0.4);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: all 0.3s ease;
                }

                .toggle-btn:hover {
                    transform: scale(1.1);
                }

                .toggle-btn svg {
                    width: 30px;
                    height: 30px;
                    fill: white;
                }
            </style>

            <div class="chat-window">
                <iframe src="${this.chatSrc}" loading="lazy" allow="microphone"></iframe>
            </div>

            <button class="toggle-btn">
                ${this._chatIcon()}
            </button>
        `;

        this.shadowRoot.querySelector('.toggle-btn')
            .addEventListener('click', () => this._toggle());

        this.shadowRoot.querySelector('iframe')
            .addEventListener('load', () => this._onIframeLoad());
    }

    _toggle() {
        this.isOpen = !this.isOpen;

        const win = this.shadowRoot.querySelector('.chat-window');
        const btn = this.shadowRoot.querySelector('.toggle-btn');

        if (win) win.classList.toggle('open', this.isOpen);
        if (btn) btn.innerHTML = this._chatIcon();

        if (this.isOpen) {
            this._postSizeToIframe();
        }
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
        if (name === 'position') this.position = val;
        if (name === 'width') this.chatWidth = parseInt(val) || 500;
        if (name === 'height') this.chatHeight = parseInt(val) || 700;

        this._render();
    }
}

customElements.define('geostat-chat-widget', GeostatChatWidget);