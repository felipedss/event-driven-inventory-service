package com.platform.inventoryservice.event.inbound;

import lombok.Data;

@Data
public class ReleaseInventoryCommand {
  private String orderId;
  private String productId;
  private int quantity;
}
