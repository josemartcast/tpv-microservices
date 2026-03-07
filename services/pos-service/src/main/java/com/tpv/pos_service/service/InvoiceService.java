package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.BusinessProfile;
import com.tpv.pos_service.domain.Customer;
import com.tpv.pos_service.domain.Invoice;
import com.tpv.pos_service.domain.InvoiceLine;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketLine;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.InvoiceLineResponse;
import com.tpv.pos_service.dto.InvoiceResponse;
import com.tpv.pos_service.dto.InvoiceSummaryResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.BusinessProfileRepository;
import com.tpv.pos_service.repository.CustomerRepository;
import com.tpv.pos_service.repository.InvoiceRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;
    private final TicketLineRepository ticketLineRepository;
    private final CustomerRepository customerRepository;
    private final BusinessProfileRepository businessProfileRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            TicketRepository ticketRepository,
            TicketLineRepository ticketLineRepository,
            CustomerRepository customerRepository,
            BusinessProfileRepository businessProfileRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.ticketRepository = ticketRepository;
        this.ticketLineRepository = ticketLineRepository;
        this.customerRepository = customerRepository;
        this.businessProfileRepository = businessProfileRepository;
    }

    @Transactional
    public InvoiceResponse issueForTicket(Long ticketId, Long customerId, String actor) {
        Invoice existing = invoiceRepository.findWithLinesByTicketId(ticketId).orElse(null);
        if (existing != null) {
            if (existing.getCustomer().getId().equals(customerId)) {
                return toResponse(existing);
            }
            throw new ConflictException("Ticket already invoiced with another customer");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new ConflictException("Only PAID tickets can be invoiced");
        }

        Customer customer = customerRepository.findByIdAndActiveTrue(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

        List<TicketLine> ticketLines = ticketLineRepository.findAllByTicketIdOrderByIdAsc(ticketId);
        if (ticketLines.isEmpty()) {
            throw new ConflictException("Cannot issue invoice for empty ticket");
        }

        BusinessProfile business = businessProfileRepository.findById(BusinessProfile.SINGLETON_ID)
                .orElseGet(() -> businessProfileRepository.save(new BusinessProfile(BusinessProfile.SINGLETON_ID, "Restaurante EL GUSTO")));

        Invoice invoice = new Invoice(ticket, customer, actor);
        invoice.setBusinessSnapshot(
                business.getBusinessName(),
                business.getLegalName(),
                business.getTaxId(),
                business.getAddress(),
                business.getPostalCode(),
                business.getCity(),
                business.getProvince(),
                business.getCountry(),
                business.getPhone(),
                business.getEmail()
        );
        invoice.setCustomerSnapshot(
                customer.getDisplayName(),
                customer.getLegalName(),
                customer.getTaxId(),
                customer.getFiscalAddress(),
                customer.getPostalCode(),
                customer.getCity(),
                customer.getProvince(),
                customer.getCountry(),
                customer.getPhone(),
                customer.getEmail()
        );

        List<InvoiceLine> lines = ticketLines.stream()
                .map(line -> new InvoiceLine(
                        line.getId(),
                        line.getProductNameSnapshot(),
                        line.getQty(),
                        line.getUnitPriceCentsSnapshot(),
                        line.getLineTotalCents(),
                        line.getVatRateBpsSnapshot(),
                        line.getNetLineTotalCents(),
                        line.getVatLineTotalCents()
                ))
                .toList();
        invoice.replaceLines(lines);

        try {
            invoiceRepository.save(invoice);
        } catch (DataIntegrityViolationException ex) {
            Invoice maybe = invoiceRepository.findWithLinesByTicketId(ticketId).orElse(null);
            if (maybe != null && maybe.getCustomer().getId().equals(customerId)) {
                return toResponse(maybe);
            }
            throw ex;
        }

        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            int year = invoice.getIssuedAt().atZone(ZoneId.systemDefault()).getYear();
            invoice.setInvoiceNumber("F-" + year + "-" + String.format("%06d", invoice.getId()));
            invoiceRepository.save(invoice);
        }
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getByTicket(Long ticketId) {
        Invoice invoice = invoiceRepository.findWithLinesByTicketId(ticketId)
                .orElseThrow(() -> new NotFoundException("Invoice not found for ticket: " + ticketId));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponse> list(
            String invoiceNumber,
            String customer,
            LocalDate issuedFrom,
            LocalDate issuedTo,
            Integer limit
    ) {
        String invoiceFilter = normalizeFilter(invoiceNumber);
        String customerFilter = normalizeFilter(customer);
        Instant fromInstant = issuedFrom == null
                ? null
                : issuedFrom.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = issuedTo == null
                ? null
                : issuedTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        int size = (limit == null || limit <= 0) ? 200 : Math.min(limit, 1000);

        return invoiceRepository.search(
                        invoiceFilter,
                        customerFilter,
                        fromInstant,
                        toInstant,
                        PageRequest.of(0, size)
                ).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceLineResponse> lines = invoice.getLines().stream()
                .map(line -> new InvoiceLineResponse(
                        line.getId(),
                        line.getTicketLineId(),
                        line.getProductName(),
                        line.getQty(),
                        line.getUnitGrossCents(),
                        line.getLineGrossCents(),
                        line.getVatRateBps(),
                        line.getLineNetCents(),
                        line.getLineVatCents()
                ))
                .toList();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getIssuedAt(),
                invoice.getIssuedBy(),
                invoice.getTicket().getId(),
                invoice.getTicket().getTableNumber(),
                invoice.getCustomer().getId(),
                invoice.getCustomerDisplayName(),
                invoice.getCustomerLegalName(),
                invoice.getCustomerTaxId(),
                invoice.getCustomerAddress(),
                invoice.getCustomerPostalCode(),
                invoice.getCustomerCity(),
                invoice.getCustomerProvince(),
                invoice.getCustomerCountry(),
                invoice.getCustomerPhone(),
                invoice.getCustomerEmail(),
                invoice.getBusinessName(),
                invoice.getBusinessLegalName(),
                invoice.getBusinessTaxId(),
                invoice.getBusinessAddress(),
                invoice.getBusinessPostalCode(),
                invoice.getBusinessCity(),
                invoice.getBusinessProvince(),
                invoice.getBusinessCountry(),
                invoice.getBusinessPhone(),
                invoice.getBusinessEmail(),
                invoice.getTotalGrossCents(),
                invoice.getTotalNetCents(),
                invoice.getTotalVatCents(),
                lines
        );
    }

    private InvoiceSummaryResponse toSummaryResponse(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getIssuedAt(),
                invoice.getIssuedBy(),
                invoice.getTicket().getId(),
                invoice.getTicket().getTableNumber(),
                invoice.getCustomer().getId(),
                invoice.getCustomerDisplayName(),
                invoice.getCustomerTaxId(),
                invoice.getTotalGrossCents()
        );
    }

    private static String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
