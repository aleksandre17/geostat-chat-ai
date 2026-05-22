package worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
public class HealthController {

    private final RestClient apiClient;

    public HealthController(@Value("${API_INTERNAL_URL:http://localhost:8090}") String apiBase) {
        this.apiClient = RestClient.builder().baseUrl(apiBase).build();
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        String apiStatus = "unknown";
        try {
            var body = apiClient.get().uri("/health").retrieve().body(String.class);
            apiStatus = body != null && body.contains("UP") ? "reachable" : "unexpected";
        } catch (Exception e) {
            apiStatus = "unreachable: " + e.getClass().getSimpleName();
        }
        return Map.of(
                "status", "UP",
                "service", "worker",
                "api", apiStatus
        );
    }
}
