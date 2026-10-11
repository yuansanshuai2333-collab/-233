package com.nonid.affinity.client;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanionSearchIndexTest {
    private CompanionSearchIndex.Row row(int id, String name, String type) { return new CompanionSearchIndex.Row("entity:" + new UUID(0, id), name, type); }
    @Test void namesAndLocalizedSpeciesSearchAcrossAllPages() {
        var index = new CompanionSearchIndex(); var first = new ArrayList<CompanionSearchIndex.Row>();
        for (int i = 1; i <= 64; i++) first.add(row(i, "伙伴" + i, "minecraft:pig"));
        assertTrue(index.append(0, 64, 65, true, first));
        assertFalse(index.search("小白", type -> "猪").complete());
        assertTrue(index.append(64, 65, 65, false, List.of(row(65, "小白", "minecraft:wolf"))));
        assertEquals(1, index.search("小白", type -> type.endsWith("wolf") ? "狼" : "猪").rows().size());
        assertEquals(1, index.search("狼", type -> type.endsWith("wolf") ? "狼" : "猪").rows().size());
        assertEquals(1, index.search("WOLF", type -> "").rows().size());
        assertTrue(index.search("不存在", type -> "").complete());
    }
    @Test void oldOrDuplicatePagesCannotMakeFalseCompleteIndex() {
        var index = new CompanionSearchIndex(); assertTrue(index.append(0, 1, 2, true, List.of(row(1, "一", "minecraft:pig"))));
        assertFalse(index.append(0, 2, 2, false, List.of(row(2, "二", "minecraft:pig"))));
        assertFalse(index.append(1, 2, 2, false, List.of(row(1, "一", "minecraft:pig"))));
        assertFalse(index.append(1, 3, 3, false, List.of(row(2, "二", "minecraft:pig"))));
        assertFalse(index.search("", type -> "").complete());
    }
    @Test void unicodeCaseAndClearWorkWithoutServerWrites() {
        var index = new CompanionSearchIndex(); index.append(0, 1, 1, false, List.of(row(1, "Ａlice", "minecraft:cow")));
        assertEquals(1, index.search(" alice ", type -> "牛").rows().size());
        index.clear(); assertFalse(index.search("", type -> "").complete()); assertTrue(index.search("", type -> "").rows().isEmpty());
    }
}
