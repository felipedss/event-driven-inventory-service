package com.platform.inventoryservice.event.outbound;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryReservationFailedEvent {

  private String orderId;
  private String productId;
  private String reason;
}
