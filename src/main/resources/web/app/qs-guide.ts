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
          --link-color: #1259A5;
          --link-hover-color: #c00;
          --content-highlight-color: #777;
      }

      @media screen and (max-width: 1300px) {
          .qs-hit {
              grid-column: span 6;
          }
      }

      .highlighted {
          font-weight: bold;
      }

      .qs-guide {
          background-size: 70px 70px;
          background-repeat: no-repeat;
          background-image: url('${unsafeCSS(icons.docs.guides)}');

          &.type-tutorial {
              background-image: url('${unsafeCSS(icons.docs.tutorials)}');
          }

          &.type-guide {
              background-image: url('${unsafeCSS(icons.docs.guides)}');
          }

          &.type-howto {
              background-image: url('${unsafeCSS(icons.docs.howto)}');
          }

          &.type-reference {
              background-image: url('${unsafeCSS(icons.docs.reference)}');
          }

          &.type-pdf {
              background-image: url('${unsafeCSS(icons.docs.pdf)}');
          }

          &.type-concepts {
              background-image: url('${unsafeCSS(icons.docs.concepts)}');
          }
      }

      .qs-guide a {
          line-height: 1.5rem;
          font-weight: 400;
          cursor: pointer;
          text-decoration: underline;
          color: var(--link-color);
      }

      .qs-guide a:hover {
          color: var(--link-hover-color);
      }

      .qs-guide h4 {
          margin: 1rem 0 0 90px;
      }

      .qs-guide div {
          margin: 1rem 0 0 90px;
          font-size: 1rem;
          line-height: 1.5rem;
          font-weight: 400;
      }

      .qs-guide .content-highlights {
          font-size: 0.7rem;
          line-height: 1rem;
          color: var(--content-highlight-color);

          p {
              margin: 0 0 .5rem;
          }
      }

      .qs-guide .origin {
          background-size: 20px 20px;
          background-repeat: no-repeat;
          background-position: center;
          margin-left: 5px;
          width: 20px;
          height: 20px;
          display: inline-block;
          vertical-align: middle;
      }

      .qs-guide .origin.quarkus {
          background-image: url('${unsafeCSS(icons.origins.quarkus)}');
      }

      .qs-guide .origin.quarkiverse-hub {
          background-image: url('${unsafeCSS(icons.origins.quarkiverse)}');
      }

      .summary {
          min-height: 40px;
      }
  `;

  @property({type: Object}) data: any;
  @property({type: String}) type: string =  "default";
  @property({type: String}) url: string;
  @property({type: String}) title: string;
  @property({type: String}) summary: string;
  @property({type: String}) keywords: string;
  @property({type: String}) content: string | [string]
  @property({type: String}) origin: string = "quarkus";


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
      <div class="qs-hit qs-guide type-${this.type}" aria-label="Guide Hit">
        <h4>
          <a href="${this.url}" target="${this.url.startsWith('http') ? '_blank' : ''}">${this._renderHTML(this.title)}</a>
          ${(this.origin && this.origin.toLowerCase() !== 'quarkus') ? html`<span class="origin ${this.origin}" title="${this.origin}"></span>` : ''}
        </h4>
        <div class="summary">
          <p>${this._renderHTML(this.summary)}</p>
        </div>
        <div class="keywords">${this._renderHTML(this.keywords)}</div>
        <div class="content-highlights">
          ${this._renderHTML(this.content)}
        </div>
        
      </div>
    `;
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
}