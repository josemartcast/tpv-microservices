package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.TicketLine;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketLineRepository extends JpaRepository<TicketLine, Long> {
    List<TicketLine> findAllByTicketIdOrderByIdAsc(Long ticketId);
    Optional<TicketLine> findByIdAndTicketId(Long id, Long ticketId);
    void deleteAllByTicketId(Long ticketId);
}
