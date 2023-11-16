import {LitElement, html, css, nothing, render} from 'lit';
import {customElement, state, property} from 'lit/decorators.js';
import debounce from 'lodash/debounce';

export const QS_START_EVENT = 'qs-start';
export const QS_END_EVENT = 'qs-end';
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
    .quarkus-search {
      display: block !important;
    }

    .d-none {
      display: none;
    }
  `;

  @property({type: String}) api: string = "/api/guides/search";
  @property({type: String}) minChars: number = 3;
  @property({type: String}) quarkusVersion: string;

  private _page: number = 0;
  private _total: number = 0;
  private _loading: boolean = false;

  render() {
    return html`
      <div id="qs-form">
        <slot></slot>
      </div>
    `;
  }

  connectedCallback() {
    super.connectedCallback();
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
    return this.querySelectorAll('input, select');
  }

  private _readQueryInputs() {
    const formElements = this._getFormElements();
    const formData = {};

    formElements.forEach((el: HTMLFormElement) => {
      if (el.value && el.value.length > 0) {
        formData[el.name] = el.value;
      }
    });

    console.log(JSON.stringify(formData));
    return formData;
  }

  private _search = () => {
    if (this._loading) {
      return;
    }
    this.dispatchEvent(new CustomEvent(QS_START_EVENT));
    const data = this._readQueryInputs();
    this._loading = true;
    this._jsonFetch(new AbortController(), 'GET', data, 1000)
      .then((r: any) => {
        let event = QS_RESULT_EVENT
        if (this._page > 0) {
          this._total += r.hits.length;
        } else {
          this._total = r.hits.length;
        }
        const hasMoreHits = r.hits.length > 0 && r.total > this._total;

        this.dispatchEvent(new CustomEvent(event, {detail: {...r, page: this._page, hasMoreHits}}));
      }).finally(() => {
      this._loading = false;
      this.dispatchEvent(new CustomEvent(QS_END_EVENT));
    });
  }

  private _searchDebounced = debounce(this._search, 300);

  private _handleInputChange = (e: Event) => {
    const target = e.target as HTMLFormElement;
    console.log(target.value);
    if (this._isInput(target)) {
      if (target.value.length === 0) {
        this._clearSearch();
        return;
      }
      if (target.value.length < this.minChars) {
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
    const response = await fetch(this.api + '?' + (new URLSearchParams(queryParams)).toString(), {
      method: method,
      mode: 'cors',
      headers: {
        'Access-Control-Allow-Origin':'*'
      },
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
    this._total = 0;
    this.dispatchEvent(new CustomEvent(QS_RESULT_EVENT, {detail: {}}));
  }
}