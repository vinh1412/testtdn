/*
 * @ {#} RabbitMQConfig.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.warehouse_service.configs;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * @description: Cấu hình RabbitMQ cho warehouse-service
 * @author: Tran Hien Vinh
 * @date:   12/11/2025
 * @version:    1.0
 */
@Configuration
public class RabbitMQConfig {
    // Định nghĩa tên Exchange chung cho nghiệp vụ instrument
    public static final String INSTRUMENT_EXCHANGE = "instrument_exchange";

    // Định nghĩa Routing Key cho sự kiện "activated"
    public static final String INSTRUMENT_ACTIVATED_ROUTING_KEY = "instrument.activated";

    // Định nghĩa Queue mà instrument-service sẽ lắng nghe
    public static final String INSTRUMENT_ACTIVATED_QUEUE = "q.instrument_activated";

    // Định nghĩa Routing Key cho sự kiện "deactivated"
    public static final String INSTRUMENT_DEACTIVATED_ROUTING_KEY = "instrument.deactivated";

    // Định nghĩa Queue mà instrument-service sẽ lắng nghe
    public static final String INSTRUMENT_DEACTIVATED_QUEUE = "q.instrument_deactivated";

    @Bean
    public TopicExchange instrumentExchange() {
        return new TopicExchange(INSTRUMENT_EXCHANGE);
    }

    // Bean này tạo ra Queue (hàng đợi)
    @Bean
    public Queue instrumentActivatedQueue() {
        return new Queue(INSTRUMENT_ACTIVATED_QUEUE, true);
    }

    // Bean này liên kết (bind) Queue với Exchange thông qua Routing Key
    @Bean
    public Binding instrumentActivatedBinding(Queue instrumentActivatedQueue, TopicExchange instrumentExchange) {
        return BindingBuilder
                .bind(instrumentActivatedQueue)
                .to(instrumentExchange)
                .with(INSTRUMENT_ACTIVATED_ROUTING_KEY);
    }

    // Bean này tạo ra Queue (hàng đợi)
    @Bean
    public Queue instrumentDeactivatedQueue() {
        return new Queue(INSTRUMENT_DEACTIVATED_QUEUE, true);
    }

    // Bean này liên kết (bind) Queue với Exchange thông qua Routing Key
    @Bean
    public Binding instrumentDeactivatedBinding(Queue instrumentDeactivatedQueue, TopicExchange instrumentExchange) {
        return BindingBuilder
                .bind(instrumentDeactivatedQueue)
                .to(instrumentExchange)
                .with(INSTRUMENT_DEACTIVATED_ROUTING_KEY);
    }

    // Bean này cấu hình Message Converter sử dụng Jackson để chuyển đổi JSON
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
