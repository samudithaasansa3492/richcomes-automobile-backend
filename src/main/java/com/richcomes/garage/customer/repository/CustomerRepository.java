package com.richcomes.garage.customer.repository;

import com.richcomes.garage.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository
        extends JpaRepository<Customer, Long> {

    Optional<Customer> findByNic(String nic);

    boolean existsByNic(String nic);

    List<Customer> findByNameContainingIgnoreCase(String name);
}