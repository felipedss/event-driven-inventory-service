package com.platform.inventoryservice.messaging.consumer;

import static org.mockito.Mockito.verify;

import com.platform.inventoryservice.event.inbound.ReserveInventoryCommand;
import com.platform.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class InventoryCommandConsumerTest {

  @Mock private InventoryService inventoryService;
  @Mock private Acknowledgment ack;
  @InjectMocks private InventoryCommandConsumer consumer;

  @Test
  void handleReserveInventory_delegatesToInventoryService() {
    ReserveInventoryCommand command = new ReserveInventoryCommand();
    command.setOrderId("order-1");
    command.setProductId("prod-1");
    command.setQuantity(2);

    consumer.handleReserveInventory(command, ack);

    verify(inventoryService).reserveInventory(command);
    verify(ack).acknowledge();
  }
}
