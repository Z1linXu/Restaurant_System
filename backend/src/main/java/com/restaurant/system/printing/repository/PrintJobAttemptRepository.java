package com.restaurant.system.printing.repository;

import com.restaurant.system.printing.entity.PrintJobAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrintJobAttemptRepository extends JpaRepository<PrintJobAttempt, Long> {

    @Query("""
        select pja from PrintJobAttempt pja
        where pja.print_job_id = :printJobId
        order by pja.attempt_number asc, pja.id asc
        """)
    List<PrintJobAttempt> findAllByPrintJobId(@Param("printJobId") Long printJobId);

    @Query("""
        select count(pja) from PrintJobAttempt pja
        where pja.print_job_id = :printJobId
        """)
    long countByPrintJobId(@Param("printJobId") Long printJobId);
}
