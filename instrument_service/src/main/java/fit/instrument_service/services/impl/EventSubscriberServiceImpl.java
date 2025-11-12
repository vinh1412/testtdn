/*
 * @ {#} EventSubscriberServiceImpl.java   1.0     12/11/2025
 *
 * Copyright (c) 2025 IUH. All rights reserved.
 */

package fit.instrument_service.services.impl;

import fit.instrument_service.configs.RabbitMQConfig;
import fit.instrument_service.events.InstrumentActivatedEvent;
import fit.instrument_service.events.InstrumentDeactivatedEvent;
import fit.instrument_service.services.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/*
 * @description: Implementation of EventSubscriberService for handling instrument-related events.
 * @author: Tran Hien Vinh
 * @date:   12/11/2025
 * @version:    1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventSubscriberServiceImpl {
    private final InstrumentService instrumentService;

    @RabbitListener(queues = RabbitMQConfig.INSTRUMENT_ACTIVATED_QUEUE)
    public void handleInstrumentActivated(InstrumentActivatedEvent event) {
        try {
            log.info("Received event from queue [{}]: {}", RabbitMQConfig.INSTRUMENT_ACTIVATED_QUEUE, event.getId());
            instrumentService.handleInstrumentActivation(event);
        } catch (Exception e) {
            log.error("Error processing message from queue [{}]. Error: {}",
                    RabbitMQConfig.INSTRUMENT_ACTIVATED_QUEUE, e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.INSTRUMENT_DEACTIVATED_QUEUE)
    public void handleInstrumentDeactivated(InstrumentDeactivatedEvent event) {
        try {
            log.info("Received event from queue [{}]: {}", RabbitMQConfig.INSTRUMENT_DEACTIVATED_QUEUE, event.getId());
            instrumentService.handleInstrumentDeactivated(event);
        } catch (Exception e) {
            log.error("Error processing message from queue [{}]. Error: {}",
                    RabbitMQConfig.INSTRUMENT_DEACTIVATED_QUEUE, e.getMessage());
        }
    }
}
