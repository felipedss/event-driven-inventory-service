package com.platform.inventoryservice.event.outbound;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryReservedEvent {

  private String orderId;
  private String productId;
  private int quantity;
}
