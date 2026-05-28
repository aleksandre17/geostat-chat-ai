package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.config.IngestionProperties;
import java.util.Map;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableRabbit
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.events", name = "enabled", havingValue = "true")
public class RabbitMqConfiguration {

    @Bean
    ConnectionFactory connectionFactory(
            @Value("${spring.rabbitmq.host}") String host,
            @Value("${spring.rabbitmq.port}") int port,
            @Value("${spring.rabbitmq.username}") String username,
            @Value("${spring.rabbitmq.password}") String password) {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);

        // Manual ack: message not acked until listener returns successfully
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        // Max 5 unacked messages per consumer — prevents memory spike during backfill
        factory.setPrefetchCount(5);

        // Retry: 3 attempts, exponential backoff 1s → 2s → 4s, then → DLQ
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxRetries(3)
                        .backOffOptions(1_000L, 2.0, 8_000L)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build());

        return factory;
    }

    @Bean
    Declarables documentIndexTopology(IngestionProperties properties) {
        IngestionProperties.Events events = properties.events();

        // Dead-letter exchange (shared for all ingestion queues)
        DirectExchange dlx = new DirectExchange("geostat.ingestion.dlx", true, false);

        // Index queue + DLQ
        Map<String, Object> indexArgs = Map.of(
                "x-dead-letter-exchange", "geostat.ingestion.dlx",
                "x-dead-letter-routing-key", "dead.index",
                "x-message-ttl", 86_400_000);
        TopicExchange exchange = new TopicExchange(events.exchange(), true, false);
        Queue indexQueue = new Queue(events.indexQueue(), true, false, false, indexArgs);
        Queue indexDlq = new Queue(events.indexQueue() + ".dlq", true);
        Binding indexBinding = BindingBuilder.bind(indexQueue).to(exchange).with(events.routingKey());
        Binding indexDlqBinding = BindingBuilder.bind(indexDlq).to(dlx).with("dead.index");

        // Enrichment queue + DLQ
        Map<String, Object> enrichArgs = Map.of(
                "x-dead-letter-exchange", "geostat.ingestion.dlx",
                "x-dead-letter-routing-key", "dead.enrichment",
                "x-message-ttl", 86_400_000);
        Queue enrichmentQueue = new Queue(events.enrichmentQueue(), true, false, false, enrichArgs);
        Queue enrichmentDlq = new Queue(events.enrichmentQueue() + ".dlq", true);
        Binding enrichmentBinding =
                BindingBuilder.bind(enrichmentQueue).to(exchange).with(events.enrichmentRoutingKey());
        Binding enrichmentDlqBinding = BindingBuilder.bind(enrichmentDlq).to(dlx).with("dead.enrichment");

        // Feedback score queue + DLQ (Phase R-C)
        Map<String, Object> feedbackArgs = Map.of(
                "x-dead-letter-exchange", "geostat.ingestion.dlx",
                "x-dead-letter-routing-key", "dead.feedback-score",
                "x-message-ttl", 86_400_000);
        Queue feedbackQueue = new Queue("geostat.ingestion.feedback-score", true, false, false, feedbackArgs);
        Queue feedbackDlq = new Queue("geostat.ingestion.feedback-score.dlq", true);
        Binding feedbackBinding = BindingBuilder.bind(feedbackQueue).to(exchange).with("feedback.score");
        Binding feedbackDlqBinding = BindingBuilder.bind(feedbackDlq).to(dlx).with("dead.feedback-score");

        return new Declarables(
                exchange,
                dlx,
                indexQueue,
                indexDlq,
                indexBinding,
                indexDlqBinding,
                enrichmentQueue,
                enrichmentDlq,
                enrichmentBinding,
                enrichmentDlqBinding,
                feedbackQueue,
                feedbackDlq,
                feedbackBinding,
                feedbackDlqBinding);
    }
}
