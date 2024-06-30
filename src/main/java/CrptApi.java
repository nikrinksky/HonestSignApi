import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс для взаимодействия с API Честного знака.
 */
public class CrptApi {
    private final String requestUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final LimitingRequests fixedWindowRateLimiter;


    /**
     * Конструктор класса CrptApi.
     *
     * @param timeUnit     Единица времени для ограничения количества запросов.
     * @param requestLimit Максимальное количество запросов в указанный промежуток времени.
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        fixedWindowRateLimiter = new LimitingRequests(requestLimit, timeUnit);
    }

    /**
     * Метод для отправки документа на API Честного знака.
     *
     * @param documentJson  Объект документа для отправки.
     * @param signature Подпись документа.
     * @return Ответ от сервера в виде строки.
     * @throws IOException  Если произошла ошибка при отправке запроса.
     */
    public void createDocument(Document documentJson, String signature) throws IOException {

        while (!fixedWindowRateLimiter.tryAcquire()){
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
        ObjectMapper objectMapper = new ObjectMapper();
        String body = objectMapper.writeValueAsString(documentJson);

        final HttpPost httpPost = new HttpPost(requestUrl);
        final StringEntity entity = new StringEntity(body);
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setHeader("Signature", signature);

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpResponse httpresponse = httpclient.execute(httpPost);
        Scanner sc = new Scanner((httpresponse).getEntity().getContent());

        while (sc.hasNext()) {
            System.out.println(sc.nextLine());
        }
    }

    // Симуляция использования класса CrptApi
    public static void main(String[] args) throws IOException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10);
        Document doc = new Document();


        // Имитируем несколько вызовов API
        for (int i = 0; i < 30; i++) {
            crptApi.createDocument(doc, "signature123");
        }

    }

    /**
     * Класс делит время на периоды фиксированного размера
     * и подсчитывает количество запросов в каждом периоде,
     * блокируя любые запросы, превышающие лимит.
     */
    public static class LimitingRequests {
        // Максимальное количество разрешенных запросов.
        private final int threshold;
        // Время начала текущего запроса.
        private volatile long RequestStartTime;
        // Продолжительность запросов в миллисекундах устанавливается равной одной секунде.
        private final long RequestUnit;
        // Счетчик количества запросов в текущем периоде
        private final AtomicInteger counter = new AtomicInteger();


        /**
         * Конструктор создает лимит запросов
         *
         * @param threshold пороговое значение - максимальное количество разрешенных запросов для каждого периода.
         */
        public LimitingRequests(int threshold, TimeUnit timeUnit) {
            RequestUnit = timeUnit.toMillis(10L);
            this.threshold = threshold;
            this.RequestStartTime = System.currentTimeMillis();
        }

        /**
         * Метод, который пытается получить разрешение по запросу.
         *
         * @return значение true, если запрос находится в пределах порогового значения; значение false в противном случае.
         */
         public boolean tryAcquire() {
            long currentTime = System.currentTimeMillis();
            // Если текущее время превышает время начала работы периода плюс заданный временной период,
            // это означает, что мы перешли к новому периоду. Сбросьте счетчик и обновите время начала работы периода.
            if (currentTime - RequestStartTime >= RequestUnit) {
                // второй раз проверяем, чтобы предотвратить проблемы с состоянием гонки в многопоточной среде.
                if (currentTime - RequestStartTime >= RequestUnit) {
                    counter.set(0);
                    RequestStartTime = currentTime;
                }
            }

            // Увеличиваем значение счетчика и проверяем, не превышает ли оно пороговое значение.
            // Если счетчик находится в пределах порогового значения, предоставьте доступ; в противном случае отклоните запрос.
            return counter.incrementAndGet() <= threshold;
        }
    }

    /**
     * Класс для представления структуры документа.
     */
@JsonNaming(value = PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Document {
        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }


        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public ArrayList<Product> products;
        public String reg_date;
        public String reg_number;

    }

}
