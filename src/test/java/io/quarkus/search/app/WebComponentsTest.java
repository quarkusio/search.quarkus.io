package io.quarkus.search.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.List;

import io.quarkus.search.app.testsupport.QuarkusIOSample;
import io.quarkus.search.app.testsupport.SetupUtil;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;

@QuarkusTest
@WithPlaywright(verbose = true)
@QuarkusIOSample.Setup
public class WebComponentsTest {

    private static final String LABEL_SEARCH_QUERY = "[aria-label='Search Query']";
    private static final String LABEL_SEARCH_HITs = "[aria-label='Search Hits']";
    private static final String LABEL_GUIDE_HIT = "[aria-label='Guide Hit']";
    private static final String PIN_TOGGLE = ".pin-toggle";

    @InjectPlaywright
    BrowserContext context;

    @TestHTTPResource("/")
    URL indexPage;

    @BeforeAll
    static void setup() {
        SetupUtil.waitForIndexing(WebComponentsTest.class);
    }

    @Test
    public void testSearch() {
        final Page page = context.newPage();
        Response response = page.navigate(indexPage.toString());
        Assertions.assertEquals("OK", response.statusText());

        page.waitForLoadState();

        String title = page.title();
        Assertions.assertEquals("Quarkus Search", title);

        ElementHandle searchInput = page.waitForSelector(LABEL_SEARCH_QUERY);
        searchInput.fill("rest");
        page.waitForSelector(LABEL_SEARCH_HITs);
        List<ElementHandle> hits = page.querySelectorAll(LABEL_GUIDE_HIT);
        assertThat(hits).isNotEmpty();
    }

    @Test
    public void noPinToggleWithoutPinnedGroup() {
        // e.g. on Quarkiverse: the target isn't pinnable
        final Page page = context.newPage();
        page.navigate(indexPage.toString());
        page.waitForSelector(LABEL_SEARCH_QUERY).fill("rest");
        page.waitForSelector(LABEL_GUIDE_HIT);
        assertThat(page.querySelectorAll(LABEL_GUIDE_HIT)).isNotEmpty();
        assertThat(page.querySelectorAll(PIN_TOGGLE)).isEmpty();
    }

    @Test
    public void userPinnedGuides() {
        final Page page = context.newPage();
        page.navigate(indexPage.toString());
        page.waitForLoadState();
        page.evaluate(
                """
                        () => {
                          localStorage.clear();
                          const target = document.querySelector('qs-target');
                          target.insertAdjacentHTML('beforeend', `
                            <qs-guide-group pinned title="Pinned"><ul class="guide-list">
                              <li><qs-guide pinned url="/guides/all-config" title="All configuration options"></qs-guide></li>
                            </ul></qs-guide-group>
                            <qs-guide-group category="core" title="Core"><ul class="guide-list">
                              <li><qs-guide url="https://quarkus.io/guides/a" title="Guide A"></qs-guide></li>
                              <li><qs-guide url="https://quarkus.io/guides/b" title="Guide B"></qs-guide></li>
                              <li><qs-guide url="https://quarkus.io/guides/c" title="Guide C"></qs-guide></li>
                              <li><qs-guide url="https://quarkus.io/guides/all-config" title="All configuration options"></qs-guide></li>
                            </ul></qs-guide-group>`);
                          target.setAttribute('pinnable', '');
                        }
                        """);
        // Site-curated pins show a disabled toggle, wherever they are
        page.waitForSelector("qs-guide-group[category=core] qs-guide " + PIN_TOGGLE);
        assertThat(page.querySelectorAll("qs-guide[url$='/all-config'] " + PIN_TOGGLE + ".curated[aria-disabled=true]"))
                .hasSize(2);
        page.click("qs-guide-group[category=core] qs-guide[url$='/all-config'] " + PIN_TOGGLE,
                new Page.ClickOptions().setForce(true));
        assertThat(userPinnedKeys(page)).isEmpty();

        for (String guide : List.of("a", "b", "c")) {
            page.click("qs-guide-group[category=core] qs-guide[url$='/" + guide + "'] " + PIN_TOGGLE);
        }
        assertThat(userPinnedKeys(page)).containsExactly("guides/a", "guides/b", "guides/c");

        // A pin change in another tab keeps the focus on the remaining pinned cards
        page.focus(userPinnedToggle("guides/b"));
        page.evaluate("""
                () => {
                  localStorage.setItem('qs-pinned-guides', JSON.stringify(['guides/b', 'guides/c']));
                  window.dispatchEvent(new StorageEvent('storage', {key: 'qs-pinned-guides'}));
                }
                """);
        assertThat(userPinnedKeys(page)).containsExactly("guides/b", "guides/c");
        assertThat(focusedUserPinnedKey(page)).isEqualTo("guides/b");

        // Unpinning the focused card moves the focus to the next pinned card
        page.keyboard().press("Enter");
        assertThat(userPinnedKeys(page)).containsExactly("guides/c");
        assertThat(focusedUserPinnedKey(page)).isEqualTo("guides/c");

        // Only the keys of the pinned guides are stored
        assertThat(page.evaluate("() => localStorage.getItem('qs-pinned-guides')")).isEqualTo("[\"guides/c\"]");
    }

    private static String userPinnedToggle(String key) {
        return "qs-guide-group[pinned] li[data-user-pinned='" + key + "'] " + PIN_TOGGLE;
    }

    @SuppressWarnings("unchecked")
    private static List<String> userPinnedKeys(Page page) {
        return (List<String>) page.evaluate(
                "() => Array.from(document.querySelectorAll('qs-guide-group[pinned] li[data-user-pinned]'), li => li.dataset.userPinned)");
    }

    private static String focusedUserPinnedKey(Page page) {
        return (String) page.evaluate(
                """
                        () => {
                          const guide = document.activeElement;
                          const focused = guide?.shadowRoot?.activeElement;
                          return focused?.classList.contains('pin-toggle') ? guide.closest('li[data-user-pinned]')?.dataset.userPinned : null;
                        }
                        """);
    }
}
