package io.quarkus.search.app;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URL;

@QuarkusTest
@WithPlaywright // <1>
public class WebComponentTest {
    @InjectPlaywright
    BrowserContext context;

    @TestHTTPResource("/")
    URL testPage;

    @Test
    public void testIndex() {
        final Page page = context.newPage();
        Response response = page.navigate(testPage.toString());
        Assertions.assertEquals("OK", response.statusText());

        page.waitForLoadState();

        String title = page.title();
        Assertions.assertEquals("Quarkus Search Web-Component test", title);
    }
}
