package com.platform.inventoryservice.messaging.consumer;

import com.platform.inventoryservice.event.inbound.ReserveInventoryCommand;
import com.platform.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCommandConsumer {

  private final InventoryService inventoryService;

  @KafkaListener(topics = "order.inventory.reserve", groupId = "${spring.kafka.consumer.group-id}")
  public void handleReserveInventory(ReserveInventoryCommand command) {
    log.info(
        "Received ReserveInventoryCommand for orderId={}, productId={}, quantity={}",
        command.getOrderId(),
        command.getProductId(),
        command.getQuantity());
    inventoryService.reserveInventory(command);
  }
}
