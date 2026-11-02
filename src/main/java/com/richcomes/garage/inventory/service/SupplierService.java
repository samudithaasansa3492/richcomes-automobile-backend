
package com.richcomes.garage.inventory.service;

import com.richcomes.garage.inventory.dto.request.SupplierRequest;
import com.richcomes.garage.inventory.dto.response.SupplierResponse;
import com.richcomes.garage.inventory.entity.Supplier;
import com.richcomes.garage.inventory.repository.SupplierRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    // CREATE SUPPLIER
    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {

        String name = request.getSupplierName().trim();
        String tin = normalizeTin(request.getTinNumber());

        if (tin != null && repository.existsByTinNumber(tin)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Supplier TIN already exists"
            );
        }

        Supplier supplier = new Supplier();
        supplier.setSupplierName(name);
        supplier.setTinNumber(tin);
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());

        return mapToResponse(repository.save(supplier));
    }

    // GET ALL SUPPLIERS
    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliers() {
        return repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET SUPPLIER BY ID
    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        return mapToResponse(findSupplier(id));
    }

    // UPDATE SUPPLIER
    @Transactional
    public SupplierResponse updateSupplier(
            Long id, SupplierRequest request) {

        Supplier supplier = findSupplier(id);

        String name = request.getSupplierName().trim();
        String tin = normalizeTin(request.getTinNumber());

        if (tin != null) {
            repository.findByTinNumber(tin)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Supplier TIN already exists"
                            );
                        }
                    });
        }

        supplier.setSupplierName(name);
        supplier.setTinNumber(tin);
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());

        return mapToResponse(repository.save(supplier));
    }

    // DEACTIVATE SUPPLIER
    @Transactional
    public void deactivateSupplier(Long id) {
        Supplier supplier = findSupplier(id);
        supplier.setActive(false);
        repository.save(supplier);
    }

    // ACTIVATE SUPPLIER
    @Transactional
    public void activateSupplier(Long id) {
        Supplier supplier = findSupplier(id);
        supplier.setActive(true);
        repository.save(supplier);
    }

    // FIND SUPPLIER
    private Supplier findSupplier(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Supplier not found"
                        )
                );
    }

    // NORMALIZE OPTIONAL TIN
    private String normalizeTin(String tin) {
        if (tin == null || tin.isBlank()) {
            return null;
        }
        return tin.trim();
    }

    // ENTITY -> RESPONSE DTO
    private SupplierResponse mapToResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierName(),
                supplier.getTinNumber(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.isActive()
        );
    }
}
