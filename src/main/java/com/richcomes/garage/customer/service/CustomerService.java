
package com.richcomes.garage.customer.service;

import com.richcomes.garage.customer.dto.CustomerCreateRequest;
import com.richcomes.garage.customer.dto.CustomerResponse;
import com.richcomes.garage.customer.dto.CustomerUpdateRequest;
import com.richcomes.garage.customer.entity.Customer;
import com.richcomes.garage.customer.enums.CustomerStatus;
import com.richcomes.garage.customer.enums.CustomerType;
import com.richcomes.garage.customer.repository.CustomerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse createCustomer(CustomerCreateRequest request) {

        if (request == null || request.getCustomerType() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer type is required"
            );
        }

        Customer customer = new Customer();

        customer.setCustomerType(request.getCustomerType());
        customer.setName(request.getName().trim());
        customer.setAddress(request.getAddress().trim());
        customer.setMobile(request.getMobile().trim());
        customer.setOfficeNumber(normalizeOptional(request.getOfficeNumber()));
        customer.setEmail(normalizeOptional(request.getEmail()));
        customer.setCity(request.getCity().trim());
        customer.setVatNumber(normalizeOptional(request.getVatNumber()));
        customer.setCreditLimit(request.getCreditLimit());
        customer.setStatus(CustomerStatus.ACTIVE);

        if (request.getCustomerType() == CustomerType.PERSONAL) {

            if (request.getNic() == null || request.getNic().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "NIC is required for personal customers"
                );
            }

            String normalizedNic = request.getNic()
                    .trim()
                    .toUpperCase(Locale.ROOT);

            if (customerRepository.existsByNic(normalizedNic)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Customer with this NIC already exists"
                );
            }

            customer.setNic(normalizedNic);

        } else if (request.getCustomerType() == CustomerType.COMPANY) {

            customer.setNic(null);
        }

        Customer savedCustomer = customerRepository.save(customer);

        return mapToResponse(savedCustomer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {

        return customerRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {

        Customer customer = findCustomerById(id);

        return mapToResponse(customer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomerEntityById(Long id) {

        return findCustomerById(id);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByNic(String nic) {

        if (nic == null || nic.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "NIC is required"
            );
        }

        String normalizedNic = nic
                .trim()
                .toUpperCase(Locale.ROOT);

        Customer customer = customerRepository
                .findByNic(normalizedNic)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );

        return mapToResponse(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomersByName(String name) {

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer name is required"
            );
        }

        return customerRepository
                .findByNameContainingIgnoreCase(name.trim())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public CustomerResponse updateCustomer(
            Long id,
            CustomerUpdateRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer update data is required"
            );
        }

        Customer customer = findCustomerById(id);

        if (request.getName() != null) {

            if (request.getName().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Name cannot be empty"
                );
            }

            customer.setName(request.getName().trim());
        }

        if (request.getAddress() != null) {

            if (request.getAddress().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Address cannot be empty"
                );
            }

            customer.setAddress(request.getAddress().trim());
        }

        if (request.getMobile() != null) {

            if (request.getMobile().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Mobile number cannot be empty"
                );
            }

            customer.setMobile(request.getMobile().trim());
        }

        if (request.getOfficeNumber() != null) {
            customer.setOfficeNumber(
                    normalizeOptional(request.getOfficeNumber())
            );
        }

        if (request.getEmail() != null) {
            customer.setEmail(
                    normalizeOptional(request.getEmail())
            );
        }

        if (request.getCity() != null) {

            if (request.getCity().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "City cannot be empty"
                );
            }

            customer.setCity(request.getCity().trim());
        }

        if (request.getVatNumber() != null) {
            customer.setVatNumber(
                    normalizeOptional(request.getVatNumber())
            );
        }

        if (request.getCreditLimit() != null) {
            customer.setCreditLimit(request.getCreditLimit());
        }

        Customer updatedCustomer = customerRepository.save(customer);

        return mapToResponse(updatedCustomer);
    }

    public CustomerResponse suspendCustomer(Long id) {

        Customer customer = findCustomerById(id);

        customer.setStatus(CustomerStatus.SUSPENDED);

        Customer updatedCustomer = customerRepository.save(customer);

        return mapToResponse(updatedCustomer);
    }

    public CustomerResponse activateCustomer(Long id) {

        Customer customer = findCustomerById(id);

        customer.setStatus(CustomerStatus.ACTIVE);

        Customer updatedCustomer = customerRepository.save(customer);

        return mapToResponse(updatedCustomer);
    }

    private Customer findCustomerById(Long id) {

        return customerRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );
    }

    private String normalizeOptional(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private CustomerResponse mapToResponse(Customer customer) {

        CustomerResponse response = new CustomerResponse();

        response.setId(customer.getId());
        response.setCustomerType(customer.getCustomerType());
        response.setName(customer.getName());
        response.setNic(customer.getNic());
        response.setAddress(customer.getAddress());
        response.setMobile(customer.getMobile());
        response.setOfficeNumber(customer.getOfficeNumber());
        response.setEmail(customer.getEmail());
        response.setCity(customer.getCity());
        response.setVatNumber(customer.getVatNumber());
        response.setCreditLimit(customer.getCreditLimit());
        response.setStatus(customer.getStatus());
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());

        return response;
    }
}
