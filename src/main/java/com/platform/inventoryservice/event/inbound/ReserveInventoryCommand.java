package com.platform.inventoryservice.event.inbound;

import lombok.Data;

@Data
public class ReserveInventoryCommand {
  private String orderId;
  private String productId;
  private int quantity;
}
