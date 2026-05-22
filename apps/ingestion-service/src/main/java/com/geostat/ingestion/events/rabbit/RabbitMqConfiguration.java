package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.config.IngestionProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableRabbit
@Profile("db")
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@ConditionalOnProperty(prefix = "geostat.ingestion.events", name = "enabled", havingValue = "true")
public class RabbitMqConfiguration {

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
        return factory;
    }

    @Bean
    Declarables documentIndexTopology(IngestionProperties properties) {
        IngestionProperties.Events events = properties.events();
        TopicExchange exchange = new TopicExchange(events.exchange(), true, false);
        Queue queue = new Queue(events.indexQueue(), true);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(events.routingKey());
        return new Declarables(exchange, queue, binding);
    }
}
