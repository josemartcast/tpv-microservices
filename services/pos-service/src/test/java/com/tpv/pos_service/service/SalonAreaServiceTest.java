package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.SalonArea;
import com.tpv.pos_service.dto.CreateSalonAreaRequest;
import com.tpv.pos_service.dto.SalonAreaResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.SalonAreaRepository;
import com.tpv.pos_service.repository.TableLockRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SalonAreaServiceTest {

    @Mock
    private SalonAreaRepository salonAreaRepo;
    @Mock
    private TicketRepository ticketRepo;
    @Mock
    private TableLockRepository tableLockRepo;
    @Mock
    private TableAliasService tableAliasService;

    @InjectMocks
    private SalonAreaService service;

    @Test
    void create_reactivatesInactiveSalonWithSameName() {
        SalonArea inactive = new SalonArea("Terraza", 1, 12);
        ReflectionTestUtils.setField(inactive, "id", 9L);
        inactive.deactivate();

        when(salonAreaRepo.findByNameIgnoreCase("Terraza")).thenReturn(Optional.of(inactive));
        when(salonAreaRepo.findOverlappingActiveRange(20, 25)).thenReturn(List.of());
        when(salonAreaRepo.save(any(SalonArea.class))).thenAnswer(inv -> inv.getArgument(0));

        SalonAreaResponse out = service.create(new CreateSalonAreaRequest("Terraza", 6, 20));

        assertEquals(9L, out.id());
        assertEquals("Terraza", out.name());
        assertEquals(20, out.firstTableNumber());
        assertEquals(6, out.tableCount());
        assertEquals(25, out.lastTableNumber());
        assertTrue(out.active());
        assertTrue(inactive.isActive());
        verify(salonAreaRepo).save(inactive);
    }

    @Test
    void create_rejectsWhenActiveSalonWithSameNameExists() {
        SalonArea active = new SalonArea("Terraza", 1, 12);
        ReflectionTestUtils.setField(active, "id", 4L);

        when(salonAreaRepo.findByNameIgnoreCase("Terraza")).thenReturn(Optional.of(active));

        assertThrows(
                ConflictException.class,
                () -> service.create(new CreateSalonAreaRequest("Terraza", 6, 20))
        );

        verify(salonAreaRepo, never()).findOverlappingActiveRange(anyInt(), anyInt());
        verify(salonAreaRepo, never()).save(any(SalonArea.class));
    }
}
