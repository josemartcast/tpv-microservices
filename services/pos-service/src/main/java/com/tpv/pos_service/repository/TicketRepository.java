package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findAllByStatusOrderByCreatedAtDesc(TicketStatus status);
    int countByCashSession_IdAndStatus(Long cashSessionId, TicketStatus status);
    List<Ticket> findAllByCashSession_IdAndStatus(Long cashSessionId, TicketStatus status);

    
}

