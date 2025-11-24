package com.gameengine.core;

import com.gameengine.components.TransformComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.core.GameObject;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 游戏逻辑类，处理具体的游戏规则
 */
public class GameLogic {
    private Scene scene;
    private InputManager inputManager;
    private ExecutorService physicsExecutor;
    
    private boolean profilingEnabled = false;
    private long physicsTimeAccum = 0L;
    private int physicsTicks = 0;
    
    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.physicsExecutor = Executors.newFixedThreadPool(threadCount);
    }
    //并行实现
    private void processAvoidanceForEnemy(List<GameObject> enemies, int index) {
        GameObject a = enemies.get(index);
        TransformComponent ta = a.getComponent(TransformComponent.class);
        PhysicsComponent pa = a.getComponent(PhysicsComponent.class);
        if (ta == null || pa == null) return;

        Vector2 myPos = ta.getPosition();
        Vector2 avoidance = new Vector2();

        for (int j = 0; j < enemies.size(); j++) {
            if (j == index) continue;
            GameObject b = enemies.get(j);
            TransformComponent tb = b.getComponent(TransformComponent.class);
            if (tb == null) continue;
            Vector2 otherPos = tb.getPosition();
            float dist = myPos.distance(otherPos);
            if (dist < 80 && dist > 0.01f) {
                Vector2 dir = myPos.subtract(otherPos).normalize();
                float strength = (80 - dist) / 80.0f;
                avoidance = avoidance.add(dir.multiply(strength * 50));
            }
        }

        if (avoidance.magnitude() > 0) {
            Vector2 currentVelocity = pa.getVelocity();
            float lerpFactor = 0.15f;
            Vector2 avoidanceDir = avoidance.normalize();
            float avoidanceStrength = Math.min(avoidance.magnitude(), 50f);

            Vector2 targetVelocity = currentVelocity.add(avoidanceDir.multiply(avoidanceStrength));
            Vector2 newVelocity = new Vector2(
                currentVelocity.x + (targetVelocity.x - currentVelocity.x) * lerpFactor,
                currentVelocity.y + (targetVelocity.y - currentVelocity.y) * lerpFactor
            );

            float maxSpeed = 150f;
            if (newVelocity.magnitude() > maxSpeed) {
                newVelocity = newVelocity.normalize().multiply(maxSpeed);
            }

            pa.setVelocity(newVelocity);
        }
    }

    private void updateSinglePhysics(PhysicsComponent physics) {
        if (physics == null || physics.getOwner() == null) return;
        TransformComponent transform = physics.getOwner().getComponent(TransformComponent.class);
        if (transform != null) {
            Vector2 pos = transform.getPosition();
            Vector2 velocity = physics.getVelocity();
            GameObject owner = physics.getOwner();
            boolean isBullet = owner != null && "Bullet".equals(owner.getName());

            if (isBullet) {
                if (pos.x <= 0 || pos.x >= 800 || pos.y <= 0 || pos.y >= 600) {
                    owner.destroy();
                    return;
                }
            } else {
                boolean velocityChanged = false;
                if (pos.x <= 0 || pos.x >= 800 - 15) {
                    velocity.x = -velocity.x;
                    velocityChanged = true;
                }
                if (pos.y <= 0 || pos.y >= 600 - 15) {
                    velocity.y = -velocity.y;
                    velocityChanged = true;
                }

                if (pos.x < 0) pos.x = 0;
                if (pos.y < 0) pos.y = 0;
                if (pos.x > 800 - 15) pos.x = 800 - 15;
                if (pos.y > 600 - 15) pos.y = 600 - 15;

                transform.setPosition(pos);
                if (velocityChanged) {
                    physics.setVelocity(velocity);
                }
            }
        }
        GameObject owner = physics.getOwner();
        if (owner != null && "Bullet".equals(owner.getName())) {
            TransformComponent t = owner.getComponent(TransformComponent.class);
            if (t != null) {
                Vector2 p = t.getPosition();
                if (p.x < -50 || p.y < -50 || p.x > 850 || p.y > 650) {
                    owner.destroy();
                }
            }
        }
    }

    public void cleanup() {
        if (physicsExecutor != null && !physicsExecutor.isShutdown()) {
            physicsExecutor.shutdown();
            try {
                if (!physicsExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    physicsExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                physicsExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void setProfilingEnabled(boolean enabled) {
        this.profilingEnabled = enabled;
        this.physicsTimeAccum = 0L;
        this.physicsTicks = 0;
    }

    public double getAveragePhysicsMs() {
        if (physicsTicks == 0) return -1;
        return (physicsTimeAccum / (double) physicsTicks) / 1_000_000.0;
    }
    
    /**
     * 处理玩家输入
     */
    public void handlePlayerInput() {
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;
        
        GameObject player = players.get(0);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        
        if (transform == null || physics == null) return;
        
        Vector2 movement = new Vector2();
        
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38)) { // W或上箭头
            movement.y -= 1;
        }
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40)) { // S或下箭头
            movement.y += 1;
        }
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37)) { // A或左箭头
            movement.x -= 1;
        }
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39)) { // D或右箭头
            movement.x += 1;
        }
        
        if (movement.magnitude() > 0) {
            float angle = (float)Math.atan2(movement.y, movement.x);
            transform.setRotation(angle);

            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }
        
        // 边界检查
        Vector2 pos = transform.getPosition();
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > 800 - 20) pos.x = 800 - 20;
        if (pos.y > 600 - 20) pos.y = 600 - 20;
        transform.setPosition(pos);
    }
    
    /**
     * 更新物理系统
     */
    public void updatePhysics() {
        long _profStart = profilingEnabled ? System.nanoTime() : 0L;
        List<GameObject> enemies = new ArrayList<>();
        for (GameObject obj : scene.getGameObjects()) {
            if ("Enemy".equals(obj.getName())) enemies.add(obj);
        }

        for (GameObject enemy : enemies) {
            TransformComponent t = enemy.getComponent(TransformComponent.class);
            PhysicsComponent p = enemy.getComponent(PhysicsComponent.class);
            if (t == null || p == null) continue;
            if (Math.random() < 0.02) {
                float angle = (float) (Math.random() * Math.PI * 2);
                float speed = 80 + (float) (Math.random() * 40);
                Vector2 v = new Vector2((float) Math.cos(angle), (float) Math.sin(angle)).multiply(speed);
                p.setVelocity(v);
            }
        }

        // 根据敌人数目选择串行或并行处理
        if (enemies.size() >= 10 && physicsExecutor != null) {
            int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            int batchSize = Math.max(1, enemies.size() / threadCount + 1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < enemies.size(); i += batchSize) {
                final int start = i;
                final int end = Math.min(i + batchSize, enemies.size());
                futures.add(physicsExecutor.submit(() -> {
                    for (int k = start; k < end; k++) {
                        processAvoidanceForEnemy(enemies, k);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception e) { e.printStackTrace(); }
            }
        } else {
            for (int i = 0; i < enemies.size(); i++) {
                processAvoidanceForEnemy(enemies, i);
            }
        }

        List<PhysicsComponent> physicsComponents = scene.getComponents(PhysicsComponent.class);
        if (physicsComponents.isEmpty()) {
            if (profilingEnabled) { physicsTimeAccum += System.nanoTime() - _profStart; physicsTicks++; }
            return;
        }

        if (physicsExecutor == null) {
            for (PhysicsComponent physics : physicsComponents) updateSinglePhysics(physics);
            if (profilingEnabled) { physicsTimeAccum += System.nanoTime() - _profStart; physicsTicks++; }
            return;
        }

        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int batchSize = Math.max(1, physicsComponents.size() / threadCount + 1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < physicsComponents.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, physicsComponents.size());
            futures.add(physicsExecutor.submit(() -> {
                for (int j = start; j < end; j++) {
                    updateSinglePhysics(physicsComponents.get(j));
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { e.printStackTrace(); }
        }

        if (profilingEnabled) { physicsTimeAccum += System.nanoTime() - _profStart; physicsTicks++; }
    }
    
    /**
     * 检查碰撞
     */
    public void checkCollisions() {
        // 直接查找玩家对象
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;
        
        GameObject player = players.get(0);
        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null) return;
        
        // 直接查找所有游戏对象，然后过滤出敌人
        for (GameObject obj : scene.getGameObjects()) {
            if (obj.getName().equals("Enemy")) {
                TransformComponent enemyTransform = obj.getComponent(TransformComponent.class);
                if (enemyTransform != null) {
                    float distance = playerTransform.getPosition().distance(enemyTransform.getPosition());
                    if (distance < 25) {
                        // 碰撞！重置玩家位置
                        playerTransform.setPosition(new Vector2(400, 300));
                        break;
                    }
                }
            }
        }

        List<GameObject> objects = scene.getGameObjects();
        List<GameObject> bullets = new java.util.ArrayList<>();
        List<GameObject> enemies = new java.util.ArrayList<>();
        for (GameObject o : objects) {
            if ("Bullet".equals(o.getName())) bullets.add(o);
            if ("Enemy".equals(o.getName())) enemies.add(o);
        }

        for (GameObject b : bullets) {
            TransformComponent bt = b.getComponent(TransformComponent.class);
            if (bt == null) continue;
            for (GameObject e : enemies) {
                TransformComponent et = e.getComponent(TransformComponent.class);
                if (et == null) continue;
                float d = bt.getPosition().distance(et.getPosition());
                if (d < 18) {
                    e.destroy();
                    b.destroy();
                    break;
                }
            }
        }
    }
}
