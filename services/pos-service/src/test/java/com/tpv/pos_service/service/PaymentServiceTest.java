package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.Payment;
import com.tpv.pos_service.domain.PaymentMethod;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.dto.CreatePaymentRequest;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PaymentServiceTest {

    @Mock
    private TicketRepository ticketRepo;
    @Mock
    private TicketLineRepository lineRepo;
    @Mock
    private PaymentRepository paymentRepo;

    @InjectMocks
    private PaymentService service;

    @Test
    void addPayment_cashRegistersSaleOnlyOnce() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession);

        when(ticketRepo.findById(1L)).thenReturn(Optional.of(ticket));
        when(lineRepo.sumGrossByTicketId(1L)).thenReturn(1_000);
        when(paymentRepo.sumAmountCentsByTicketId(1L)).thenReturn(0);
        when(paymentRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addPayment(1L, new CreatePaymentRequest(PaymentMethod.CASH, 300));

        assertEquals(300, cashSession.getExpectedCashCents());
    }

    @Test
    void addPayment_withSameIdempotencyKey_doesNotDuplicateCashSale() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession);
        String key = "idem-1";

        AtomicReference<Payment> saved = new AtomicReference<>();

        when(ticketRepo.findById(1L)).thenReturn(Optional.of(ticket));
        when(lineRepo.sumGrossByTicketId(1L)).thenReturn(1_000);
        when(paymentRepo.sumAmountCentsByTicketId(1L)).thenReturn(0);
        when(paymentRepo.findByTicketIdAndIdempotencyKey(1L, key)).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(paymentRepo.save(any())).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            saved.set(p);
            return p;
        });

        service.addPayment(1L, new CreatePaymentRequest(PaymentMethod.CASH, 300), key);
        service.addPayment(1L, new CreatePaymentRequest(PaymentMethod.CASH, 300), key);

        assertEquals(300, cashSession.getExpectedCashCents());
    }
}
