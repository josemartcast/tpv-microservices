package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.TableLock;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.TableLockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableLockService {

    private static final long LOCK_TTL_SECONDS = 90;

    private final TableLockRepository repo;
    @PersistenceContext
    private EntityManager entityManager;

    public TableLockService(TableLockRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public TableLock lock(int tableNumber, String terminalId, String username) {
        cleanupExpired();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(LOCK_TTL_SECONDS);
        Optional<TableLock> existingOpt = repo.findByTableNumber(tableNumber);
        if (existingOpt.isEmpty()) {
            try {
                return repo.save(new TableLock(tableNumber, terminalId, username, expiresAt));
            } catch (DataIntegrityViolationException duplicate) {
                // Concurrent insert for same table_number
                // Reset persistence context after failed insert in this transaction.
                entityManager.clear();
                TableLock concurrent = repo.findByTableNumber(tableNumber).orElseThrow(() -> duplicate);
                if (!concurrent.getTerminalId().equalsIgnoreCase(terminalId)) {
                    throw new ConflictException("Table " + tableNumber + " is locked by " + concurrent.getLockedBy()
                            + " (" + concurrent.getTerminalId() + ")");
                }
                concurrent.renew(expiresAt);
                return concurrent;
            }
        }

        TableLock existing = existingOpt.get();
        if (existing.isExpired(now)) {
            repo.delete(existing);
            return repo.save(new TableLock(tableNumber, terminalId, username, expiresAt));
        }

        if (!existing.getTerminalId().equalsIgnoreCase(terminalId)) {
            throw new ConflictException("Table " + tableNumber + " is locked by " + existing.getLockedBy()
                    + " (" + existing.getTerminalId() + ")");
        }
        existing.renew(expiresAt);
        return existing;
    }

    @Transactional
    public TableLock heartbeat(int tableNumber, String terminalId, String username) {
        cleanupExpired();
        Instant now = Instant.now();
        TableLock existing = repo.findByTableNumber(tableNumber)
                .orElseThrow(() -> new ConflictException("No active lock for table " + tableNumber));
        if (existing.isExpired(now)) {
            repo.delete(existing);
            throw new ConflictException("Lock expired for table " + tableNumber);
        }
        if (!existing.getTerminalId().equalsIgnoreCase(terminalId)) {
            throw new ConflictException("Cannot heartbeat lock owned by " + existing.getTerminalId());
        }
        existing.renew(now.plusSeconds(LOCK_TTL_SECONDS));
        return existing;
    }

    @Transactional
    public void unlock(int tableNumber, String terminalId, String username) {
        cleanupExpired();
        TableLock existing = repo.findByTableNumber(tableNumber).orElse(null);
        if (existing == null) {
            return;
        }
        if (!existing.getTerminalId().equalsIgnoreCase(terminalId)) {
            throw new ConflictException("Cannot unlock table locked by " + existing.getTerminalId());
        }
        repo.delete(existing);
    }

    @Transactional(readOnly = true)
    public TableLock activeLock(int tableNumber) {
        TableLock lock = repo.findByTableNumber(tableNumber).orElse(null);
        if (lock == null) {
            return null;
        }
        if (lock.isExpired(Instant.now())) {
            return null;
        }
        return lock;
    }

    @Transactional
    public void cleanupExpired() {
        repo.deleteByExpiresAtBefore(Instant.now());
    }
}
