package com.platform.inventoryservice.repository;

import com.platform.inventoryservice.model.InventoryItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

  Optional<InventoryItem> findByProductId(String productId);
}
