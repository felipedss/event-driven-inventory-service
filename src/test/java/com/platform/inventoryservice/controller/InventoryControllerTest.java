package com.platform.inventoryservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.inventoryservice.exception.GlobalExceptionHandler;
import com.platform.inventoryservice.exception.InventoryItemNotFoundException;
import com.platform.inventoryservice.model.InventoryItem;
import com.platform.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

  @Mock private InventoryService inventoryService;
  @InjectMocks private InventoryController inventoryController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(inventoryController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getStock_returns200_whenFound() throws Exception {
    InventoryItem item =
        InventoryItem.builder().itemId("item-1").productId("prod-1").availableQuantity(10).build();
    when(inventoryService.getByProductId("prod-1")).thenReturn(item);

    mockMvc
        .perform(get("/api/v1/inventory/prod-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value("prod-1"))
        .andExpect(jsonPath("$.availableQuantity").value(10));
  }

  @Test
  void getStock_returns404_whenNotFound() throws Exception {
    when(inventoryService.getByProductId("unknown"))
        .thenThrow(new InventoryItemNotFoundException("unknown"));

    mockMvc.perform(get("/api/v1/inventory/unknown")).andExpect(status().isNotFound());
  }
}
