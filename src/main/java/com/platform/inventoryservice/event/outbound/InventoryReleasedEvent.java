package com.platform.inventoryservice.event.outbound;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryReleasedEvent {

  private String orderId;
  private String productId;
}
