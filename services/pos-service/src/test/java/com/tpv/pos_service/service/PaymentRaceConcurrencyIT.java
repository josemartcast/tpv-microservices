package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.domain.PaymentMethod;
import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.dto.CreatePaymentRequest;
import com.tpv.pos_service.dto.PaymentSummaryResponse;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.CategoryRepository;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.ProductRepository;
import com.tpv.pos_service.repository.TableLockRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PaymentRaceConcurrencyIT {

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private TicketService ticketService;
    @Autowired
    private CashSessionRepository cashSessionRepo;
    @Autowired
    private CategoryRepository categoryRepo;
    @Autowired
    private ProductRepository productRepo;
    @Autowired
    private PaymentRepository paymentRepo;
    @Autowired
    private TicketRepository ticketRepo;
    @Autowired
    private TicketLineRepository ticketLineRepo;
    @Autowired
    private TableLockRepository tableLockRepo;

    @BeforeEach
    void cleanData() {
        tableLockRepo.deleteAll();
        paymentRepo.deleteAll();
        ticketLineRepo.deleteAll();
        ticketRepo.deleteAll();
        productRepo.deleteAll();
        categoryRepo.deleteAll();
        cashSessionRepo.deleteAll();
    }

    @Test
    void concurrentFullPayment_allowsOneAndRejectsOther() throws Exception {
        cashSessionRepo.save(new CashSession(0, "qa", null));

        Category category = categoryRepo.save(new Category("QA-CAT"));
        Product product = productRepo.save(new Product("QA-PROD", 1_000, category, 1000));

        TicketResponse ticket = ticketService.create(1);
        ticketService.addLine(ticket.id(), product.getId(), 1);

        PaymentSummaryResponse before = ticketService.paymentSummary(ticket.id());
        int pending = before.pendingCents();
        assertTrue(pending > 0, "ticket should have pending amount");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<PayResult> payA = () -> runPay(start, ticket.id(), pending);
            Callable<PayResult> payB = () -> runPay(start, ticket.id(), pending);

            Future<PayResult> futureA = pool.submit(payA);
            Future<PayResult> futureB = pool.submit(payB);

            start.countDown();

            PayResult resultA = futureA.get(10, TimeUnit.SECONDS);
            PayResult resultB = futureB.get(10, TimeUnit.SECONDS);

            int successCount = (resultA.success ? 1 : 0) + (resultB.success ? 1 : 0);
            assertEquals(1, successCount, "exactly one concurrent payment should succeed");

            PayResult successful = resultA.success ? resultA : resultB;
            PayResult failed = resultA.success ? resultB : resultA;
            assertTrue(successful.paymentId != null && successful.paymentId > 0, "successful payment should return id");
            assertTrue(failed.error instanceof ConflictException, "losing concurrent payment should be conflict");

            PaymentSummaryResponse after = ticketService.paymentSummary(ticket.id());
            assertEquals(0, after.pendingCents(), "ticket should end fully paid");
            assertEquals(1, paymentRepo.findByTicketId(ticket.id()).size(), "only one payment must be persisted");
            assertEquals(pending, after.paidCents(), "paid amount should match original pending");
        } finally {
            pool.shutdownNow();
        }
    }

    private PayResult runPay(CountDownLatch start, long ticketId, int amountCents) {
        try {
            start.await(5, TimeUnit.SECONDS);
            var response = paymentService.addPayment(ticketId, new CreatePaymentRequest(PaymentMethod.CARD, amountCents), null);
            return PayResult.success(response.id());
        } catch (Throwable e) {
            return PayResult.failure(e);
        }
    }

    private static final class PayResult {
        private final boolean success;
        private final Long paymentId;
        private final Throwable error;

        private PayResult(boolean success, Long paymentId, Throwable error) {
            this.success = success;
            this.paymentId = paymentId;
            this.error = error;
        }

        static PayResult success(Long paymentId) {
            return new PayResult(true, paymentId, null);
        }

        static PayResult failure(Throwable error) {
            return new PayResult(false, null, error);
        }
    }
}
