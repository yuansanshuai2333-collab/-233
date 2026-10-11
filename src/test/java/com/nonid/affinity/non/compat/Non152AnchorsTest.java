package com.nonid.affinity.non.compat;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class Non152AnchorsTest {
    private static ClassNode load(Path p,String owner)throws Exception{
        assertTrue(Files.isRegularFile(p),p.toString());
        try(var jar=new JarFile(p.toFile())){var node=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(owner+".class"))).accept(node,0);return node;}
    }
    @Test void bothRealVersionsKeepNaturalAndExplicitFinalStageEvidence()throws Exception{
        for(String jar:List.of("libs/needsofnature-1.5.11.21.11.jar","libs/compatibility/needsofnature-1.5.2+1.21.11.jar")){
            var c=load(Path.of(jar),ServerAnimationControllerBytecode.CONTROLLER_OWNER);
            assertTrue(ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(c),jar);
            assertTrue(ServerAnimationControllerBytecode.hasAdvanceMethod(c),jar);
            assertTrue(ServerAnimationControllerBytecode.containsStopInstanceInvocation(ServerAnimationControllerBytecode.advanceMethod(c)),jar);
        }
    }
    @Test void replySelectorsMatchExactlyOneInnerServerThreadHandlerInEachVersion()throws Exception{
        String context="Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;";
        for(String jar:List.of("libs/needsofnature-1.5.11.21.11.jar","libs/compatibility/needsofnature-1.5.2+1.21.11.jar")){
            var c=load(Path.of(jar),"com/nonid/internal/animation/AnimationEngine");
            for(String kind:List.of("Choice","Cancel")){
                String payload="Lcom/nonid/internal/animation/network/DebugAnimation"+kind+"C2SPayload;";
                var names=kind.equals("Choice")?Set.of("lambda$initialize$10","lambda$initialize$11"):Set.of("lambda$initialize$12","lambda$initialize$13");
                var matches=c.methods.stream().filter(m->names.contains(m.name)&&m.desc.equals("("+context+payload+")V")).toList();
                assertEquals(1,matches.size(),jar+kind);
                // The selected body must actually route the same request, not just be an unrelated lambda.
                boolean readsPayload=false;for(var ins:matches.getFirst().instructions)if(ins instanceof MethodInsnNode call&&call.owner.contains("DebugAnimation"+kind+"C2SPayload"))readsPayload=true;
                assertTrue(readsPayload,jar+kind);
            }
        }
    }
}
