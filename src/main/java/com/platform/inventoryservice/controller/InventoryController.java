package com.platform.inventoryservice.controller;

import com.platform.inventoryservice.model.InventoryItem;
import com.platform.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

  private final InventoryService inventoryService;

  @GetMapping("/{productId}")
  public ResponseEntity<InventoryItem> getStock(@PathVariable String productId) {
    return ResponseEntity.ok(inventoryService.getByProductId(productId));
  }

  @PostMapping
  public ResponseEntity<InventoryItem> addStock(@RequestBody InventoryItem item) {
    return ResponseEntity.ok(inventoryService.save(item));
  }
}
