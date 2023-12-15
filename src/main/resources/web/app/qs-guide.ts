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
    }

    .summary {
      min-height: 40px;
    }
  `;

  @property({type: Object}) data: any;


  connectedCallback() {
    super.connectedCallback();
  }

  disconnectedCallback() {
    super.disconnectedCallback();
  }

  render() {
    return html`
      <div class="qs-hit qs-guide type-${this.data.type}">
        <h4><a href="${this.data.url}">${this._renderContent(this.data.title)}</a></h4>
        <div class="summary">
          <p>${this._renderContent(this.data.summary)}</p>
        </div>
        <div class="keywords">${this._renderContent(this.data.keywords)}</div>
        <div class="content-highlights">
          ${this._renderContent(this.data.content)}
        </div>
      </div>
    `;
  }

  private _renderContent(content: string) {
    if(!content) {
      return content;
    }
    if(Array.isArray(content)) {
      return content.map((c) => html`<p>${this._renderContent(c)}</p>`);
    }
    return unsafeHTML(content);
  }
}