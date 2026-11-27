package com.richcomes.garage.customer.repository;

import com.richcomes.garage.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository
        extends JpaRepository<Customer, Long> {

    // Serialize contact updates with account registration even when no account exists yet.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Customer c where c.id = :id")
    Optional<Customer> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Customer> findByNic(String nic);

    boolean existsByNic(String nic);

    List<Customer> findByNameContainingIgnoreCase(String name);
}