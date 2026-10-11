package com.nonid.affinity.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveHideConfirmationTest {
    private final UUID viewer = UUID.randomUUID(), page = UUID.randomUUID();
    private final String id = "entity:" + UUID.randomUUID();
    @Test void onlyCorrectViewerPageIdentityAndDeathCanConfirmOnce() {
        var service = new ArchiveHideConfirmation(); var ticket = service.prepare(viewer, page, id, 1, 10);
        assertFalse(service.consume(UUID.randomUUID(), page, id, ticket.nonce(), 1, 11));
        assertTrue(service.consume(viewer, page, id, ticket.nonce(), 1, 11));
        assertFalse(service.consume(viewer, page, id, ticket.nonce(), 1, 12));
    }
    @Test void revivalAndNewDeathInvalidateOldConfirmation() {
        var service = new ArchiveHideConfirmation(); var ticket = service.prepare(viewer, page, id, 1, 10);
        assertFalse(service.consume(viewer, page, id, ticket.nonce(), 2, 11));
        assertFalse(service.consume(viewer, page, id, ticket.nonce(), 1, 12));
    }
    @Test void cancelTimeoutAndReplacementCannotWriteAnything() {
        var service = new ArchiveHideConfirmation(); var ticket = service.prepare(viewer, page, id, 1, 10);
        service.cancel(viewer); assertFalse(service.consume(viewer, page, id, ticket.nonce(), 1, 11));
        ticket = service.prepare(viewer, page, id, 1, 10); assertFalse(service.consume(viewer, page, id, ticket.nonce(), 1, 610));
        ticket = service.prepare(viewer, page, id, 1, 20); var newer = service.prepare(viewer, page, id, 1, 21);
        assertFalse(service.consume(viewer, page, id, ticket.nonce(), 1, 22));
        assertTrue(service.consume(viewer, page, id, newer.nonce(), 1, 22));
    }
    @Test void cannotMoveTicketToDifferentPageOrCreature() {
        var service = new ArchiveHideConfirmation(); var ticket = service.prepare(viewer, page, id, 1, 10);
        assertFalse(service.consume(viewer, UUID.randomUUID(), id, ticket.nonce(), 1, 11));
        ticket = service.prepare(viewer, page, id, 1, 10);
        assertFalse(service.consume(viewer, page, "entity:" + UUID.randomUUID(), ticket.nonce(), 1, 11));
    }
}
