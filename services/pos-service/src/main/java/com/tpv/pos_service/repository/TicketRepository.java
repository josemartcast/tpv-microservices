package com.tpv.pos_service.repository;

import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findAllByStatusOrderByCreatedAtDesc(TicketStatus status);
    List<Ticket> findAllByCashSession_IdOrderByCreatedAtDesc(Long cashSessionId);
    int countByCashSession_IdAndStatus(Long cashSessionId, TicketStatus status);
    List<Ticket> findAllByCashSession_IdAndStatus(Long cashSessionId, TicketStatus status);
    boolean existsByStatus(TicketStatus status);
    boolean existsByCashSession_IdAndStatus(Long cashSessionId, TicketStatus status);
    boolean existsByTableNumberAndStatus(Integer tableNumber, TicketStatus status);
    java.util.Optional<Ticket> findByTableNumberAndStatus(Integer tableNumber, TicketStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :ticketId")
    Optional<Ticket> findByIdForUpdate(@Param("ticketId") Long ticketId);
}

