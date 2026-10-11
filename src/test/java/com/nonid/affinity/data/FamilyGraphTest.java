package com.nonid.affinity.data;

import java.util.*;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FamilyGraphTest {
    private String id(int i) { return "entity:" + new UUID(0, i); }
    private FamilyGraph.Node node(int i) { return new FamilyGraph.Node(id(i), "伙伴" + i, "minecraft:pig", 0); }
    @Test void incompleteEvidenceDoesNotCreateSecondParent() {
        var graph = new FamilyGraph(); graph.put(node(1)); graph.put(node(2));
        assertTrue(graph.link(id(1), id(2), FamilyGraph.Evidence.NON_BIRTH));
        assertEquals(1, graph.edges().size()); assertEquals(-1, graph.generations(id(2), id(1)));
        assertFalse(graph.link(id(99), id(2), FamilyGraph.Evidence.NON_BIRTH));
        assertEquals(2, graph.size());
    }
    @Test void rejectsSelfCyclesAndDuplicateEvidence() {
        var graph = new FamilyGraph(); for (int i = 1; i <= 3; i++) graph.put(node(i));
        assertTrue(graph.link(id(1), id(2), FamilyGraph.Evidence.NON_BIRTH));
        assertTrue(graph.link(id(2), id(3), FamilyGraph.Evidence.NON_BIRTH));
        assertFalse(graph.link(id(3), id(1), FamilyGraph.Evidence.VANILLA_BIRTH));
        assertFalse(graph.link(id(2), id(2), FamilyGraph.Evidence.NON_BIRTH));
        assertFalse(graph.link(id(1), id(2), FamilyGraph.Evidence.SAVED_BIRTH_PARENT));
        assertEquals(2, graph.edges().size());
    }
    @Test void longChainsPageWithoutCroppingAndNoRecursion() {
        var graph = new FamilyGraph();
        for (int i = 1; i <= 2500; i++) { graph.put(node(i)); if (i > 1) assertTrue(graph.link(id(i-1), id(i), FamilyGraph.Evidence.VANILLA_BIRTH)); }
        assertEquals(2499, graph.generations(id(1), id(2500)));
        assertEquals(-2499, graph.generations(id(2500), id(1)));
        var all = new HashSet<String>();
        for (int offset = 0; offset < 2500; offset += 64) {
            var page = graph.page(id(1250), offset, 64); assertEquals(2500, page.total());
            page.nodes().forEach(row -> assertTrue(all.add(row.node().id())));
        }
        assertEquals(2500, all.size()); assertEquals(2499, graph.edges().size());
        assertTrue(graph.page(id(1), Integer.MAX_VALUE, 64).nodes().isEmpty());
    }
    @Test void roundTripPreservesEvidenceAndUnknownGender() {
        var graph = new FamilyGraph(); graph.put(node(1)); graph.put(new FamilyGraph.Node(id(2), "自定义名字", "minecraft:cow", 3));
        graph.link(id(1), id(2), FamilyGraph.Evidence.SAVED_BIRTH_PARENT);
        var restored = FamilyGraph.fromNbt(graph.toNbt());
        assertEquals(graph.edges(), restored.edges()); assertEquals("自定义名字", restored.get(id(2)).name());
        assertEquals(0, restored.get(id(2)).gender()); assertEquals(2, restored.page(id(1), 0, 64).total());
    }
    @Test void malformedSavedCycleTerminatesAndUnknownEvidenceCannotGrantKinship() {
        var graph = new FamilyGraph(); for (int i = 1; i <= 3; i++) graph.put(node(i));
        graph.link(id(1), id(2), FamilyGraph.Evidence.NON_BIRTH);
        var n = graph.toNbt(); var links = n.getCompoundOrEmpty("parents");
        var cycle = new NbtCompound(); cycle.putString(id(2), "NON_BIRTH"); links.put(id(1), cycle);
        var invented = new NbtCompound(); invented.putString(id(1), "PROXIMITY"); links.put(id(3), invented);
        n.put("parents", links); var restored = FamilyGraph.fromNbt(n);
        assertEquals(1, restored.edges().size()); assertNull(restored.generations(id(1), id(3)));
    }
    @Test void separateViewerGraphsDoNotShareMutableState() {
        var a = new FamilyGraph(); a.put(node(1)); a.put(node(2)); a.link(id(1), id(2), FamilyGraph.Evidence.NON_BIRTH);
        var b = new FamilyGraph(); b.put(node(1));
        assertEquals(1, b.page(id(1), 0, 64).total()); assertTrue(b.edges().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> a.page(id(1), -1, 64));
        assertThrows(IllegalArgumentException.class, () -> a.page(id(1), 0, 65));
    }
    @Test void coparentIsNotInventedAsBloodRelativeOfOtherParent() {
        var graph = new FamilyGraph(); for (int i = 1; i <= 3; i++) graph.put(node(i));
        graph.link(id(1), id(3), FamilyGraph.Evidence.VANILLA_BIRTH); graph.link(id(2), id(3), FamilyGraph.Evidence.VANILLA_BIRTH);
        assertNull(graph.generations(id(1), id(2))); assertFalse(graph.relatedIds(id(1)).contains(id(2)));
        assertTrue(graph.relatedIds(id(3)).contains(id(1))); assertTrue(graph.relatedIds(id(3)).contains(id(2)));
    }
    @Test void newNameOriginsSurviveSavingWithoutChangingNamesOrKinship(){
        var graph=new FamilyGraph();graph.put(new FamilyGraph.Node(id(1),"Pig","minecraft:pig",0,FamilyGraph.NameKind.DEFAULT_TYPE));
        graph.put(new FamilyGraph.Node(id(2),"English Pet","minecraft:pig",0,FamilyGraph.NameKind.CUSTOM));
        graph.put(new FamilyGraph.Node("player:"+new UUID(0,3),"English Player","minecraft:player",0,FamilyGraph.NameKind.PLAYER));
        graph.link(id(1),id(2),FamilyGraph.Evidence.VANILLA_BIRTH);var read=FamilyGraph.fromNbt(graph.toNbt());
        assertEquals(FamilyGraph.NameKind.DEFAULT_TYPE,read.get(id(1)).nameKind());assertEquals(FamilyGraph.NameKind.CUSTOM,read.get(id(2)).nameKind());
        assertEquals("English Pet",read.get(id(2)).name());assertEquals(FamilyGraph.NameKind.PLAYER,read.get("player:"+new UUID(0,3)).nameKind());assertEquals(graph.edges(),read.edges());
    }
    @Test void oldAndUnknownNameMetadataStayConservative(){
        var graph=new FamilyGraph();graph.put(new FamilyGraph.Node(id(1),"Pig","minecraft:pig",0));
        var n=graph.toNbt();n.getCompoundOrEmpty("nodes").getCompoundOrEmpty(id(1)).remove("name_kind");
        var old=FamilyGraph.fromNbt(n);assertEquals(FamilyGraph.NameKind.UNKNOWN,old.get(id(1)).nameKind());assertEquals("Pig",old.get(id(1)).name());
        n.getCompoundOrEmpty("nodes").getCompoundOrEmpty(id(1)).putString("name_kind","UNRECOGNIZED");
        var unknown=FamilyGraph.fromNbt(n);assertEquals(FamilyGraph.NameKind.UNKNOWN,unknown.get(id(1)).nameKind());assertEquals("Pig",unknown.get(id(1)).name());
    }
}
