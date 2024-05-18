import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final AtomicInteger requestCount;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestCount = new AtomicInteger(0);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient();

        // Используем планировщик задач для обнуления количества запросов через фиксированные промежутки времени
        this.scheduler.scheduleAtFixedRate(() -> requestCount.set(0), 0, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        synchronized (this) {
            while (requestCount.get() >= requestLimit) {
                wait();
            }
            requestCount.incrementAndGet();
        }

        String jsonBody = objectMapper.writeValueAsString(new DocumentRequest(document, signature));
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Код исключения " + response);
            }
        } finally {
            synchronized (this) {
                requestCount.decrementAndGet();
                notifyAll();
            }
        }
    }

    public static class Document {
        // Поля документа (выбраны произвольно для примера)
        private String participantInn;
        private String docId;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String docType;
        private boolean importRequest;

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public String getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }
    }

    private static class DocumentRequest {
        private final Document description;
        private final String signature;

        public DocumentRequest(Document document, String signature) {
            this.description = document;
            this.signature = signature;
        }

        public Document getDescription() {
            return description;
        }

        public String getSignature() {
            return signature;
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);
        Document document = new Document();
        // Задаем поля документа (для примера)
        document.setParticipantInn("9999999999");
        document.setDocId("333");
        document.setOwnerInn("Владелец");
        document.setProducerInn("Производитель");
        document.setProductionDate("2024-05-01");
        document.setDocType("ProductDescription");
        document.setImportRequest(true);

        String signature = "Подпись";

        api.createDocument(document, signature);
    }
}
