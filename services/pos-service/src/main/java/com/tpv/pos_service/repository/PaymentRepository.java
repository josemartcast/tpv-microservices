package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Payment;
import com.tpv.pos_service.domain.PaymentMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByTicketId(Long ticketId);

    @Query("select coalesce(sum(p.amountCents),0) from Payment p where p.ticket.id = :ticketId")
    int sumAmountCentsByTicketId(@Param("ticketId") Long ticketId);

    @Query("""
        select p.method, coalesce(sum(p.amountCents),0)
        from Payment p
        where p.ticket.cashSession.id = :cashSessionId
        and p.ticket.status = com.tpv.pos_service.domain.TicketStatus.PAID
        group by p.method
        """)
    List<Object[]> sumByMethodForCashSession(@Param("cashSessionId") Long cashSessionId);

    @Query("""
        select coalesce(sum(p.amountCents),0)
        from Payment p
        join p.ticket t
        where t.cashSession.id = :cashSessionId
          and t.status = com.tpv.pos_service.domain.TicketStatus.PAID
          and p.method = :method
        """)
    int sumByCashSessionAndMethod(@Param("cashSessionId") Long cashSessionId,
            @Param("method") PaymentMethod method);

   /* @Query("""
        select coalesce(sum(p.amountCents),0)
        from Payment p
        where p.ticket.cashSession.id = :csId
          and p.ticket.status = 'PAID'
          and p.method = :method
    """)
    int sumPaidByCashSessionAndMethod(@Param("csId") Long cashSessionId,
            @Param("method") PaymentMethod method);*/

}
