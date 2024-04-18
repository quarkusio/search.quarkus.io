import {css, html, LitElement} from 'lit-element';
import {customElement, property} from 'lit/decorators.js';
import debounce from 'lodash/debounce';
import {LocalSearch} from "./local-search";

export const QS_START_EVENT = 'qs-start';
export const QS_RESULT_EVENT = 'qs-result';
export const QS_NEXT_PAGE_EVENT = 'qs-next-page';

export interface QsResult {
  hits: QsHit[];
  hasMoreHits: boolean;
}

export interface QsHit {
  title: string;
  summary: string;
  url: string;
  keywords: string | undefined;
  content: string | undefined;
  type: string | undefined;
}

/**
 * This component is the form that triggers the search
 */
@customElement('qs-form')
export class QsForm extends LitElement {

  static styles = css`

      ::slotted(*) {
          display: block !important;
      }

      .quarkus-search {
          display: block !important;
      }

      .d-none {
          display: none;
      }
  `;

  @property({type: String}) server: string = "";
  @property({type: String, attribute: 'min-chars'}) minChars: number = 3;
  @property({type: String, attribute: 'initial-timeout'}) initialTimeout: number = 1500;
  @property({type: String, attribute: 'more-timeout'}) moreTimeout: number = 2500;
  @property({type: String}) language: String = "en";
  @property({type: String, attribute: 'quarkus-version'}) quarkusversion?: string;
  @property({type: String, attribute: 'local-search'}) localSearch: boolean = false;

  private _page: number = 0;
  private _currentHitCount: number = 0;
  private _abortController?: AbortController = null;

  render() {
    return html`
      <div id="qs-form">
        <slot></slot>
      </div>
    `;
  }

  connectedCallback() {
    super.connectedCallback();
    LocalSearch.enableLocalSearch();
    const formElements = this._getFormElements();
    this.addEventListener(QS_NEXT_PAGE_EVENT, this._handleNextPage);
    formElements.forEach((el) => {
      const eventName = this._isInput(el) ? 'input' : 'change';
      el.addEventListener(eventName, this._handleInputChange);
    });
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this.removeEventListener(QS_NEXT_PAGE_EVENT, this._handleNextPage);
    const formElements = this._getFormElements();
    formElements.forEach(el => {
      const eventName = this._isInput(el) ? 'input' : 'change';
      el.removeEventListener(eventName, this._handleInputChange);
    });
  }

  private _getFormElements(): NodeListOf<HTMLFormElement> {
    return this.querySelectorAll('input[name], select[name]');
  }

  private _readQueryInputs() {
    const formElements = this._getFormElements();
    const formData = {
      language: this.language,
      contentSnippets: 2,
      contentSnippetsLength: 120,
    };

    if (this.quarkusversion) {
      formData['version'] = this.quarkusversion;
    }
    var elements = 0;
    for (let el: HTMLFormElement of formElements) {
      if (elements == 0 && this._isInput(el) && (el.value.length === 0 || el.value.length < this.minChars)) {
        // Let's ignore if the search is empty of not long enough
        return null;
      }
      if (el.value && el.value.length > 0 && el.name.length > 0) {
        formData[el.name] = el.value;
        elements++;
      }
    }

    return formData;
  }

  private _search = () => {
    if (this._abortController) {
      // If a search is already in progress, abort it
      this._abortController.abort();
    }
    const data = this._readQueryInputs();
    if (!data) {
      this._clearSearch();
      return;
    }
    const controller = new AbortController();
    this._abortController = controller;
    this.dispatchEvent(new CustomEvent(QS_START_EVENT, {detail: {page: this._page}}));
    if (this.localSearch) {
      this._localSearch();
      if (this._abortController == controller) {
        this._abortController = null
      }
      return;
    }

    this._jsonFetch(controller, 'GET', data, this._page > 0 ? this.initialTimeout : this.moreTimeout)
      .then((r: any) => {
        if (this._page > 0) {
          this._currentHitCount += r.hits.length;
        } else {
          this._currentHitCount = r.hits.length;
        }
        const total = r.total?.lowerBound;
        const hasMoreHits = r.hits.length > 0 && total > this._currentHitCount;
        this.dispatchEvent(new CustomEvent(QS_RESULT_EVENT, {detail: {...r, search: data, page: this._page, hasMoreHits}}));
      }).catch(e => {
      console.error('Could not run search: ' + e);
      if (this._abortController != controller) {
        // A concurrent search erased ours; most likely input changed while waiting for results.
        // Ignore this search and let the concurrent one reset the data as it sees fit.
        return;
      }
      this._page = 0;
      this._currentHitCount = 0;
      // Fall back to Javascript in-page search
      this._localSearch();
    }).finally(() => {
      if (this._abortController == controller) {
        this._abortController = null
      }
    });
  }


  private _searchDebounced = debounce(this._search, 300);

  private _handleInputChange = (e: Event) => {
    const target = e.target as HTMLFormElement;
    if (this._isInput(target)) {
      if (target.value.length === 0 || target.value.length < this.minChars) {
        this._clearSearch();
        return;
      }
    }
    this._searchDebounced();
  }

  private _handleNextPage = (e: CustomEvent) => {
    this._page++;
    this._search();
  }

  private _isInput(el: HTMLFormElement) {
    return el.tagName.toLowerCase() === 'input'
  }

  private async _jsonFetch(controller: AbortController, method: string, params: object, timeout: number) {
    const queryParams: Record<string, string> = {
      ...params,
      'page': this._page.toString()
    };
    const timeoutId = setTimeout(() => controller.abort(), timeout)
    const response = await fetch(this.server + '/api/guides/search?' + (new URLSearchParams(queryParams)).toString(), {
      method: method,
      signal: controller.signal,
      body: null
    })
    clearTimeout(timeoutId)
    if (response.ok) {
      return await response.json()
    } else {
      throw 'Response status is ' + response.status + '; response: ' + await response.text()
    }
  }

  private _clearSearch() {
    this._page = 0;
    this._currentHitCount = 0;
    if (this._abortController) {
      this._abortController.abort();
      this._abortController = null;
    }
    this.dispatchEvent(new CustomEvent(QS_RESULT_EVENT));
  }

  private _localSearch(): any {
    let search = this._readQueryInputs();
    let hits = LocalSearch.search(search);
    if (!!hits) {
      const result = {
        hits,
        total: hits.length
      }
      this.dispatchEvent(new CustomEvent(QS_RESULT_EVENT, {detail: {...result, search, page: 0, hasMoreHits: false}}));
      return;
    }
    this.dispatchEvent(new CustomEvent(QS_RESULT_EVENT));
  }

}
