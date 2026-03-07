package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Invoice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByTicket_Id(Long ticketId);

    @Query("select i from Invoice i left join fetch i.lines where i.ticket.id = :ticketId")
    Optional<Invoice> findWithLinesByTicketId(@Param("ticketId") Long ticketId);

    @Query("select i from Invoice i left join fetch i.lines where i.id = :id")
    Optional<Invoice> findWithLinesById(@Param("id") Long id);

    @Query("""
            select i from Invoice i
            where (:invoiceNumber is null or lower(i.invoiceNumber) like lower(concat('%', :invoiceNumber, '%')))
              and (:customer is null
                    or lower(i.customerDisplayName) like lower(concat('%', :customer, '%'))
                    or lower(i.customerLegalName) like lower(concat('%', :customer, '%'))
                    or lower(i.customerTaxId) like lower(concat('%', :customer, '%')))
              and (:fromInstant is null or i.issuedAt >= :fromInstant)
              and (:toInstant is null or i.issuedAt < :toInstant)
            order by i.issuedAt desc, i.id desc
            """)
    List<Invoice> search(
            @Param("invoiceNumber") String invoiceNumber,
            @Param("customer") String customer,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            Pageable pageable
    );
}
