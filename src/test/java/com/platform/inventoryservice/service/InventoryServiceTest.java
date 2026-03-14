package com.platform.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.inventoryservice.event.inbound.ReserveInventoryCommand;
import com.platform.inventoryservice.event.outbound.InventoryReservationFailedEvent;
import com.platform.inventoryservice.event.outbound.InventoryReservedEvent;
import com.platform.inventoryservice.exception.InventoryItemNotFoundException;
import com.platform.inventoryservice.messaging.producer.KafkaProducerService;
import com.platform.inventoryservice.model.InventoryItem;
import com.platform.inventoryservice.repository.InventoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private KafkaProducerService kafkaProducer;
  @InjectMocks private InventoryService inventoryService;

  @Test
  void reserveInventory_publishesReservedEvent_whenStockIsSufficient() {
    ReserveInventoryCommand command = new ReserveInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(3);

    InventoryItem item = InventoryItem.builder().productId("prod-1").availableQuantity(10).build();
    when(inventoryRepository.findByProductId("prod-1")).thenReturn(Optional.of(item));
    when(inventoryRepository.save(any())).thenReturn(item);

    inventoryService.reserveInventory(command);

    assertThat(item.getAvailableQuantity()).isEqualTo(7);
    verify(inventoryRepository).save(item);

    ArgumentCaptor<InventoryReservedEvent> captor =
        ArgumentCaptor.forClass(InventoryReservedEvent.class);
    verify(kafkaProducer).send(eq("order.inventory.reserved"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
    assertThat(captor.getValue().getQuantity()).isEqualTo(3);
  }

  @Test
  void reserveInventory_publishesFailedEvent_whenStockIsInsufficient() {
    ReserveInventoryCommand command = new ReserveInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(10);

    InventoryItem item = InventoryItem.builder().productId("prod-1").availableQuantity(2).build();
    when(inventoryRepository.findByProductId("prod-1")).thenReturn(Optional.of(item));

    inventoryService.reserveInventory(command);

    verify(inventoryRepository, never()).save(any());

    ArgumentCaptor<InventoryReservationFailedEvent> captor =
        ArgumentCaptor.forClass(InventoryReservationFailedEvent.class);
    verify(kafkaProducer)
        .send(eq("order.inventory.reservation-failed"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
    assertThat(captor.getValue().getReason()).contains("Insufficient stock");
  }

  @Test
  void reserveInventory_publishesFailedEvent_whenProductNotFound() {
    ReserveInventoryCommand command = new ReserveInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("unknown");
    command.setQuantity(1);

    when(inventoryRepository.findByProductId("unknown")).thenReturn(Optional.empty());

    inventoryService.reserveInventory(command);

    verify(inventoryRepository, never()).save(any());

    ArgumentCaptor<InventoryReservationFailedEvent> captor =
        ArgumentCaptor.forClass(InventoryReservationFailedEvent.class);
    verify(kafkaProducer)
        .send(eq("order.inventory.reservation-failed"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getReason()).contains("Product not found");
  }

  @Test
  void getByProductId_returnsItem_whenFound() {
    InventoryItem item = InventoryItem.builder().productId("prod-1").availableQuantity(5).build();
    when(inventoryRepository.findByProductId("prod-1")).thenReturn(Optional.of(item));

    assertThat(inventoryService.getByProductId("prod-1")).isEqualTo(item);
  }

  @Test
  void getByProductId_throwsException_whenNotFound() {
    when(inventoryRepository.findByProductId("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> inventoryService.getByProductId("missing"))
        .isInstanceOf(InventoryItemNotFoundException.class)
        .hasMessageContaining("missing");
  }
}
