package Chatbot.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievalPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(RetrievalClientProperties.class)
public class RetrievalClientConfig {

    @Bean
    RetrievalPort retrievalPort(RetrievalClientProperties properties, WebClient.Builder webClientBuilder) {
        if (!properties.enabled()) {
            return disabledRetrievalPort();
        }
        WebClient client = webClientBuilder.baseUrl(properties.baseUrl()).build();
        return new HttpRetrievalClient(client);
    }

    private static RetrievalPort disabledRetrievalPort() {
        return new RetrievalPort() {
            @Override
            public List<RetrievedChunk> search(RetrievalQuery query) {
                return List.of();
            }
        };
    }
}
