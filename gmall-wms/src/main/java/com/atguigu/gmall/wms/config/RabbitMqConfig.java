package com.atguigu.gmall.wms.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Bean("STOCK-TTL-QUEUE")
    public Queue ttlQueue() {
        Map<String, Object> map = new HashMap<>();
        map.put("x-dead-letter-exchange", "GMALL_ORDER_EXCHANGE");
        map.put("x-dead-letter-routing-key", "stock.unlock");
        map.put("x-message-ttl", 120000); // 2分钟

        return new Queue("STOCK-TTL-QUEUE",true, false, false, map);
    }

    @Bean("STOCK-TTL-BINDING")
    public Binding ttlBinding() {
        return new Binding("STOCK-TTL-QUEUE", Binding.DestinationType.QUEUE, "GMALL_ORDER_EXCHANGE", "stock.ttl", null);
    }
}
