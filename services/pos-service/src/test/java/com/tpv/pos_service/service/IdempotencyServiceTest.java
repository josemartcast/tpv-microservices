package com.tpv.pos_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpv.pos_service.domain.IdempotencyRequest;
import com.tpv.pos_service.repository.IdempotencyRequestRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRequestRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService service;

    @Test
    void execute_returnsStoredResponse_whenKeyAlreadyExists() throws Exception {
        when(repository.findByScopeAndResourceIdAndIdempotencyKey("ticket-send", 10L, "k1"))
                .thenReturn(Optional.of(new IdempotencyRequest("ticket-send", 10L, "k1", "{\"ok\":true}")));
        when(objectMapper.readValue("{\"ok\":true}", DummyResponse.class))
                .thenReturn(new DummyResponse(true));

        AtomicBoolean executed = new AtomicBoolean(false);
        DummyResponse out = service.execute("ticket-send", 10L, "k1", DummyResponse.class, () -> {
            executed.set(true);
            return new DummyResponse(false);
        });

        assertFalse(executed.get());
        assertTrue(out.ok());
    }

    @Test
    void execute_runsAndStores_whenKeyIsNew() throws Exception {
        when(repository.findByScopeAndResourceIdAndIdempotencyKey("cash-close", 4L, "k2"))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any(DummyResponse.class))).thenReturn("{\"ok\":true}");
        when(repository.save(any(IdempotencyRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DummyResponse out = service.execute("cash-close", 4L, "k2", DummyResponse.class, () -> new DummyResponse(true));
        assertTrue(out.ok());

        ArgumentCaptor<IdempotencyRequest> captor = ArgumentCaptor.forClass(IdempotencyRequest.class);
        verify(repository).save(captor.capture());
        assertEquals("{\"ok\":true}", captor.getValue().getResponseJson());
    }

    @Test
    void claim_returnsTrue_whenKeyIsNew() {
        when(repository.save(any(IdempotencyRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean claimed = service.claim("ticket-autoprint-comanda", 17L, "BAR:abc123");

        assertTrue(claimed);
    }

    @Test
    void claim_returnsFalse_whenDuplicateClaimArrives() {
        when(repository.save(any(IdempotencyRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        boolean claimed = service.claim("ticket-autoprint-comanda", 17L, "BAR:abc123");

        assertFalse(claimed);
    }

    private record DummyResponse(boolean ok) {
    }
}
