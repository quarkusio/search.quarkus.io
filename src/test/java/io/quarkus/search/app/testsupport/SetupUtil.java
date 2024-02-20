package io.quarkus.search.app.testsupport;

import static io.restassured.RestAssured.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.pollinterval.FibonacciPollInterval;

public final class SetupUtil {
    private SetupUtil() {
    }

    public static int managementPort(Class<?> testClass) {
        if (testClass.getName().endsWith("IT")) {
            return 9000;
        } else {
            return 9001;
        }
    }

    public static void waitForIndexing(Class<?> testClass) {
        Awaitility.await().timeout(Duration.ofMinutes(1))
                .pollInterval(FibonacciPollInterval.fibonacci(1, TimeUnit.SECONDS))
                .untilAsserted(() -> when().get("http://localhost:" + managementPort(testClass) + "/q/health/ready")
                        .then()
                        .statusCode(200));
    }

}
