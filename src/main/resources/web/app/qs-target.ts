import {LitElement, html, css, unsafeCSS} from 'lit';
import {customElement, property, state, queryAll} from 'lit/decorators.js';
import './qs-guide'
import {QS_NEXT_PAGE_EVENT, QS_QUERY_SUGGESTION_EVENT, QS_RESULT_EVENT, QS_START_EVENT, QsResult} from "./qs-form";
import debounce from 'lodash/debounce';
import icons from "./assets/icons";
import { unsafeHTML } from 'lit/directives/unsafe-html.js';


/**
 * This component is the target of the search results
 */
@customElement('qs-target')
export class QsTarget extends LitElement {

  static styles = css`
    
    .loading {
      background-image: url('${unsafeCSS(icons.loading)}');
      background-repeat: no-repeat;
      background-position: top;
      background-size: 45px;
      padding-top: 55px;
      text-align: center;
      padding-bottom: 70px;
    }
    
    .qs-hits {
      display: grid;
      grid-template-columns: repeat(12, 1fr);
      grid-gap: 1em;
      clear: both;
      margin-bottom: 4em;
    }
    
    .no-hits {
      padding: 10px;
      margin: 6em 10px 6em 10px;
      font-size: 1.2rem;
      line-height: 1.5;
      font-weight: 400;
      font-style: italic;
      text-align: center;
      background: var(--empty-background-color, #F0CA4D);
      box-shadow: 0 4px 10px 0 rgba(0, 0, 0, 0.05), 0 4px 20px 0 rgba(0, 0, 0, 0.05);
    }

    .search-result-title {
      margin-top: 2.5rem;
      font-weight: var(--heading-font-weight);
    }

    .result-message {
      box-shadow: 0 4px 10px 0 rgba(0, 0, 0, 0.05), 0 4px 20px 0 rgba(0, 0, 0, 0.05);
      background: var(--empty-background-color, #F0CA4D);
      padding: 10px 10px 10px 90px;
      font-size: 0.9rem;
      
      .suggestion {
        text-decoration: underline;
        cursor: pointer;
        .highlighted {
          font-weight: bold;
        }
      }
    }

    qs-guide {
      grid-column: span 4;
      margin: 1rem 0rem 1rem 0rem;

      @media screen and (max-width: 1300px) {
        grid-column: span 6;
      }

      @media screen and (max-width: 768px) {
        grid-column: span 12;
        margin: 1rem 0rem 1rem 0rem;
      }

      @media screen and (max-width: 480px) {
        grid-column: span 12;
      }
    }
   
  `;

  @property({type: String, attribute: 'search-results-title'}) searchResultsTitle: string = '';
  @property({type: String}) private type: string = "guide";
  @property({type: String, attribute: 'origins-with-relative-urls'}) originsWithRelativeUrls: string[] = [];
  @state() private _result: QsResult | undefined;
  @state() private _loading = true;
  @queryAll('.qs-hit') private _hits: NodeListOf<HTMLElement>;

  private _form: HTMLElement;

  connectedCallback() {
    super.connectedCallback();
    this._form = document.querySelector("qs-form");
    this._form.addEventListener(QS_RESULT_EVENT, this._handleResult);
    this._form.addEventListener(QS_START_EVENT, this._loadingStart);
    document.addEventListener('scroll', this._handleScrollDebounced)
  }

  disconnectedCallback() {
    this._form.removeEventListener(QS_RESULT_EVENT, this._handleResult);
    this._form.removeEventListener(QS_START_EVENT, this._loadingStart);
    document.removeEventListener('scroll', this._handleScrollDebounced);
    super.disconnectedCallback();
  }

  render() {
    if (this._result?.hits) {
      if (this._result.hits.length === 0) {
        return html`
          <div id="qs-target" class="no-hits">
            <p>Sorry, no ${this.type}s matched your search. Please try again.</p>
          </div>
        `;
      }
      const result = this._result.hits.map(i => this._renderHit(i));
      if (this._result.suggestion) {
        return html`
          ${this.searchResultsTitle === '' ? '' : html`<h1 class="search-result-title">${this.searchResultsTitle}</h1>`}
            <div class="result-message">
              <p class="">No ${this.type}s matched your original search query.
                  Showing results for <span class="suggestion" @click=${this._querySuggestion}>${unsafeHTML(this._result.suggestion.highlighted)}</span> instead.</p>
            </div>
            <div id="qs-target" class="qs-hits" aria-label="Search Hits">
              ${result}
            </div>
            ${this._loading ? this._renderLoading() : ''}
          `;
      } else {
        return html`
          ${this.searchResultsTitle === '' ? '' : html`<h1 class="search-result-title">${this.searchResultsTitle}</h1>`}
          <div id="qs-target" class="qs-hits" aria-label="Search Hits">
            ${result}
          </div>
          ${this._loading ? this._renderLoading() : ''}
        `;
      }
    }
    if (this._loading) {
      return html`
        <div id="qs-target">${this._renderLoading()}</div>`;
    }
    return html`
      <div id="qs-target">
        <slot></slot>
      </div>
    `;
  }


  private _renderLoading() {
    return html`
      <div class="loading">Searching...</div>
    `;
  }

  private _renderHit(i) {
    switch (this.type) {
      case 'guide':
        return html`
          <qs-guide class="qs-hit" .data=${i} origins-with-relative-urls=${this.originsWithRelativeUrls}></qs-guide>`
    }
    return ''
  }


  private _handleScroll = (e) => {
    if (this._loading) {
      return;
    }
    if (!this._result) {
        // No search.
        return;
    }
    if (!this._result.hasMoreHits) {
      // No more hits to fetch.
      console.debug("no more hits");
      return
    }
    const lastHit = this._hits.length == 0 ? null : this._hits[this._hits.length - 1]
    if (!lastHit) {
      // No result card is being displayed at the moment.
      return
    }
    const scrollElement = document.documentElement // Scroll bar is on the <html> element
    const bottomOfViewport = scrollElement.scrollTop + scrollElement.clientHeight
    const topOfLastResultCard = lastHit.offsetTop
    if (bottomOfViewport >= topOfLastResultCard) {
      // We have scrolled to the bottom of the last result card.
      this._loading = true;
      this._form.dispatchEvent(new CustomEvent(QS_NEXT_PAGE_EVENT));
    }
  }
  private _handleScrollDebounced = debounce(this._handleScroll, 100);

  private _handleResult = (e: CustomEvent) => {
    console.debug("Received results in qs-target: ", e.detail);
    this._loadingEnd();
    if (!this._result || !e.detail || !e.detail.hits || e.detail.page === 0) {
      if(e.detail?.hits) {
        document.body.classList.add("qs-has-results");
      } else {
        document.body.classList.remove("qs-has-results");
      }
      this._result = e.detail;
      return;
    }
    this._result.hits = this._result.hits.concat(e.detail.hits);
    console.debug(`${this._result.hits.length} results in qs-target: `);
    this._result.hasMoreHits = e.detail.hasMoreHits;
  }

  private _loadingStart = (e:CustomEvent) => {
    this._loading = true;
    if(e.detail.page === 0) {
      this._result = undefined;
    }
  }

  private _loadingEnd = () => {
    this._loading = false;
  }

  private _querySuggestion() {
    this._form.dispatchEvent(new CustomEvent(QS_QUERY_SUGGESTION_EVENT, {detail: {suggestion: this._result.suggestion}}));
  }
}
