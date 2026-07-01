package com.restaurant.system.printing.repository;

import com.restaurant.system.printing.entity.StoreDevice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreDeviceRepository extends JpaRepository<StoreDevice, Long> {

    List<StoreDevice> findAllByStoreIdOrderByIdAsc(Long storeId);
}
