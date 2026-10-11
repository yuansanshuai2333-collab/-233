package com.nonid.affinity.non.compat;
import com.nonid.NonAnimationSessionData;
import com.nonid.api.animation.NonAnimationDamageBehavior;
import com.nonid.api.animation.NonAnimationEvents;
import com.nonid.internal.animation.network.AnimationStageInfo;
import com.nonid.internal.animation.server.AnimationStartContext;
import com.nonid.internal.animation.api.AnimationService;
import com.nonid.internal.animation.util.ActorRoleBindings;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Captures server-owned NoN intent; nothing is supplied by the answering client. */
public final class NativeStartIntent {
    private static final ThreadLocal<NativeStartIntent> CURRENT=new ThreadLocal<>(),RESUMING=new ThreadLocal<>();
    private final RegistryKey<World> world;
    private final Identifier animation;
    private final List<UUID> actors;
    private final List<String> keys;
    private final List<UUID> orderedActors;
    private final List<String> orderedKeys;
    private final List<AnimationStageInfo> stages;
    private final UUID requester,anchor;
    private final NonAnimationDamageBehavior damage;
    private final boolean ignoreAttackers,debug,multiple;
    private final AnimationStartContext context;
    public NativeStartIntent(ServerWorld world,ServerPlayerEntity requester,Identifier animation,
            List<Entity> actors,List<String> keys,List<AnimationStageInfo> stages,
            NonAnimationDamageBehavior damage,boolean ignoreAttackers,UUID anchor,
            AnimationStartContext context,boolean debug,boolean multiple){
        this.world=world.getRegistryKey();this.animation=animation;
        this.actors=actors.stream().map(Entity::getUuid).toList();
        this.keys=keys==null?List.of():List.copyOf(keys);this.stages=stages==null?List.of():List.copyOf(stages);
        // NoN's service reorders bound actor/role pairs before re-entering the controller.
        // Keep the captured input for restoration, but compare the same canonical bindings.
        var ordered=ActorRoleBindings.sort(actors,keys,Entity::getId);
        this.orderedActors=ordered.actors().stream().map(Entity::getUuid).toList();
        this.orderedKeys=keys!=null&&!keys.isEmpty()&&keys.size()!=actors.size()
            ?this.keys:List.copyOf(ordered.actorKeys());
        this.requester=requester==null?null:requester.getUuid();this.anchor=anchor;
        this.damage=damage;this.ignoreAttackers=ignoreAttackers;this.context=context;this.debug=debug;this.multiple=multiple;
    }
    public <T>T observe(Supplier<T> action){
        var previous=CURRENT.get();CURRENT.set(this);
        try{return action.get();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    public static NativeStartIntent current(NonAnimationEvents.BeforeStartContext event){
        var intent=CURRENT.get();
        return intent!=null&&intent.world.equals(event.world().getRegistryKey())&&intent.animation.equals(event.animationId())
            &&new HashSet<>(intent.actors).equals(new HashSet<>(event.actorUuids()))?intent:null;
    }
    /** Proven NoN mob-driven attack only; manual/PvP/pregnancy/unknown and continuations pass through. */
    public boolean autonomous(){
        return context!=null&&!context.isJoinReplacement()&&!context.queueContinuation()
            &&context.sessionData() instanceof NonAnimationSessionData data
            &&data.isAttack()&&data.playerAttack()==null&&data.pregnancy()==null;
    }
    /** One matching callback; unrelated nested starts and other listeners retain their own checks. */
    public boolean consumeMatchingResume(){
        var saved=RESUMING.get();
        if(saved==null||!world.equals(saved.world)||!animation.equals(saved.animation)
            ||!orderedActors.equals(saved.orderedActors)||!orderedKeys.equals(saved.orderedKeys)
            ||!stages.equals(saved.stages)||context!=saved.context)return false;
        RESUMING.remove();return true;
    }
    public UUID resume(ServerWorld target,ServerPlayerEntity player,MobEntity creature){
        if(!world.equals(target.getRegistryKey())||!actors.contains(player.getUuid())||!actors.contains(creature.getUuid()))return null;
        var entities=new ArrayList<Entity>();
        for(UUID id:actors){var entity=target.getEntity(id);if(entity==null||!entity.isAlive())return null;entities.add(entity);}
        var originalRequester=requester==null?null:target.getServer().getPlayerManager().getPlayer(requester);
        if(requester!=null&&(originalRequester==null||originalRequester.getEntityWorld()!=target))return null;
        if(anchor!=null&&target.getEntity(anchor)==null)return null;
        var previous=RESUMING.get();RESUMING.set(this);
        try{
            // Re-enter NoN's ordinary validation with the original definition, roles and context.
            return AnimationService.startAnimationNowWithContext(target,animation,entities,keys,stages,
                new AnimationService.StartOptions(damage,ignoreAttackers,anchor==null?null:target.getEntity(anchor),
                        originalRequester,context.addonMetadata(),debug,multiple),context);
        }finally{if(previous==null)RESUMING.remove();else RESUMING.set(previous);}
    }
}
