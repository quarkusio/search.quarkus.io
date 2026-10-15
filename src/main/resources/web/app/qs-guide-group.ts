import {LitElement, html, css, unsafeCSS, PropertyValues, render, nothing} from 'lit';
import {repeat} from 'lit/directives/repeat.js';
import {customElement, property, state} from 'lit/decorators.js';
import './qs-guide';
import type {QsGuide} from './qs-guide';
import icons from './assets/icons';
import {onPinsChanged, userPinnedGuides} from './pinned-guides';

export interface SearchContext {
  server: string;
  query: string;
  language: string;
  version?: string;
}

@customElement('qs-guide-group')
export class QsGuideGroup extends LitElement {

  static styles = css`
    .qs-guide-group {
      margin-bottom: 1.5rem;
    }

    .qs-guide-group-header {
      display: flex;
      align-items: baseline;
      margin-bottom: 0.95rem;
    }

    .qs-guide-group-header h2,
    .qs-guide-group-header h3 {
      margin: 0;
      font-weight: 600;
      white-space: nowrap;
    }

    .qs-guide-group-header h2 {
        font-size: 1.875rem;
        margin: 2rem 0 .75rem;
    }

    .qs-guide-group-header h3 {
        font-size: 1.05rem;
        padding-bottom: .5rem;
        color: var(--sub-title-text-color);
    }

    .count {
      margin-left: 0.5rem;
      font-size: 1.5rem;
      color: var(--content-highlight-color, #777);
      white-space: nowrap;
    }

    .title-line {
      background-color: color-mix(in srgb, var(--card-border-color) 33%, transparent);
      flex: 1;
      height: 1px;
      margin-left: 10px;
    }

    .qs-guide-group-description {
      margin: 0 0 0.5rem 1rem;
      color: var(--content-highlight-color, #555555);
      font-size: 1rem;
    }

    .qs-guide-group-content {
      display: grid;
      grid-template-columns: repeat(12, 1fr);
      grid-gap: 0.75rem;
      margin-bottom: 0.5rem;
    }

    .qs-guide-group-content qs-guide {
      grid-column: span 4;
    }

    @media screen and (max-width: 1300px) {
      .qs-guide-group-content qs-guide {
        grid-column: span 6;
      }
    }

    @media screen and (max-width: 1200px) {
      .qs-guide-group-description {
        margin: 0.5rem 0 0.5rem 0;
        display: block;
        white-space: normal
      }
    }

    @media screen and (max-width: 768px) {
      .qs-guide-group-content qs-guide {
        grid-column: span 12;
      }
    }

    .qs-guide-group-more {
      text-align: end;
      padding: 0.5rem 0 1rem 0;
    }

    .qs-guide-group-more button {
      background: none;
      border: none;
      border-radius: 0.5rem;
      padding: 0.5rem 1.5rem;
      cursor: pointer;
      font-size: 0.85rem;
      color: var(--main-text-color, black);
      transition: background-color 0.2s ease, border-color 0.2s ease;
    }

    .qs-guide-group-more button:hover {
      border-color: var(--card-border-hover-color, var(--link-color, #1259A5));
      background-color: var(--tag-chip-bg, #eef2f8);
    }

    .qs-guide-group-more button:disabled {
      opacity: 0.6;
      cursor: wait;
    }

    .chevron {
      display: inline-block;
      margin-left: 0.5rem;
      font-size: 0.7rem;
    }

    .loading-spinner {
      display: inline-block;
      width: 16px;
      height: 16px;
      background-image: url('${unsafeCSS(icons.loading)}');
      background-repeat: no-repeat;
      background-size: contain;
      vertical-align: middle;
      margin-left: 0.5rem;
    }
      
    .qs-guide-group-pinned .qs-guide-group-header h2 {
      color: var(--card-accent-color)
    }

    .qs-guide-group-pinned .qs-guide-group-header div.title-line {
      background-color: var(--card-accent-color)
    }
  `;

  @property({type: String}) category: string = '';
  @property({type: String}) title: string = '';
  @property({type: String}) description: string = '';
  @property({type: Number, attribute: 'hit-count'}) hitCount: number = 0;
  @property({type: Array}) hits: any[] = [];
  @property({type: Object, attribute: false}) searchContext: SearchContext | null = null;
  @property({type: String, attribute: 'origins-with-relative-urls'}) originsWithRelativeUrls: string[] = [];
  @property({type: Boolean}) subgroup: boolean = false;
  @property({type: Boolean}) pinned: boolean = false;

  @state() private _additionalHits: any[] = [];
  @state() private _loading: boolean = false;

  private _unsubscribePins: () => void;

  connectedCallback() {
    super.connectedCallback();
    if (!this.pinned) {
      return;
    }
    this._unsubscribePins = onPinsChanged(() => this._renderUserPinnedGuides());
    this._renderUserPinnedGuides();
  }

  disconnectedCallback() {
    this._unsubscribePins?.();
    super.disconnectedCallback();
  }

  /**
   * Renders the guides pinned by the visitor in the (server-rendered) list of this pinned group,
   * after the site-curated ones, so they get the site's own grid and card styling.
   * Cards are keyed by guide, so the ones that stay pinned keep their DOM nodes and focus.
   */
  private _renderUserPinnedGuides() {
    let list = this.querySelector(':scope > ul.guide-list');
    if (!list) {
      list = document.createElement('ul');
      list.className = 'guide-list';
      this.appendChild(list);
    }
    const guides = userPinnedGuides();

    // If the focused card is unpinned, move the focus to the next (or previous) pinned card, or to the group title
    const focused = (document.activeElement as Element)?.closest('li[data-user-pinned]');
    let focusTarget: string | null | undefined;
    if (focused && list.contains(focused) && !guides.some(g => g.key === focused.getAttribute('data-user-pinned'))) {
      const keys = Array.from(list.querySelectorAll(':scope > li[data-user-pinned]'), li => li.getAttribute('data-user-pinned'));
      const index = keys.indexOf(focused.getAttribute('data-user-pinned'));
      const remaining = new Set(guides.map(g => g.key));
      focusTarget = [...keys.slice(index + 1), ...keys.slice(0, index).reverse()].find(k => remaining.has(k)) ?? null;
    }

    render(html`${repeat(guides, g => g.key, g => html`
      <li data-user-pinned=${g.key}>
        <qs-guide pinned .data=${g} origins-with-relative-urls=${this.originsWithRelativeUrls}></qs-guide>
      </li>
    `)}`, list);

    if (focusTarget) {
      (list.querySelector(`:scope > li[data-user-pinned="${CSS.escape(focusTarget)}"] qs-guide`) as QsGuide)?.focusPinToggle();
    } else if (focusTarget === null) {
      this.renderRoot.querySelector<HTMLElement>('.qs-guide-group-header h2')?.focus();
    }
  }

  willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('hits') || changedProperties.has('category')) {
      this._additionalHits = [];
    }
  }

  private get _allHits(): any[] {
    return [...(this.hits || []), ...this._additionalHits];
  }

  private get _isSearchMode(): boolean {
    return !!(this.hits && this.hits.length > 0);
  }

  private get _displayedCount(): number {
    return this._allHits.length;
  }

  private get _hasMore(): boolean {
    return this.hitCount > this._displayedCount;
  }

  private get _remainingCount(): number {
    return Math.max(0, this.hitCount - this._displayedCount);
  }

  render() {
    if (this._isSearchMode) {
      return this._renderSearchMode();
    }
    return this._renderBrowseMode();
  }

  private _renderBrowseMode() {
    const pinnedClass = this.pinned ? 'qs-guide-group-pinned' : '';
    return html`
      <div class="qs-guide-group ${pinnedClass}">
        ${this._renderHeader()}
        <slot></slot>
      </div>
    `;
  }

  private _renderSearchMode() {
    const allHits = this._allHits;
    return html`
      <div class="qs-guide-group">
        ${this._renderHeader()}
        <div class="qs-guide-group-content">
          ${allHits.map(hit => html`
            <qs-guide .data=${hit} origins-with-relative-urls=${this.originsWithRelativeUrls}></qs-guide>
          `)}
        </div>
        ${this._hasMore ? this._renderShowMore() : ''}
      </div>
    `;
  }

  private _renderHeader() {
    const useH3 = this.subgroup;
    const displayTitle = this.title || this.category;

    return html`
      <div class="qs-guide-group-header">
        ${useH3
          ? html`<h3>${displayTitle}</h3>`
          : html`<h2 tabindex=${this.pinned ? '-1' : nothing}>
                  ${displayTitle}
                  ${this.hitCount > 0 ? html`<span class="count">(${this.hitCount})</span>` : ''}
                  ${this.description ? html`<span class="qs-guide-group-description">${this.description}</span>` : ''}
                </h2>`
        }
      </div>
    `;
  }

  private _renderShowMore() {
    const remaining = this._remainingCount;
    return html`
      <div class="qs-guide-group-more">
        <button @click=${this._handleShowMore} ?disabled=${this._loading}>
          ${this._loading
            ? html`Loading<span class="loading-spinner"></span>`
            : html`Show remaining ${remaining} guides <span class="chevron">▼</span>`
          }
        </button>
      </div>
    `;
  }

  private _handleShowMore = async () => {
    if (this._loading || !this.searchContext) return;

    this._loading = true;

    try {
      const allHits = this._allHits;
      const params = new URLSearchParams();

      if (this.searchContext.query) {
        params.append('q', this.searchContext.query);
      }
      params.append('categories', this.category);
      params.append('language', this.searchContext.language || 'en');
      if (this.searchContext.version) {
        params.append('version', this.searchContext.version);
      }
      params.append('contentSnippets', '0');

      for (const hit of allHits) {
        const url = hit.url || hit.id;
        if (url) {
          params.append('excludeIds', url);
        }
      }

      const fetchUrl = `${this.searchContext.server || ''}/api/guides/search?${params.toString()}`;

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);

      const response = await fetch(fetchUrl, {
        method: 'GET',
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (response.ok) {
        const data = await response.json();
        if (data.hits && data.hits.length > 0) {
          this._additionalHits = [...this._additionalHits, ...data.hits];
        }
      } else {
        console.error('Failed to fetch more guides:', response.status);
      }
    } catch (e) {
      console.error('Error fetching more guides:', e);
    } finally {
      this._loading = false;
    }
  }
}
