package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Customer;
import com.tpv.pos_service.dto.CreateCustomerRequest;
import com.tpv.pos_service.dto.CustomerResponse;
import com.tpv.pos_service.dto.UpdateCustomerRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CustomerRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listActive() {
        return repository.findAllByActiveTrueOrderByDisplayNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        Customer customer = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req) {
        String displayName = normalizeRequired(req.displayName(), "displayName");
        if (repository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException("Customer displayName already exists: " + displayName);
        }

        Customer created = new Customer(displayName);
        created.updateFiscalData(
                normalizeOptional(req.legalName()),
                normalizeOptional(req.taxId()).toUpperCase(Locale.ROOT),
                normalizeOptional(req.fiscalAddress()),
                normalizeOptional(req.postalCode()),
                normalizeOptional(req.city()),
                normalizeOptional(req.province()),
                normalizeOptional(req.country()).toUpperCase(Locale.ROOT),
                normalizeOptional(req.phone()),
                normalizeOptional(req.email()).toLowerCase(Locale.ROOT)
        );
        repository.save(created);
        return toResponse(created);
    }

    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest req) {
        Customer customer = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));

        String displayName = normalizeRequired(req.displayName(), "displayName");
        if (!customer.getDisplayName().equalsIgnoreCase(displayName)
                && repository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException("Customer displayName already exists: " + displayName);
        }

        customer.rename(displayName);
        customer.updateFiscalData(
                normalizeOptional(req.legalName()),
                normalizeOptional(req.taxId()).toUpperCase(Locale.ROOT),
                normalizeOptional(req.fiscalAddress()),
                normalizeOptional(req.postalCode()),
                normalizeOptional(req.city()),
                normalizeOptional(req.province()),
                normalizeOptional(req.country()).toUpperCase(Locale.ROOT),
                normalizeOptional(req.phone()),
                normalizeOptional(req.email()).toLowerCase(Locale.ROOT)
        );
        return toResponse(customer);
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));
        customer.deactivate();
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getDisplayName(),
                customer.getLegalName(),
                customer.getTaxId(),
                customer.getFiscalAddress(),
                customer.getPostalCode(),
                customer.getCity(),
                customer.getProvince(),
                customer.getCountry(),
                customer.getPhone(),
                customer.getEmail(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
