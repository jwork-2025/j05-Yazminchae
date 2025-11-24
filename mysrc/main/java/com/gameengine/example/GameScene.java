package com.gameengine.example;

import com.gameengine.components.*;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.core.GameLogic;
import com.gameengine.graphics.Renderer;
import com.gameengine.math.Vector2;
import com.gameengine.recording.FileRecordingStorage;
import com.gameengine.recording.RecordingService;
import com.gameengine.scene.Scene;

import java.util.Random;

/**
 * 可切换回主菜单的游戏场景（ESC 返回）
 */
public class GameScene extends Scene {
    private final GameEngine engine;
    private Renderer renderer;
    private Random random;
    private float time;
    private GameLogic gameLogic;
    private RecordingService recordingService;
    private float recordAccumulator;

    public GameScene(GameEngine engine) {
        super("GameScene");
        this.engine = engine;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.random = new Random();
        this.time = 0;
        this.gameLogic = new GameLogic(this);
        try { recordingService = new RecordingService(new FileRecordingStorage()); } catch (Exception e) { recordingService = null; }
        this.recordAccumulator = 0f;

        createPlayer();
        createEnemies();
        createDecorations();
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        time += deltaTime;

        com.gameengine.input.InputManager input = com.gameengine.input.InputManager.getInstance();

        // ESC 回到主菜单
        if (input.isKeyJustPressed(27)) {
            engine.setScene(new MenuScene(engine));
            return;
        }

        gameLogic.handlePlayerInput();
        gameLogic.updatePhysics();
        gameLogic.checkCollisions();

        if (time > 2.0f) { createEnemy(); time = 0; }

        // R 录制开关
        if (input.isKeyJustPressed(82) && recordingService != null) {
            try {
                if (!recordingService.isRecording()) {
                    String name = "session_" + System.currentTimeMillis();
                    recordingService.startRecording(name, 800, 600);
                    System.out.println("开始录制: " + name);
                } else {
                    recordingService.stopRecording();
                    System.out.println("结束录制");
                }
            } catch (Exception ex) { }
        }

        if (recordingService != null && recordingService.isRecording()) {
            // 更新录制服务，提高录制频率
            recordingService.update(deltaTime);
            // 每帧都录制，确保最高流畅度
            recordingService.writeKeyframe(this);
        }
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, 800, 600, 0.1f, 0.1f, 0.2f, 1.0f);
        super.render();
    }

    // --- helper creators copied from original example ---
    private void createPlayer() {
        final Scene outer = this;
        GameObject player = new GameObject("Player") {
            private Vector2 basePosition;
            private com.gameengine.input.InputManager input = com.gameengine.input.InputManager.getInstance();
            private final float rotationOffset = (float)(Math.PI / 2.0);

            @Override public void update(float deltaTime) { super.update(deltaTime); updateComponents(deltaTime); updateBodyParts();
                if (input.isKeyJustPressed(32)) {
                    TransformComponent transform = getComponent(TransformComponent.class);
                    if (transform == null) return;
                    Vector2 headCenter = transform.getPosition(); headCenter.y -= 16;
                    float angle = transform.getRotation();
                    Vector2 dir = new Vector2((float)Math.cos(angle), (float)Math.sin(angle));
                    GameObject bullet = new GameObject("Bullet") { @Override public void update(float dt){ super.update(dt); updateComponents(dt); } @Override public void render(){ renderComponents(); } };
                    bullet.addComponent(new TransformComponent(new Vector2(headCenter)));
                    PhysicsComponent bPhys = bullet.addComponent(new PhysicsComponent(0.1f));
                    bPhys.setVelocity(dir.multiply(400f)); bPhys.setFriction(0.999f);
                    RenderComponent bRender = bullet.addComponent(new RenderComponent(RenderComponent.RenderType.CIRCLE, new Vector2(10,10), new RenderComponent.Color(1f,1f,1f,1f)));
                    bRender.setRenderer(renderer);
                    outer.addGameObject(bullet);
                }
            }
            @Override public void render(){ renderBodyParts(); }
            private void updateBodyParts(){ TransformComponent t = getComponent(TransformComponent.class); if (t != null) basePosition = t.getPosition(); }
            private void renderBodyParts(){ if (basePosition == null) return; TransformComponent transform = getComponent(TransformComponent.class); float angle = transform!=null?transform.getRotation()+rotationOffset:0f; float cx=basePosition.x, cy=basePosition.y; float cos=(float)Math.cos(angle), sin=(float)Math.sin(angle);
                float rx = 0f * cos - 0f * sin; float ry = 0f * sin + 0f * cos;
                if (Math.abs(cos) < Math.abs(sin)) renderer.drawRect(cx + rx - 10, cy + ry - 8, 20, 16, 1f,0f,0f,1f);
                else renderer.drawRect(cx + rx - 8, cy + ry - 10, 16, 20, 1f,0f,0f,1f);
                rx = 0f * cos - (-16f) * sin; ry = 0f * sin + (-16f) * cos; renderer.drawRect(cx + rx - 6, cy + ry - 6, 12, 12, 1f,0.5f,0f,1f);
                float armW, armH; if (Math.abs(cos) < Math.abs(sin)){ armW=12f; armH=6f; } else { armW=6f; armH=12f; }
                rx = -10f * cos - 1f * sin; ry = -10f * sin + 1f * cos; renderer.drawRect(cx + rx - armW/2, cy + ry - armH/2, armW, armH, 1f,0.8f,0f,1f);
                rx = 10f * cos - 1f * sin; ry = 10f * sin + 1f * cos; renderer.drawRect(cx + rx - armW/2, cy + ry - armH/2, armW, armH, 0f,1f,0f,1f);
            }
        };
        player.addComponent(new TransformComponent(new Vector2(400,300)));
        PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f)); physics.setFriction(0.95f);
        addGameObject(player);
    }

    private void createEnemies(){ for (int i=0;i<3;i++) createEnemy(); }

    private void createEnemy(){ GameObject enemy = new GameObject("Enemy"){ @Override public void update(float dt){ super.update(dt); updateComponents(dt);} @Override public void render(){ renderComponents(); } };
        Vector2 position = new Vector2(random.nextFloat()*800, random.nextFloat()*600);
        enemy.addComponent(new TransformComponent(position));
        RenderComponent render = enemy.addComponent(new RenderComponent(RenderComponent.RenderType.RECTANGLE, new Vector2(20,20), new RenderComponent.Color(1f,0.5f,0f,1f)));
        render.setRenderer(renderer);
        PhysicsComponent physics = enemy.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(new Vector2((random.nextFloat()-0.5f)*100, (random.nextFloat()-0.5f)*100));
        physics.setFriction(0.98f);
        addGameObject(enemy);
    }

    private void createDecorations(){ for (int i=0;i<5;i++) createDecoration(); }

    private void createDecoration(){ GameObject decoration = new GameObject("Decoration"){ @Override public void update(float dt){ super.update(dt); updateComponents(dt);} @Override public void render(){ renderComponents(); } };
        Vector2 position = new Vector2(random.nextFloat()*800, random.nextFloat()*600);
        decoration.addComponent(new TransformComponent(position));
        RenderComponent render = decoration.addComponent(new RenderComponent(RenderComponent.RenderType.CIRCLE, new Vector2(5,5), new RenderComponent.Color(0.5f,0.5f,1f,0.8f)));
        render.setRenderer(renderer);
        addGameObject(decoration);
    }
}