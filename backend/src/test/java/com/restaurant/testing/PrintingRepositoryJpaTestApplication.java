package com.restaurant.testing;

import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.PrintJobRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = PrintJob.class)
@EnableJpaRepositories(basePackageClasses = PrintJobRepository.class)
public class PrintingRepositoryJpaTestApplication {
}
