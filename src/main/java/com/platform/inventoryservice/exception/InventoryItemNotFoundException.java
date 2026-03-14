package com.platform.inventoryservice.exception;

public class InventoryItemNotFoundException extends RuntimeException {

  public InventoryItemNotFoundException(String productId) {
    super("Inventory item not found for productId: " + productId);
  }
}
