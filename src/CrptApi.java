import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final HttpClient httpClient;
    private final String apiUrl;
    private final ChronoUnit timeUnit;
    private final int requestLimit;
    private final Duration interval;
    private final Semaphore semaphore;
    private final ScheduledExecutorService executorService;

    public CrptApi(String apiUrl, ChronoUnit timeUnit, int requestLimit) {
        this.apiUrl = apiUrl;
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.interval = Duration.of(1, timeUnit.SECONDS);
        this.semaphore = new Semaphore(requestLimit, true);
        this.executorService = Executors.newScheduledThreadPool(1);
        this.executorService.scheduleAtFixedRate(this::resetSemaphore, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        this.httpClient = HttpClient.newHttpClient();
    }

    private void resetSemaphore() {
        semaphore.release(requestLimit);
    }

    public void createDocument(String document, String signature) throws InterruptedException {
        semaphore.acquire();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(document))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("API Request failed with status: " + response.statusCode());
            }
            request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl))
                    .header("Content-Type", "application/plain-text")
                    .POST(HttpRequest.BodyPublishers.ofString(signature))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("API Request failed with status: " + response.statusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }

    private static String readJsonFile(String filePath) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath));
        return new String(bytes);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi crpt = new CrptApi("https://ismp.crpt.ru/api/v3/lk/documents/create", ChronoUnit.SECONDS, 10);
        crpt.createDocument(readJsonFile("json"), "abc");

        crpt.createDocument(readJsonFile("json"), "abc");
        crpt.createDocument(readJsonFile("json"), "abc");
        crpt.createDocument(readJsonFile("json"), "abc");
    }

    public void close() {
        executorService.shutdown();
    }
}