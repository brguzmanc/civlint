package com.bguzman.civlint.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bguzman.civlint.adapters.EvaluationRunJpaRepository;
import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Digest;
import com.bguzman.civlint.support.Json;
import com.bguzman.civlint.support.JsonPath;
import com.bguzman.civlint.support.JsonReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * End-to-end test over a running application: Flyway migration, JPA persistence, REST API and the
 * rendered dashboard.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CivLintApiIT {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private EvaluationRunJpaRepository runEntities;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(URI.create(url(path))).GET().build());
    }

    private HttpResponse<String> post(String path) {
        return send(HttpRequest.newBuilder(URI.create(url(path)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AssertionError("HTTP request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted during " + request.uri(), e);
        }
    }

    @Test
    @DisplayName("the application boots, migrates the schema and reports healthy")
    void bootsHealthy() {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("UP");
    }

    @Test
    @DisplayName("sensitive actuator endpoints are not exposed")
    void sensitiveEndpointsAreClosed() {
        for (String path : new String[] {"/actuator/env", "/actuator/beans",
                "/actuator/configprops", "/actuator/mappings", "/actuator/heapdump"}) {
            assertThat(get(path).statusCode())
                    .as("%s must not be exposed", path)
                    .isEqualTo(404);
        }
    }

    @Test
    @DisplayName("the H2 console is not reachable")
    void h2ConsoleIsClosed() {
        assertThat(get("/h2-console").statusCode()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("an evaluation can be started, stored and read back")
    void evaluationLifecycle() {
        HttpResponse<String> created = post("/api/evaluations?mode=ADVANCED");
        assertThat(created.statusCode()).isEqualTo(201);
        String location = created.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith("/api/evaluations/RUN.ADVANCED.");

        HttpResponse<String> fetched = get(location);
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(created.body());

        // The API body is the canonical document, so its SHA-256 is verifiable from the response.
        Json parsed = JsonReader.read(fetched.body());
        assertThat(CanonicalJson.write(parsed)).isEqualTo(fetched.body());
        assertThat(Digest.sha256Hex(fetched.body())).hasSize(64);
        assertThat(JsonPath.string(parsed, "run", "mode")).isEqualTo("ADVANCED");
    }

    @Test
    @DisplayName("findings, metrics, traces, counterexamples and the report are all served")
    void subResources() {
        String location = post("/api/evaluations?mode=ADVANCED")
                .headers()
                .firstValue("Location")
                .orElseThrow();

        for (String suffix : new String[] {"/findings", "/metrics", "/traces", "/counterexamples"}) {
            HttpResponse<String> response = get(location + suffix);
            assertThat(response.statusCode()).as("%s", suffix).isEqualTo(200);
            assertThat(JsonReader.read(response.body())).isInstanceOf(Json.Arr.class);
        }

        HttpResponse<String> report = get(location + "/report");
        assertThat(report.statusCode()).isEqualTo(200);
        Json parsed = JsonReader.read(report.body());
        assertThat(JsonPath.string(parsed, "report", "canonicalHash")).hasSize(64);
        assertThat(JsonPath.string(parsed, "report", "dataMode")).isEqualTo("DEMO");
        assertThat(JsonPath.string(parsed, "report", "dataStatement")).contains("synthetic");
        assertThat(JsonPath.string(parsed, "report", "releaseOutcome")).isEqualTo("ALLOW");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADVANCED", "BASELINE"})
    @DisplayName("repeated evaluations of a mode return byte-identical response bodies")
    void repeatedPostsAreByteIdentical(String mode) {
        assertThat(post("/api/evaluations?mode=" + mode).body())
                .isEqualTo(post("/api/evaluations?mode=" + mode).body());
    }

    /**
     * Pins the property every published number rests on: for each mode, all five evidence surfaces
     * carry one hash. Any surface drifting off the shared replay-verified operation fails here.
     */
    @Test
    @DisplayName("every evidence surface publishes one canonical hash per mode")
    void everyEvidenceSurfaceAgrees() {
        String dashboard = get("/").body();
        Json published = JsonReader.read(readArtifact("evaluation-results.json"));
        String reportMarkdown = readArtifact("evaluation-report.md");

        for (String mode : new String[] {"advanced", "baseline"}) {
            HttpResponse<String> created = post("/api/evaluations?mode=" + mode.toUpperCase(Locale.ROOT));
            assertThat(created.statusCode()).isEqualTo(201);
            String digest = Digest.sha256Hex(created.body());

            assertThat(JsonPath.string(JsonPath.member(published, "results", mode), mode, "canonicalHash"))
                    .as("evaluation-results.json %s hash equals the digest of the POST body", mode)
                    .isEqualTo(digest);
            assertThat(reportMarkdown)
                    .as("evaluation-report.md displays the %s hash", mode)
                    .contains("`" + digest + "`");

            String location = created.headers().firstValue("Location").orElseThrow();
            assertThat(JsonPath.string(
                            JsonReader.read(get(location + "/report").body()), "report", "canonicalHash"))
                    .as("the stored %s report hash equals that digest", mode)
                    .isEqualTo(digest);
            assertThat(dashboard)
                    .as("the dashboard displays the %s hash", mode)
                    .contains(digest);
        }
    }

    /**
     * The stored row is the archival copy of the evidence, so its hash column must be the SHA-256 of
     * the document column beside it. Nothing else reads that column back, so without this a wrong
     * stored hash would persist silently while every other test passed.
     */
    @Test
    @DisplayName("each stored row's hash column is the digest of its own document column")
    void storedRowIsSelfVerifying() {
        for (String mode : new String[] {"ADVANCED", "BASELINE"}) {
            post("/api/evaluations?mode=" + mode);
        }

        assertThat(runEntities.findAllByOrderByRunIdAsc())
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.getCanonicalHash())
                        .as("%s", row.getRunId())
                        .isEqualTo(Digest.sha256Hex(row.getCanonicalDocument())));
    }

    private static String readArtifact(String name) {
        Path path = Path.of(System.getProperty("civlint.artifact.root", "."), name);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read published artifact " + path, e);
        }
    }

    @Test
    @DisplayName("the policy pack, map, cases and comparison endpoints serve canonical JSON")
    void readOnlyEndpoints() {
        for (String path : new String[] {"/api/policy", "/api/human-necessity", "/api/procedures",
                "/api/cases", "/api/procedures/FCR.NAMECORR/comparison"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as("%s", path).isEqualTo(200);
            assertThat(CanonicalJson.write(JsonReader.read(response.body())))
                    .as("%s serves canonical JSON", path)
                    .isEqualTo(response.body());
        }
    }

    @Test
    @DisplayName("an unknown run yields a problem detail, not a stack trace")
    void unknownRun() {
        HttpResponse<String> response = get("/api/evaluations/RUN.DOES.NOT.EXIST");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Not found").doesNotContain("Exception");
    }

    @Test
    @DisplayName("an invalid mode yields a problem detail naming the permitted values")
    void invalidMode() {
        HttpResponse<String> response = post("/api/evaluations?mode=WISHFUL");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("ADVANCED").contains("BASELINE");
    }

    @Test
    @DisplayName("an unknown procedure or version yields 404")
    void unknownComparison() {
        assertThat(get("/api/procedures/NOPE/comparison").statusCode()).isEqualTo(404);
        assertThat(get("/api/procedures/FCR.NAMECORR/comparison?existing=V1.REGIONAL&proposed=V9.NOPE")
                        .statusCode())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("the dashboard renders every required view and states the data mode")
    void dashboardRenders() {
        String storedBefore = get("/api/evaluations").body();
        HttpResponse<String> response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("text/html");

        String html = response.body();
        assertThat(html)
                .contains("Synthetic demonstration data")
                .contains("id=\"overview\"")
                .contains("id=\"comparison\"")
                .contains("id=\"hnm\"")
                .contains("id=\"findings\"")
                .contains("id=\"counterexamples\"")
                .contains("id=\"metrics\"")
                .contains("id=\"traces\"")
                .contains("id=\"repro\"");
        // Real content, not empty tables.
        assertThat(html).contains("SEPARATION_OF_DUTIES_VIOLATED");
        assertThat(html).contains("APPEAL_ROUTE_REMOVED");
        assertThat(html).contains("HUMAN_GATE_SAFELY_REMOVED");
        assertThat(html).contains("CASE.15.DUTY.VIOLATION");
        assertThat(html).doesNotContain("Whitelabel Error Page");
        // Agent contribution is visible, and visibly advisory.
        assertThat(html)
                .as("a validated agent rationale reaches the page")
                .contains("Preparation and approval resolve to the same role");
        assertThat(html)
                .as("the advisory-authority disclaimer accompanies it")
                .contains("Advisory only")
                .contains("authorise, block or alter a release");
        // Agent-supplied text reaches the page as data, never as markup. The case-13 boundary
        // rationale contains an apostrophe, so its escaped form proves escaping is actually in force
        // rather than merely intended; switching any of these fields to th:utext fails here.
        assertThat(html)
                .as("agent rationale text is HTML-escaped when rendered")
                .contains("applicant&#39;s")
                .doesNotContain("applicant's");
        assertThat(get("/api/evaluations").body())
                .as("a read-only dashboard request must not persist runs")
                .isEqualTo(storedBefore);
    }

    @Test
    @DisplayName("stored runs are listed")
    void listRuns() {
        post("/api/evaluations?mode=BASELINE");
        post("/api/evaluations?mode=ADVANCED");
        HttpResponse<String> response = get("/api/evaluations");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("RUN.ADVANCED.").contains("RUN.BASELINE.");
    }

    @Test
    @DisplayName("the data-mode statement is served")
    void dataMode() {
        HttpResponse<String> response = get("/api/data-mode");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("DEMO").contains("Synthetic fictional data only");
    }

    // ---------------------------------------------------------------------------------------------
    // Presentation layer. The dashboard is a server-rendered, read-only evidence view: one local
    // stylesheet, no script, no external asset. These tests pin that contract rather than the
    // layout, so restyling stays cheap while the guarantees stay enforced.
    // ---------------------------------------------------------------------------------------------

    private static int count(String haystack, String needle) {
        int total = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            total++;
        }
        return total;
    }

    private static String section(String html, String id) {
        int start = html.indexOf("id=\"" + id + "\"");
        assertThat(start).as("section %s is present", id).isNotNegative();
        int end = html.indexOf("</section>", start);
        assertThat(end).as("section %s is closed", id).isNotNegative();
        return html.substring(start, end);
    }

    /**
     * Rebuilds {@code MetricResult.display()} from the canonical API document, so the expected text
     * is derived from the run rather than written into the test.
     */
    private static String metricDisplay(List<Json> metrics, String metricId) {
        for (Json metric : metrics) {
            if (!JsonPath.string(metric, "metric", "metricId").equals(metricId)) {
                continue;
            }
            if (!(JsonPath.member(metric, "metric", "value") instanceof Json.Num(BigDecimal value))) {
                return "UNAVAILABLE";
            }
            return switch (JsonPath.string(metric, "metric", "unit")) {
                case "PERCENT" -> value.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%";
                case "TOUCH_UNITS" -> value.toPlainString() + " touch units";
                default -> value.toPlainString();
            };
        }
        throw new AssertionError("No metric " + metricId + " in the canonical document");
    }

    private static String metricLabel(List<Json> metrics, String metricId) {
        for (Json metric : metrics) {
            if (JsonPath.string(metric, "metric", "metricId").equals(metricId)) {
                return JsonPath.string(metric, "metric", "label");
            }
        }
        throw new AssertionError("No metric " + metricId + " in the canonical document");
    }

    @Test
    @DisplayName("the local stylesheet is served as CSS and references nothing remote")
    void stylesheetIsServedLocally() {
        HttpResponse<String> response = get("/css/dashboard.css");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .as("the stylesheet is identified as CSS")
                .containsIgnoringCase("text/css");

        String css = response.body();
        assertThat(css)
                .as("both themes are defined explicitly rather than left to the browser")
                .contains("prefers-color-scheme: dark")
                .contains("--table-header-bg")
                .contains("--row-header-bg");
        assertThat(css)
                .as("no remote import, font or image")
                .doesNotContain("@import")
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain("url(");
    }

    @Test
    @DisplayName("the dashboard is responsive and carries the accessibility landmarks")
    void dashboardShellIsResponsiveAndLandmarked() {
        String html = get("/").body();

        assertThat(html)
                .as("the responsive viewport meta tag is present")
                .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"");

        assertThat(html)
                .as("the only stylesheet is the local one")
                .contains("<link rel=\"stylesheet\" href=\"/css/dashboard.css\"");
        assertThat(count(html, "rel=\"stylesheet\""))
                .as("exactly one stylesheet")
                .isEqualTo(1);
        // The only other link is an empty inline icon, which exists solely to avoid a favicon 404.
        assertThat(count(html, "<link")).isEqualTo(2);
        assertThat(html).contains("<link rel=\"icon\" href=\"data:,\"");

        assertThat(html)
                .as("skip link, main landmark and labelled navigation")
                .contains("class=\"skip-link\" href=\"#main-content\"")
                .containsPattern("<main[^>]*id=\"main-content\"")
                .containsPattern("<nav[^>]*aria-labelledby=\"section-nav-heading\"")
                .contains("id=\"section-nav-heading\"");

        assertThat(count(html, "class=\"table-scroll"))
                .as("every data table sits in its own horizontal scroll region")
                .isEqualTo(count(html, "<table "));
        assertThat(count(html, "<table ")).isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("the dashboard still exposes all eight evidence sections and names every table")
    void dashboardKeepsEverySection() {
        String html = get("/").body();
        for (String id : new String[] {"overview", "comparison", "hnm", "findings",
                "counterexamples", "metrics", "traces", "repro"}) {
            assertThat(html).as("section %s", id).contains("id=\"" + id + "\"");
        }
        // Progressive disclosure must not need script: details/summary only.
        assertThat(count(html, "<details>")).isGreaterThan(0);
        assertThat(count(html, "<summary>")).isEqualTo(count(html, "<details>"));
        // Every table is named, headed and scoped.
        assertThat(count(html, "<table aria-labelledby=")).isEqualTo(count(html, "<table "));
        assertThat(count(html, "<thead>")).isEqualTo(count(html, "<table "));
        assertThat(count(html, "<tbody>")).isEqualTo(count(html, "<table "));
        assertThat(html).contains("scope=\"col\"").contains("scope=\"row\"");
    }

    @Test
    @DisplayName("the dashboard carries no script, no inline style and no external resource")
    void dashboardCarriesNoClientSideCode() {
        String html = get("/").body();

        assertThat(html)
                .as("no script element")
                .doesNotContainIgnoringCase("<script")
                .doesNotContainIgnoringCase("</script>");
        assertThat(html)
                .as("no style block")
                .doesNotContainIgnoringCase("<style")
                .doesNotContainIgnoringCase("</style>");
        assertThat(html)
                .as("no inline style attribute")
                .doesNotContainPattern("(?i)<[a-z][^>]*\\sstyle\\s*=");
        assertThat(html)
                .as("no inline event handler")
                .doesNotContainPattern(
                        "(?i)\\son(click|load|error|submit|change|focus|blur|input|keydown|mouseover)"
                                + "\\s*=");
        assertThat(html)
                .as("no remote stylesheet, script, font or image")
                .doesNotContainPattern("(?i)(src|href)\\s*=\\s*[\"\']?(https?:)?//")
                .doesNotContainIgnoringCase("<img")
                .doesNotContainIgnoringCase("<iframe")
                .doesNotContainIgnoringCase("@font-face");
    }

    @Test
    @DisplayName("the executive summary renders model values, and rendering stores nothing")
    void summaryRendersModelValues() {
        List<Json> baselineMetrics = JsonPath.array(
                JsonReader.read(post("/api/evaluations?mode=BASELINE").body()), "run", "metrics");
        Json advancedRun = JsonReader.read(post("/api/evaluations?mode=ADVANCED").body());
        List<Json> advancedMetrics = JsonPath.array(advancedRun, "run", "metrics");
        String outcome = JsonPath.string(
                JsonPath.member(advancedRun, "run", "releaseDecision"), "releaseDecision", "outcome");

        String storedBefore = get("/api/evaluations").body();
        String summary = section(get("/").body(), "summary");

        assertThat(summary)
                .as("the outcome badge comes from the run, not from the template")
                .contains(outcome);
        assertThat(summary)
                .as("both architectures' oracle agreement is rendered from the model")
                .contains(metricDisplay(baselineMetrics, "M.ORACLE.AGREEMENT"))
                .contains(metricDisplay(advancedMetrics, "M.ORACLE.AGREEMENT"));
        assertThat(summary)
                .as("the safety and burden headlines are rendered from the model")
                .contains(metricDisplay(advancedMetrics, "M.GATES.UNSAFE.REMOVALS"))
                .contains(metricDisplay(advancedMetrics, "M.HUMAN.TOUCH.REDUCTION"));
        assertThat(summary)
                .as("each headline is captioned with the metric's own published label")
                .contains(metricLabel(advancedMetrics, "M.ORACLE.AGREEMENT"))
                .contains(metricLabel(advancedMetrics, "M.GATES.UNSAFE.REMOVALS"))
                .contains(metricLabel(advancedMetrics, "M.HUMAN.TOUCH.REDUCTION"));
        assertThat(summary)
                .as("the summary is a product-level view, not a second copy of the metric table")
                .doesNotContain("<table");

        assertThat(get("/api/evaluations").body())
                .as("rendering the dashboard must not persist a run")
                .isEqualTo(storedBefore);
    }
}
