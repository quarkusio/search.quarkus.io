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

}
