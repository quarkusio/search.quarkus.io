import {LitElement, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {QS_GROUPED_RESULT_EVENT, QS_RESULT_EVENT, QS_START_EVENT, QsGroupedResult} from './qs-form';

export interface TocCategory {
  id: string;
  title: string;
  subcategories?: TocCategory[];
}

@customElement('qs-categories-toc')
export class QsCategoriesToc extends LitElement {

  static styles = css`
    :host {
      display: block;
    }

    h3 {
      margin: 0 0 0.4rem 0;
      font-size: 1.125rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--main-text-color);
    }

    ul {
      list-style: none;
      padding: 0;
      margin: 0;
    }

    .toc > ul > li {
      display: block;
      margin-bottom: 0.35rem;
      padding: 0;
    }

    .toc > ul > li > a {
      display: block;
      padding: 0.15rem 0.4rem;
      font-size: 1rem;
      font-weight: 600;
      line-height: 1.5;
      color: var(--main-text-color);
      text-decoration: none;
      border-radius: 3px;
      cursor: pointer;
    }

    .toc > ul > li > a:hover {
      background-color: var(--hover-background-color, rgba(0, 0, 0, 0.05));
      color: var(--link-color, #1259A5);
    }

    /* Subcategory chips */
    .toc > ul > li > ul {
      padding: 0.1rem 0 0.2rem 0.6rem;
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem;
    }

    .toc > ul > li > ul > li {
      display: inline;
      line-height: 1;
    }

    .toc > ul > li > ul > li > a {
      display: inline-block;
      padding: 0.1rem 0.45rem;
      font-size: 0.9rem;
      line-height: 1.5;
      color: var(--main-text-color);
      opacity: 0.75;
      text-decoration: none;
      border-radius: 8px;
      cursor: pointer;
    }

    .toc > ul > li > ul > li > a:hover {
      opacity: 1;
      color: var(--link-hover-color, white);
      background-color: var(--link-color, #1259A5);
    }

    .count {
      font-weight: 400;
      font-size: 0.75rem;
      opacity: 0.7;
    }
  `;

  @property({type: Array}) categories: TocCategory[] = [];
  @property({type: Object, attribute: 'categories-meta'}) categoriesMeta: Record<string, { title: string, description?: string }> = {};

  @state() private _groupedResult: QsGroupedResult | undefined;

  private _form: HTMLElement;

  connectedCallback() {
    super.connectedCallback();
    this._form = document.querySelector('qs-form');
    if (this._form) {
      this._form.addEventListener(QS_GROUPED_RESULT_EVENT, this._handleGroupedResult);
      this._form.addEventListener(QS_RESULT_EVENT, this._handleResult);
      this._form.addEventListener(QS_START_EVENT, this._loadingStart);
    }
  }

  disconnectedCallback() {
    if (this._form) {
      this._form.removeEventListener(QS_GROUPED_RESULT_EVENT, this._handleGroupedResult);
      this._form.removeEventListener(QS_RESULT_EVENT, this._handleResult);
      this._form.removeEventListener(QS_START_EVENT, this._loadingStart);
    }
    super.disconnectedCallback();
  }

  private get _isSearchMode(): boolean {
    return !!(this._groupedResult?.categories?.length);
  }

  render() {
    if (this._isSearchMode) {
      return this._renderSearchToc();
    }
    if (this.categories && this.categories.length > 0) {
      return this._renderBrowseToc();
    }
    return html`<slot></slot>`;
  }

  private _renderBrowseToc() {
    return html`
      <div class="toc">
        <h3>Categories</h3>
        <ul>
          ${this.categories.map(cat => html`
            <li>
              <a @click=${(e: Event) => this._scrollToCategory(e, cat.id)}>${cat.title}</a>
              ${cat.subcategories && cat.subcategories.length > 0 ? html`
                <ul>
                  ${cat.subcategories.map(sub => html`
                    <li><a @click=${(e: Event) => this._scrollToCategory(e, sub.id)}>${sub.title}</a></li>
                  `)}
                </ul>
              ` : ''}
            </li>
          `)}
        </ul>
      </div>
    `;
  }

  private _renderSearchToc() {
    return html`
      <div class="toc">
        <h3>Categories</h3>
        <ul>
          ${this._groupedResult.categories.map(cat => {
            const meta = this.categoriesMeta?.[cat.category] || {};
            const title = meta.title || cat.category;
            return html`
              <li>
                <a @click=${(e: Event) => this._scrollToCategory(e, cat.category)}>
                  ${title} <span class="count">(${cat.hitCount})</span>
                </a>
              </li>
            `;
          })}
        </ul>
      </div>
    `;
  }

  private _scrollToCategory(e: Event, category: string) {
    e.preventDefault();
    const target = document.querySelector('qs-target');
    if (target?.shadowRoot) {
      let group = target.shadowRoot.querySelector(`qs-guide-group[category="${CSS.escape(category)}"]`);
      if (!group) {
        group = target.querySelector(`qs-guide-group[category="${CSS.escape(category)}"]`);
      }
      if(group) {
        group.scrollIntoView({behavior: 'instant', block: 'start'});
      }
    }
  }

  private _handleGroupedResult = (e: CustomEvent) => {
    this._groupedResult = e.detail;
  }

  private _handleResult = (e: CustomEvent) => {
    this._groupedResult = undefined;
  }

  private _loadingStart = () => {
    // TODO: maybe add loader later ?
  }
}
