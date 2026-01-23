package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.TicketLine;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketLineRepository extends JpaRepository<TicketLine, Long> {

    List<TicketLine> findAllByTicketIdOrderByIdAsc(Long ticketId);

    Optional<TicketLine> findByIdAndTicketId(Long id, Long ticketId);

    void deleteAllByTicketId(Long ticketId);

    @Query("select coalesce(sum(l.lineTotalCents),0) from TicketLine l where l.ticket.id = :ticketId")
    int sumGrossByTicketId(@Param("ticketId") Long ticketId);

    @Query("select coalesce(sum(l.netLineTotalCents),0) from TicketLine l where l.ticket.id = :ticketId")
    int sumNetByTicketId(@Param("ticketId") Long ticketId);

    @Query("select coalesce(sum(l.vatLineTotalCents),0) from TicketLine l where l.ticket.id = :ticketId")
    int sumVatByTicketId(@Param("ticketId") Long ticketId);

    @Query("""
        select coalesce(sum(l.lineTotalCents),0)
        from TicketLine l
        where l.ticket.cashSession.id = :csId
          and l.ticket.status = 'PAID'
    """)
    int sumGrossPaidByCashSession(@Param("csId") Long cashSessionId);

    @Query("""
        select coalesce(sum(l.netLineTotalCents),0)
        from TicketLine l
        where l.ticket.cashSession.id = :csId
          and l.ticket.status = 'PAID'
    """)
    int sumNetPaidByCashSession(@Param("csId") Long cashSessionId);

    @Query("""
        select coalesce(sum(l.vatLineTotalCents),0)
        from TicketLine l
        where l.ticket.cashSession.id = :csId
          and l.ticket.status = 'PAID'
    """)
    int sumVatPaidByCashSession(@Param("csId") Long cashSessionId);

    @Query("""
        select coalesce(sum(l.lineTotalCents),0)
        from TicketLine l
        join l.ticket t
        where t.cashSession.id = :cashSessionId and t.status = com.tpv.pos_service.domain.TicketStatus.PAID
        """)
    int sumGrossByCashSession(@Param("cashSessionId") Long cashSessionId);

    @Query("""
        select coalesce(sum(l.netLineTotalCents),0)
        from TicketLine l
        join l.ticket t
        where t.cashSession.id = :cashSessionId and t.status = com.tpv.pos_service.domain.TicketStatus.PAID
        """)
    int sumNetByCashSession(@Param("cashSessionId") Long cashSessionId);

    @Query("""
        select coalesce(sum(l.vatLineTotalCents),0)
        from TicketLine l
        join l.ticket t
        where t.cashSession.id = :cashSessionId and t.status = com.tpv.pos_service.domain.TicketStatus.PAID
        """)
    int sumVatByCashSession(@Param("cashSessionId") Long cashSessionId);

}
