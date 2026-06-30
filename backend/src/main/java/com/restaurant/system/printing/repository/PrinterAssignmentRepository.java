package com.restaurant.system.printing.repository;

import com.restaurant.system.printing.entity.PrinterAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrinterAssignmentRepository extends JpaRepository<PrinterAssignment, Long> {

    @Query("""
        select pa from PrinterAssignment pa
        where pa.store_id = :storeId
        order by pa.id asc
        """)
    List<PrinterAssignment> findAllByStoreIdOrderByIdAsc(@Param("storeId") Long storeId);

    @Query("""
        select pa from PrinterAssignment pa
        where pa.store_id = :storeId and pa.module_code = :moduleCode
        """)
    Optional<PrinterAssignment> findByStoreIdAndModuleCode(@Param("storeId") Long storeId, @Param("moduleCode") String moduleCode);

    @Query("""
        select count(pa) from PrinterAssignment pa
        where pa.store_id = :storeId and pa.printer_id = :printerId
        """)
    long countByStoreIdAndPrinterId(@Param("storeId") Long storeId, @Param("printerId") Long printerId);
}
