package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByTicketId(Long ticketId);

    @Query("select coalesce(sum(p.amountCents),0) from Payment p where p.ticket.id = :ticketId")
    int sumAmountCentsByTicketId(@Param("ticketId") Long ticketId);

}
