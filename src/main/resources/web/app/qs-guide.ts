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
      @media screen and (max-width: 1300px) {
          .qs-hit {
              grid-column: span 6;
          }
      }

      .highlighted {
          font-weight: bold;
      }

      .qs-guide {
          display: flex;
          column-gap: 20px;
      }

      .qs-guide a {
          line-height: 1.5rem;
          font-weight: 400;
          cursor: pointer;
          text-decoration: underline;
          color: var(--link-color, #1259A5);
      }

      .qs-guide a:hover {
          color: var(--link-color-hover, #c00);
      }

      .qs-guide h4 {
          margin: 1rem 0 0 0;
      }

      .qs-guide div {
          margin: 1rem 0 0 0;
          font-size: 1rem;
          line-height: 1.5rem;
          font-weight: 400;
      }

      .qs-guide .content-highlights {
          font-size: 0.7rem;
          line-height: 1rem;
          word-break: break-word;
          color: var(--content-highlight-color);

          p {
              margin: 0 0 .5rem;
          }
      }

      .qs-guide .origin {
          background-size: 100px 25px;
          background-repeat: no-repeat;
          background-position: center;
          width: 100px;
          height: 30px;
          display: block;
          vertical-align: middle;
      }

      .qs-guide .origin.quarkus {
          background-image: url('${unsafeCSS(icons.origins.quarkus)}');
      }

      .qs-guide .origin.quarkiverse-hub {
          background-image: url('${unsafeCSS(icons.origins.quarkiverse)}');
      }
    
      .qs-guide-icon svg {
        width: 70px;
        margin: 1rem 0 0 0;
        fill: var(--main-text-color);
      }

      .qs-guide .status-tag {
          cursor: default;
          font-size: 0.6em;
          line-height: 1em;
          text-transform: uppercase;
          font-weight: bold;
          display: inline-block;
          padding: 4px 12px;
          border-radius: 50px;
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

      .summary {
          min-height: 40px;
      }
  `;

  @property({type: Object}) data: any;
  @property({type: String}) type: string =  "default";
  @property({type: String}) status: string;
  @property({type: String}) url: string;
  @property({type: String}) title: string;
  @property({type: String}) summary: string;
  @property({type: String}) keywords: string;
  @property({type: String}) content: string | [string]
  @property({type: String}) origin: string = "quarkus";
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
    return html`
      <div class="qs-hit qs-guide" aria-label="Guide Hit">
        <div class="qs-guide-icon">
          ${unsafeHTML(this.icon())}
        </div>
        <div>
          <h4>
            <a href="${this.relativizeUrl()}" target="_blank">${this._renderHTML(this.title)}</a>
            ${(this.origin && this.origin.toLowerCase() !== 'quarkus') ? html`<a href="${this._originLink()}" target="_blank" class="origin" title="${this._originTitle()}">${unsafeHTML(this._originIcon())}</a>` : ''}
          </h4>
          ${this.status && this.status !== 'stable' ? html`<span class="status-tag status-${this.status}" title="${this._statusHint()}">${this.status}</span>` : ''} 
          <div class="summary">
            <p>${this._renderHTML(this.summary)}</p>
          </div>
          <div class="keywords">${this._renderHTML(this.keywords)}</div>
          <div class="content-highlights">
            ${this._renderHTML(this.content)}
          </div>
        </div>
      </div>
    `;
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
    if(!content) {
      return content;
    }
    if(Array.isArray(content)) {
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

  private _originIcon():string {
    const icon = icons.origins['quarkiverse-hub' === this.origin ? 'quarkiverse' : this.origin];
    console.log(icon)
    return this._iconToSvg(icon);
  }

  private _iconToSvg(icon: string):string {
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
        case 'experimental': return 'Early feedback is requested to mature the idea';
        case 'preview': return 'Backward compatibility and presence in the ecosystem is not guaranteed';
        case 'deprecated': return 'This extension is likely to be replaced or removed';
        default: return '';
    }
  }
}
