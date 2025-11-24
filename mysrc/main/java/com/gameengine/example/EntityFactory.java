package com.gameengine.example;

import com.gameengine.core.GameObject;
import com.gameengine.components.TransformComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.graphics.Renderer;
import com.gameengine.math.Vector2;

/**
 * 简化的工厂，用于回放场景构造可视化对象
 */
public class EntityFactory {
    public static GameObject create(String name, Renderer renderer) {
        if ("Player".equals(name)) return createPlayer(renderer);
        if (name.startsWith("Enemy")) return createEnemy(renderer);
        if (name.startsWith("Decoration")) return createDecoration(renderer);
        if ("Bullet".equals(name)) return createBullet(renderer);
        // 默认
        return createGeneric(name, renderer);
    }

    private static GameObject createPlayer(Renderer renderer) {
        GameObject player = new GameObject("Player") {
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
            }
            @Override
            public void render() { renderComponents(); }
        };
        player.addComponent(new TransformComponent());
        RenderComponent rc = new RenderComponent(RenderComponent.RenderType.RECTANGLE, new Vector2(20,20), new RenderComponent.Color(0.2f,0.8f,0.2f,1f));
        rc.setRenderer(renderer);
        player.addComponent(rc);
        return player;
    }

    private static GameObject createEnemy(Renderer renderer) {
        GameObject e = new GameObject("Enemy"){
            @Override public void update(float deltaTime){ super.update(deltaTime); updateComponents(deltaTime); }
            @Override public void render(){ renderComponents(); }
        };
        e.addComponent(new TransformComponent());
        RenderComponent rc = new RenderComponent(RenderComponent.RenderType.RECTANGLE, new Vector2(20,20), new RenderComponent.Color(1.0f,0.5f,0.0f,1f));
        rc.setRenderer(renderer);
        e.addComponent(rc);
        return e;
    }

    private static GameObject createDecoration(Renderer renderer) {
        GameObject d = new GameObject("Decoration"){
            @Override public void update(float deltaTime){ super.update(deltaTime); updateComponents(deltaTime); }
            @Override public void render(){ renderComponents(); }
        };
        d.addComponent(new TransformComponent());
        RenderComponent rc = new RenderComponent(RenderComponent.RenderType.CIRCLE, new Vector2(6,6), new RenderComponent.Color(0.5f,0.5f,1f,0.8f));
        rc.setRenderer(renderer);
        d.addComponent(rc);
        return d;
    }

    private static GameObject createBullet(Renderer renderer) {
        GameObject bullet = new GameObject("Bullet") { 
            @Override 
            public void update(float dt){ 
                super.update(dt); 
                updateComponents(dt); 
            } 
            @Override 
            public void render(){ 
                renderComponents(); 
            } 
        };
        bullet.addComponent(new TransformComponent());
        RenderComponent rc = new RenderComponent(RenderComponent.RenderType.CIRCLE, new Vector2(10,10), new RenderComponent.Color(1f,1f,1f,1f));
        rc.setRenderer(renderer);
        bullet.addComponent(rc);
        return bullet;
    }

    private static GameObject createGeneric(String name, Renderer renderer) {
        GameObject o = new GameObject(name){ 
            @Override 
            public void update(float deltaTime){ 
                super.update(deltaTime); 
                updateComponents(deltaTime);
            } 
            @Override 
            public void render(){ 
                renderComponents(); 
            } 
        };
        o.addComponent(new TransformComponent());
        RenderComponent rc = new RenderComponent(RenderComponent.RenderType.RECTANGLE, new Vector2(12,12), new RenderComponent.Color(0.8f,0.8f,0.8f,1f));
        rc.setRenderer(renderer);
        o.addComponent(rc);
        return o;
    }
}