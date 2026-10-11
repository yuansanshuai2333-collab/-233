package com.nonid.affinity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;

/** Own render state and command buffers: broken preview renderers cannot poison the world/UI queue. */
public final class CompanionModelPreview {
    private CompanionModelPreview() {}
    public static void register(){SpecialGuiElementRegistry.register(ctx->new Renderer(ctx.vertexConsumers()));}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void draw(DrawContext context,LivingEntity entity,int x,int y,int width,int height,Consumer<RuntimeException> failure){
        draw(context,entity,x,y,width,height,0,failure);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void draw(DrawContext context,LivingEntity entity,int x,int y,int width,int height,float yaw,Consumer<RuntimeException> failure){
        float h=entity.getHeight(),w=entity.getWidth();
        if(!Float.isFinite(h)||!Float.isFinite(w)||h<=0||w<=0)throw new IllegalArgumentException("Invalid preview dimensions");
        EntityRenderer renderer=MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        EntityRenderState state=renderer.createRenderState();
        renderer.updateRenderState(entity,state,1f);
        if(!Float.isFinite(state.height)||!Float.isFinite(state.width))throw new IllegalArgumentException("Invalid render dimensions");
        state.light=15728880;state.shadowPieces.clear();state.displayName=null;
        if(state instanceof LivingEntityRenderState living){living.bodyYaw=180+yaw;living.relativeHeadYaw=0;living.pitch=0;}
        // Reserve room for side-on models and geometry extending beyond the collision box (heads/tails).
        float scale=Math.max(1,Math.min((height-6)/Math.max(.1f,h),(width-6)/Math.max(.1f,w*2f))*.75f);
        var clip=context.scissorStack.peekLast();
        context.state.addSpecialElement(new State(state,x,y,x+width,y+height,scale,clip,
                SpecialGuiElementRenderState.createBounds(x,y,x+width,y+height,clip),failure));
    }
    private record State(EntityRenderState entity,int x1,int y1,int x2,int y2,float scale,ScreenRect scissorArea,ScreenRect bounds,Consumer<RuntimeException> failure) implements SpecialGuiElementRenderState {}
    private static final class Resources implements AutoCloseable {
        final BufferAllocator buffer=new BufferAllocator(65536),overlay=new BufferAllocator(16384);
        final VertexConsumerProvider.Immediate vertices=VertexConsumerProvider.immediate(buffer),crumbling=VertexConsumerProvider.immediate(overlay);
        final OrderedRenderCommandQueueImpl queue=new OrderedRenderCommandQueueImpl();
        final RenderDispatcher dispatcher;
        Resources(){var c=MinecraftClient.getInstance();dispatcher=new RenderDispatcher(queue,c.getBlockRenderManager(),vertices,c.getAtlasManager(),new OutlineVertexConsumerProvider(),crumbling,c.textRenderer);}
        @Override public void close(){dispatcher.close();buffer.close();overlay.close();}
    }
    private static final class Renderer extends SpecialGuiElementRenderer<State> {
        private Resources resources;
        Renderer(VertexConsumerProvider.Immediate vertices){super(vertices);}
        @Override public Class<State> getElementClass(){return State.class;}
        @Override protected float getYOffset(int height,int scale){return height/2f;}
        @Override protected String getName(){return "natural_relationships_companion";}
        @Override protected void render(State state,MatrixStack matrices){
            var c=MinecraftClient.getInstance();var previousLights=RenderSystem.getShaderLights();
            // Third-party renderers only receive this private matrix stack and private command queue.
            var local=new MatrixStack();local.multiplyPositionMatrix(matrices.peek().getPositionMatrix());
            try {
                if(resources==null)resources=new Resources();
                c.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);
                local.translate(0,(state.y2-state.y1-6)/(2f*state.scale),0);local.multiply(new Quaternionf().rotationZ((float)Math.PI));
                var camera=new CameraRenderState();camera.orientation=new Quaternionf().rotationY((float)Math.PI);
                c.getEntityRenderDispatcher().render(state.entity,camera,0,0,0,local,resources.queue);
                resources.dispatcher.render();resources.vertices.draw();resources.crumbling.draw();resources.queue.clear();
            } catch(RuntimeException error){
                // Degrade only this explicitly optional preview; the callback logs once and shows the placeholder.
                if(resources!=null){resources.queue.clear();resources.close();resources=null;}
                state.failure.accept(error);
            } finally {RenderSystem.setShaderLights(previousLights);}
        }
        @Override public void close(){if(resources!=null){resources.close();resources=null;}super.close();}
    }
}

