package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.dto.AutoPrintClaimResponse;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ComandaServiceTest {

    @Mock
    private TicketRepository ticketRepo;
    @Mock
    private TicketLineRepository lineRepo;
    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private ComandaService service;

    @Test
    void claimAutoPrint_returnsClaimedTrue_whenFirstClaim() {
        when(ticketRepo.findById(42L)).thenReturn(Optional.of(mock(Ticket.class)));
        when(idempotencyService.claim("ticket-autoprint-comanda", 42L, "BAR:abc123"))
                .thenReturn(true);

        AutoPrintClaimResponse response = service.claimAutoPrint(42L, "bar", "abc123");

        assertEquals(42L, response.ticketId());
        assertEquals("BAR", response.destination());
        assertEquals("abc123", response.printJobId());
        assertTrue(response.claimed());
    }

    @Test
    void claimAutoPrint_returnsClaimedFalse_whenDuplicateClaim() {
        when(ticketRepo.findById(42L)).thenReturn(Optional.of(mock(Ticket.class)));
        when(idempotencyService.claim("ticket-autoprint-comanda", 42L, "COCINA:abc123"))
                .thenReturn(false);

        AutoPrintClaimResponse response = service.claimAutoPrint(42L, "cocina", "abc123");

        assertFalse(response.claimed());
        verify(idempotencyService).claim("ticket-autoprint-comanda", 42L, "COCINA:abc123");
    }

    @Test
    void claimAutoPrint_throwsNotFound_whenTicketDoesNotExist() {
        when(ticketRepo.findById(404L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.claimAutoPrint(404L, "BAR", "abc123"));
    }
}
