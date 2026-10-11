package com.nonid.affinity.non.compat;
import com.nonid.internal.animation.server.AnimationStartContext;
import java.util.function.Supplier;
/** Actual innermost engine context; queue entries can bypass the outer startNow capture. */
public final class NativeEventContext {
    private static final ThreadLocal<AnimationStartContext> CURRENT=new ThreadLocal<>();
    public static boolean continuation(){var c=CURRENT.get();return c!=null&&(c.isJoinReplacement()||c.queueContinuation());}
    public static <T>T run(AnimationStartContext context,Supplier<T> operation){
        var previous=CURRENT.get();if(context==null)CURRENT.remove();else CURRENT.set(context);
        try{return operation.get();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    private NativeEventContext(){}
}
