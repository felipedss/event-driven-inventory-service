package com.platform.inventoryservice.service;

import com.platform.inventoryservice.event.inbound.ReserveInventoryCommand;
import com.platform.inventoryservice.event.outbound.InventoryReservationFailedEvent;
import com.platform.inventoryservice.event.outbound.InventoryReservedEvent;
import com.platform.inventoryservice.exception.InventoryItemNotFoundException;
import com.platform.inventoryservice.messaging.producer.KafkaProducerService;
import com.platform.inventoryservice.model.InventoryItem;
import com.platform.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

  private final InventoryRepository inventoryRepository;
  private final KafkaProducerService kafkaProducer;

  public void reserveInventory(ReserveInventoryCommand command) {
    InventoryItem item = inventoryRepository.findByProductId(command.getProductId()).orElse(null);

    if (item == null) {
      log.warn("Product not found: productId={}", command.getProductId());
      kafkaProducer.send(
          "order.inventory.failed",
          command.getOrderId(),
          new InventoryReservationFailedEvent(
              command.getOrderId(),
                  command.getProductId(),
                  "Product not found: " + command.getProductId()));
      return;
    }

    if (item.getAvailableQuantity() < command.getQuantity()) {
      log.warn(
          "Insufficient stock for productId={}: available={}, requested={}",
          command.getProductId(),
          item.getAvailableQuantity(),
          command.getQuantity());
      kafkaProducer.send(
          "order.inventory.failed",
          command.getOrderId(),
          new InventoryReservationFailedEvent(
              command.getOrderId(),
                  command.getProductId(),
                  "Insufficient stock for product: " + command.getProductId()));
      return;
    }

    item.setAvailableQuantity(item.getAvailableQuantity() - command.getQuantity());
    inventoryRepository.save(item);
    log.info(
        "Inventory reserved for orderId={}, productId={}, quantity={}",
        command.getOrderId(),
        command.getProductId(),
        command.getQuantity());

    kafkaProducer.send(
        "order.inventory.reserved",
        command.getOrderId(),
        new InventoryReservedEvent(
            command.getOrderId(), command.getProductId(), command.getQuantity()));
  }

  public InventoryItem getByProductId(String productId) {
    return inventoryRepository
        .findByProductId(productId)
        .orElseThrow(() -> new InventoryItemNotFoundException(productId));
  }

  public InventoryItem save(InventoryItem item) {
    return inventoryRepository.save(item);
  }
}
