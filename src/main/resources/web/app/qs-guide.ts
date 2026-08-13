import { LitElement, html, css, unsafeCSS } from 'lit';
import { unsafeHTML } from 'lit/directives/unsafe-html.js';
import { customElement, property } from 'lit/decorators.js';
import icons from './assets/icons';

/**
 * This component is a single guide hit in the search results
 */
@customElement('qs-guide')
export class QsGuide extends LitElement {

  static styles = css`
      :host {
          display: block;
      }
      .highlighted {
          font-weight: bold;
      }

      .qs-guide {
          display: flex;
          flex-direction: column;
          gap: 0.75rem;
          border: 1px solid var(--card-border-color);
          border-radius: 10px;
          padding: 1rem;
          cursor: pointer;
          position: relative;
          overflow: hidden;
          transition: border-color 0.2s ease, box-shadow 0.2s ease;
          box-sizing: border-box;
          height: 100%;
      }

      .qs-guide:hover {
          border-color: var(--card-border-hover-color);
          background-color: var(--card-border-hover-color);
      }

      .qs-guide--pinned {
          background-color: var(--card-pinned-bg-color);
          border-color: var(--card-pinned-border-color);
      }

      .qs-guide--pinned::before {
          opacity: 1;
      }

      .qs-guide--pinned:hover {
          border-color: var(--card-accent-color);
      }

      .qs-guide-header {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          gap: 0.5rem;
      }

      .qs-guide-header h4 {
          margin: 0;
          font-size: 1.15rem;
          font-weight: 600;
          line-height: 1.375;
      }

      .qs-guide a {
          color: var(--title-text-color);
          text-decoration: none;
          transition: color 0.15s ease;
      }

      .qs-guide:hover a {
          color: var(--card-accent-color);
      }

      .qs-guide-badges {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          flex-shrink: 0;
          margin-top: 0.125rem;
      }

      .status-tag {
          cursor: default;
          font-size: 0.625rem;
          line-height: 1;
          text-transform: uppercase;
          font-weight: 600;
          display: inline-block;
          padding: 0.25rem 0.5rem;
          border-radius: 9999px;
          border: 1px solid transparent;
          letter-spacing: 0.05em;
      }

      .status-stable {
          color: var(--tag-stable-text-color, #047857);
          background-color: var(--tag-stable-background-color, #ecfdf5);
          border-color: var(--tag-stable-border-color, #a7f3d0);
      }

      .status-preview {
          color: var(--tag-preview-text-color);
          background-color: var(--tag-preview-background-color);
      }

      .status-deprecated {
          color: var(--tag-deprecated-text-color);
          background-color: var(--tag-deprecated-background-color);
      }

      .status-experimental {
          color: var(--tag-experimental-text-color);
          background-color: var(--tag-experimental-background-color);
      }

      .qs-guide-body {
          display: flex;
          gap: 0.75rem;
          align-items: flex-start;
      }

      .qs-guide-icon {
          flex-shrink: 0;
          margin-top: 0.125rem;
      }

      .qs-guide-icon svg {
          width: 32px;
          height: 32px;
          fill: var(--main-text-color);
      }

      .qs-guide-summary {
          font-size: 0.9rem;
          line-height: 1.625;
          color: var(--main-text-color);
          margin: 0;
          display: -webkit-box;
          -webkit-box-orient: vertical;
          -webkit-line-clamp: 3;
          overflow: hidden;

          p {
              margin: 0;
          }
      }

      .qs-guide--pinned .qs-guide-summary {
          -webkit-line-clamp: 2;
      }

      .qs-guide-tags {
          display: flex;
          flex-wrap: wrap;
          gap: 0.375rem;
          margin-top: auto;
          padding-top: 0.25rem;
      }

      .qs-guide-tag {
          display: inline-block;
          padding: 0.125rem 0.5rem;
          border-radius: 0.25rem;
          font-size: 0.6875rem;
          font-weight: 500;
          background-color: var(--tag-chip-bg);
          color: var(--tag-chip-text);
          border: 1px solid var(--tag-chip-border);
          font-family: ui-monospace, monospace;
      }

      .qs-guide .origin {
          background-size: 100px 25px;
          background-repeat: no-repeat;
          background-position: center;
          width: 100px;
          height: 25px;
          display: inline-block;
          vertical-align: middle;
      }

      .qs-guide .origin.quarkus {
          background-image: url('${unsafeCSS(icons.origins.quarkus)}');
      }

      .qs-guide .origin.quarkiverse-hub {
          background-image: url('${unsafeCSS(icons.origins.quarkiverse)}');
      }
  `;

  @property({type: Object}) data: any;
  @property({type: String}) type: string = "default";
  @property({type: String}) status: string;
  @property({type: String}) url: string;
  @property({type: String}) title: string;
  @property({type: String}) summary: string;
  @property({type: String}) keywords: string;
  @property({type: String}) origin: string = "quarkus";
  @property({type: String}) categories: string;
  @property({type: Boolean}) pinned: boolean = false;
  @property({type: String, attribute: 'origins-with-relative-urls'}) originsWithRelativeUrls: string[] = [];

  connectedCallback() {
    if (this.data) {
      for (const key in this.data) {
        if (this.data.hasOwnProperty(key)) {
          this[key] = this.data[key];
        }
      }
    }
    super.connectedCallback();
  }

  disconnectedCallback() {
    super.disconnectedCallback();
  }

  render() {
    const pinnedClass = this.pinned ? 'qs-guide--pinned' : '';

    return html`
      <div class="qs-hit qs-guide ${pinnedClass}" aria-label="Guide Hit" @click="${this._handleCardClick}" @auxclick="${this._handleCardAuxClick}">
        <div class="qs-guide-header">
          <h4>
            <a href="${this.relativizeUrl()}">${this._renderHTML(this.title)}</a>
          </h4>
          <div class="qs-guide-badges">
            ${this.status ? html`<span class="status-tag status-${this.status}" title="${this._statusHint()}">${this.status}</span>` : ''}
            ${(this.origin && this.origin.toLowerCase() !== 'quarkus') ? html`<a href="${this._originLink()}" target="_blank" class="origin ${this.origin}" title="${this._originTitle()}">${unsafeHTML(this._originIcon())}</a>` : ''}
          </div>
        </div>

        <div class="qs-guide-body">
          <div class="qs-guide-icon">
            ${unsafeHTML(this.icon())}
          </div>
          <p class="qs-guide-summary">${this._renderHTML(this.summary)}</p>
        </div>
      </div>
    `;
  }

  private _handleCardClick(e: MouseEvent) {
    if ((e.target as HTMLElement).closest('a')) {
      return;
    }
    // Ctrl (Windows/Linux) or Cmd (macOS) click opens in a new tab, like a normal link
    const newTab = e.ctrlKey || e.metaKey;
    window.open(this.relativizeUrl(), newTab ? '_blank' : '_self');
  }

  private _handleCardAuxClick(e: MouseEvent) {
    // Middle mouse button ("scroll" click) opens in a new tab
    if (e.button !== 1) {
      return;
    }
    if ((e.target as HTMLElement).closest('a')) {
      return;
    }
    e.preventDefault();
    window.open(this.relativizeUrl(), '_blank');
  }

  private _renderTags() {
    const tags = this._parseTags();
    if (tags.length === 0) {
      return '';
    }
    return html`
      <div class="qs-guide-tags">
        ${tags.map(tag => html`<span class="qs-guide-tag">${tag}</span>`)}
      </div>
    `;
  }

  private _parseTags(): string[] {
    const result: string[] = [];
    if (this.keywords) {
      const parts = this.keywords.replace(/<[^>]*>/g, '').split(/[,]+/).map(s => s.trim()).filter(s => s.length > 0);
      result.push(...parts);
    }
    if (this.categories) {
      if (Array.isArray(this.categories)) {
        result.push(...this.categories);
      } else {
        const parts = this.categories.replace(/<[^>]*>/g, '').split(/[,]+/).map(s => s.trim()).filter(s => s.length > 0);
        result.push(...parts);
      }
    }
    return result;
  }

  private relativizeUrl(): string {
    // When we are running local search the urls may already be relative so let's check if
    // it starts with a `/` and if so assume it is not an absolute url:
    if (this.originsWithRelativeUrls.includes(this.origin) && !this.url.startsWith("/")) {
      try {
        return this.url.substring(new URL(this.url).origin.length);
      } catch (e) {
        // and just in case something goes wrong even after the simple startsWith('/') check let's
        // catch the exception and return the original URL:
        return this.url;
      }
    } else {
      return this.url;
    }
  }

  private icon(): string {
    const icon = icons.docs[this.type];
    return this._iconToSvg(icon);
  }

  private _renderHTML(content?: string | [string]) {
    if (!content) {
      return content;
    }
    if (Array.isArray(content)) {
      return content.map((c) => html`<p>${this._renderHTML(c)}</p>`);
    }
    return unsafeHTML(content);
  }

  private _originTitle(): string {
    if ('quarkiverse-hub' === this.origin) {
      return 'Quarkus extension project contributed by the community';
    } else {
      return this.origin;
    }
  }

  private _originLink(): string {
    if ('quarkiverse-hub' === this.origin) {
      return 'https://github.com/quarkiverse';
    } else {
      return '#';
    }
  }

  private _originIcon(): string {
    const icon = icons.origins['quarkiverse-hub' === this.origin ? 'quarkiverse' : this.origin];
    return this._iconToSvg(icon);
  }

  private _iconToSvg(icon: string): string {
    if (icon) {
      const match = icon.match(/.*(<svg.*<\/svg>)/);
      if (match) {
        // NOTE: we are getting a data-url string here and some characters in there may be encoded, in particular `#`
        // decoding with decodeURI or decodeURIComponent are failing, hence we just manually replace some of these characters
        //
        // Ideally we would change the svg loading to text but that breaks other parts of the app >_<
        return match[1].replaceAll('%23', '#');
      }
    }
    return '';
  }

  private _statusHint() {
    switch (this.status) {
      case 'stable': return 'Backward compatibility and presence in the ecosystem are taken very seriously';
      case 'experimental': return 'Early feedback is requested to mature the idea';
      case 'preview': return 'Backward compatibility and presence in the ecosystem is not guaranteed';
      case 'deprecated': return 'This extension is likely to be replaced or removed';
      default: return '';
    }
  }
}
