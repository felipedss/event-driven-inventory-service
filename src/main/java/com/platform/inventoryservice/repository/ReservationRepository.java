package com.platform.inventoryservice.repository;

import com.platform.inventoryservice.model.Reservation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
  Optional<Reservation> findByOrderId(String orderId);
}
