package com.platform.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.inventoryservice.event.inbound.ReleaseInventoryCommand;
import com.platform.inventoryservice.event.inbound.ReserveInventoryCommand;
import com.platform.inventoryservice.event.outbound.InventoryReleasedEvent;
import com.platform.inventoryservice.event.outbound.InventoryReleaseFailedEvent;
import com.platform.inventoryservice.event.outbound.InventoryReservationFailedEvent;
import com.platform.inventoryservice.event.outbound.InventoryReservedEvent;
import com.platform.inventoryservice.exception.InventoryItemNotFoundException;
import com.platform.inventoryservice.messaging.producer.KafkaProducerService;
import com.platform.inventoryservice.model.InventoryItem;
import com.platform.inventoryservice.model.Reservation;
import com.platform.inventoryservice.model.ReservationStatus;
import com.platform.inventoryservice.repository.InventoryRepository;
import com.platform.inventoryservice.repository.ReservationRepository;
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
  @Mock private ReservationRepository reservationRepository;
  @Mock private KafkaProducerService kafkaProducer;
  @InjectMocks private InventoryService inventoryService;

  // ── reserveInventory ────────────────────────────────────────────────────────

  @Test
  void reserveInventory_publishesReservedEvent_whenStockIsSufficient() {
    ReserveInventoryCommand command = new ReserveInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(3);

    when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
    InventoryItem item = InventoryItem.builder().productId("prod-1").availableQuantity(10).build();
    when(inventoryRepository.findByProductId("prod-1")).thenReturn(Optional.of(item));
    when(inventoryRepository.save(any())).thenReturn(item);
    when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

    inventoryService.reserveInventory(command);

    assertThat(item.getAvailableQuantity()).isEqualTo(7);
    verify(inventoryRepository).save(item);

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    verify(reservationRepository).save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getOrderId()).isEqualTo("order-1");
    assertThat(reservationCaptor.getValue().getQuantity()).isEqualTo(3);
    assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.ACTIVE);

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

    when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
    InventoryItem item = InventoryItem.builder().productId("prod-1").availableQuantity(2).build();
    when(inventoryRepository.findByProductId("prod-1")).thenReturn(Optional.of(item));

    inventoryService.reserveInventory(command);

    verify(inventoryRepository, never()).save(any());
    verify(reservationRepository, never()).save(any());

    ArgumentCaptor<InventoryReservationFailedEvent> captor =
        ArgumentCaptor.forClass(InventoryReservationFailedEvent.class);
    verify(kafkaProducer).send(eq("order.inventory.failed"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
    assertThat(captor.getValue().getReason()).contains("Insufficient stock");
  }

  @Test
  void reserveInventory_publishesFailedEvent_whenProductNotFound() {
    ReserveInventoryCommand command = new ReserveInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("unknown");
    command.setQuantity(1);

    when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
    when(inventoryRepository.findByProductId("unknown")).thenReturn(Optional.empty());

    inventoryService.reserveInventory(command);

    verify(inventoryRepository, never()).save(any());
    verify(reservationRepository, never()).save(any());

    ArgumentCaptor<InventoryReservationFailedEvent> captor =
        ArgumentCaptor.forClass(InventoryReservationFailedEvent.class);
    verify(kafkaProducer).send(eq("order.inventory.failed"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getReason()).contains("Product not found");
  }

  @Test
  void reserveInventory_skipsDuplicate_whenReservationAlreadyExists() {
    ReserveInventoryCommand command = new ReserveInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(3);

    when(reservationRepository.findByOrderId("order-1"))
        .thenReturn(Optional.of(reservationFor("order-1", "prod-1", 3, ReservationStatus.ACTIVE)));

    inventoryService.reserveInventory(command);

    verify(inventoryRepository, never()).findByProductId(any());
    verify(inventoryRepository, never()).save(any());
    verify(reservationRepository, never()).save(any());
    verify(kafkaProducer, never()).send(any(), any(), any());
  }

  // ── releaseInventory ────────────────────────────────────────────────────────

  @Test
  void releaseInventory_restoresStockAndPublishesReleasedEvent() {
    ReleaseInventoryCommand command = new ReleaseInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(3);

    Reservation reservation = reservationFor("order-1", "prod-1", 3, ReservationStatus.ACTIVE);
    when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.of(reservation));

    InventoryItem item = InventoryItem.builder().productId("prod-1").availableQuantity(7).build();
    when(inventoryRepository.findByProductId("prod-1")).thenReturn(Optional.of(item));
    when(inventoryRepository.save(any())).thenReturn(item);
    when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

    inventoryService.releaseInventory(command);

    assertThat(item.getAvailableQuantity()).isEqualTo(10);
    verify(inventoryRepository).save(item);

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    verify(reservationRepository).save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.RELEASED);

    ArgumentCaptor<InventoryReleasedEvent> captor =
        ArgumentCaptor.forClass(InventoryReleasedEvent.class);
    verify(kafkaProducer).send(eq("order.inventory.released"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
    assertThat(captor.getValue().getProductId()).isEqualTo("prod-1");
  }

  @Test
  void releaseInventory_usesQuantityFromReservation_notFromCommand() {
    ReleaseInventoryCommand command = new ReleaseInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(99); // stale quantity in redelivered command — should be ignored

    Reservation reservation = reservationFor("order-1", "prod-1", 3, ReservationStatus.ACTIVE);
    when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.of(reservation));

    InventoryItem item = InventoryItem.builder().productId("prod-1").availableQuantity(7).build();
    when(inventoryRepository.findByProductId("prod-1")).thenReturn(Optional.of(item));
    when(inventoryRepository.save(any())).thenReturn(item);

    inventoryService.releaseInventory(command);

    assertThat(item.getAvailableQuantity()).isEqualTo(10); // 7 + 3 from reservation, not 99
  }

  @Test
  void releaseInventory_publishesReleaseFailedEvent_whenProductNotFound() {
    ReleaseInventoryCommand command = new ReleaseInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("unknown");
    command.setQuantity(1);

    Reservation reservation = reservationFor("order-1", "unknown", 1, ReservationStatus.ACTIVE);
    when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
    when(inventoryRepository.findByProductId("unknown")).thenReturn(Optional.empty());
    when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

    inventoryService.releaseInventory(command);

    verify(inventoryRepository, never()).save(any());

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    verify(reservationRepository).save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.RELEASE_FAILED);

    ArgumentCaptor<InventoryReleaseFailedEvent> captor =
        ArgumentCaptor.forClass(InventoryReleaseFailedEvent.class);
    verify(kafkaProducer)
        .send(eq("order.inventory.release.failed"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getReason()).contains("Product not found");
  }

  @Test
  void releaseInventory_treatsAsSuccessAndPublishesReleasedEvent_whenNoReservationFound() {
    ReleaseInventoryCommand command = new ReleaseInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(3);

    when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

    inventoryService.releaseInventory(command);

    verify(inventoryRepository, never()).save(any());
    verify(reservationRepository, never()).save(any());

    ArgumentCaptor<InventoryReleasedEvent> captor =
        ArgumentCaptor.forClass(InventoryReleasedEvent.class);
    verify(kafkaProducer).send(eq("order.inventory.released"), eq("order-1"), captor.capture());
    assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
  }

  @Test
  void releaseInventory_skipsDuplicate_whenReservationAlreadyReleased() {
    ReleaseInventoryCommand command = new ReleaseInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(3);

    when(reservationRepository.findByOrderId("order-1"))
        .thenReturn(Optional.of(reservationFor("order-1", "prod-1", 3, ReservationStatus.RELEASED)));

    inventoryService.releaseInventory(command);

    verify(inventoryRepository, never()).findByProductId(any());
    verify(inventoryRepository, never()).save(any());
    verify(reservationRepository, never()).save(any());
    verify(kafkaProducer, never()).send(any(), any(), any());
  }

  // ── getByProductId ──────────────────────────────────────────────────────────

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

  // ── helpers ─────────────────────────────────────────────────────────────────

  @Test
  void releaseInventory_skipsDuplicate_whenReservationStatusIsReleaseFailed() {
    ReleaseInventoryCommand command = new ReleaseInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(3);

    when(reservationRepository.findByOrderId("order-1"))
        .thenReturn(
            Optional.of(reservationFor("order-1", "prod-1", 3, ReservationStatus.RELEASE_FAILED)));

    inventoryService.releaseInventory(command);

    verify(inventoryRepository, never()).findByProductId(any());
    verify(inventoryRepository, never()).save(any());
    verify(reservationRepository, never()).save(any());
    verify(kafkaProducer, never()).send(any(), any(), any());
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private Reservation reservationFor(
      String orderId, String productId, int quantity, ReservationStatus status) {
    return Reservation.builder()
        .orderId(orderId)
        .productId(productId)
        .quantity(quantity)
        .status(status)
        .build();
  }
}
