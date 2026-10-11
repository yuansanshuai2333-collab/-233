package com.nonid.affinity.client;
import com.nonid.affinity.service.FreeTradeStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeTradeStatusTextTest {
    @Test void onlyAnEligibleFreeOfferHasAPanel() {
        assertNull(FreeTradeStatusText.panelFor(null));
        for (var reason : FreeTradeStatus.Reason.values()) {
            if (reason == FreeTradeStatus.Reason.FREE) continue;
            for (boolean free : new boolean[]{false, true}) {
                assertNull(FreeTradeStatusText.panelFor(new FreeTradeStatus(3, 0, free,
                        0, 3, 1, "Emerald", reason, 1)));
            }
        }
        assertNull(FreeTradeStatusText.panelFor(new FreeTradeStatus(3, 0, false,
                3, 3, 1, "Emerald", FreeTradeStatus.Reason.FREE, 1)));
    }
    @Test void freeOfferShowsBothMaterialsWaivedAndRemainingCount() {
        var panel = FreeTradeStatusText.panelFor(new FreeTradeStatus(3, 0, true,
                2, 3, 0, "Emerald", FreeTradeStatus.Reason.FREE, 1));
        assertNotNull(panel);
        assertEquals(java.util.List.of("noncreatureaffinity.trade.free",
                "noncreatureaffinity.trade.free_materials", "noncreatureaffinity.trade.free_remaining"),
                panel.lines().stream().map(FreeTradeStatusText.Line::key).toList());
        assertArrayEquals(new Object[]{2, 3}, panel.lines().get(2).args());
    }
}
