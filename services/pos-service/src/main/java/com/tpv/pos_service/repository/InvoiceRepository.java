package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Invoice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByTicket_Id(Long ticketId);

    @Query("select i from Invoice i left join fetch i.lines where i.ticket.id = :ticketId")
    Optional<Invoice> findWithLinesByTicketId(@Param("ticketId") Long ticketId);

    @Query("select i from Invoice i left join fetch i.lines where i.id = :id")
    Optional<Invoice> findWithLinesById(@Param("id") Long id);
}
