package com.gameengine.example;

import com.gameengine.scene.Scene;
import com.gameengine.graphics.Renderer;
import com.gameengine.core.GameObject;
import com.gameengine.components.TransformComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.math.Vector2;
import com.gameengine.core.GameEngine;
import com.gameengine.input.InputManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * 简单回放场景：解析 RecordingService 输出的 KF 行并插值播放
 */
public class ReplayScene extends Scene {
    private String filePath;
    private Renderer renderer;
    private final GameEngine engine;
    private List<Frame> frames = new ArrayList<>();
    private long playbackStartMs = -1;
    private int currentIndex = 0;
    private List<GameObject> spawned = new ArrayList<>();
    private Map<String, GameObject> objectIdMap = new HashMap<>();
    private boolean initialized = false;
    
    // 对象池，避免频繁创建销毁对象
    private Map<String, List<GameObject>> objectPool = new HashMap<>();

    public ReplayScene(GameEngine engine, String filePath) {
        super("Replay");
        this.engine = engine;
        this.filePath = filePath;
        this.renderer = engine.getRenderer();
    }

    @Override
    public void initialize() {
        super.initialize();
        loadFile();
        
        if (frames.isEmpty()) {
            return;
        }
        
        playbackStartMs = System.currentTimeMillis();
        currentIndex = 0;
        
        if (!frames.isEmpty()) {
            syncObjectsToFrame(frames.get(0));
        }
        
        initialized = true;
    }

    /**
     * 同步对象到指定帧的状态
     */
    private void syncObjectsToFrame(Frame frame) {
        if (frame == null || frame.objects.isEmpty()) {
            clearAllObjects();
            return;
        }

        // 构建当前帧的对象ID集合
        Set<String> frameObjectIds = new HashSet<>();
        for (Frame.ObjInfo oi : frame.objects) {
            String objectId = generateStableObjectId(oi);
            frameObjectIds.add(objectId);
        }

        // 移除不存在于当前帧的对象
        List<GameObject> toRemove = new ArrayList<>();
        for (GameObject obj : new ArrayList<>(spawned)) {
            String objId = getObjectId(obj);
            if (!frameObjectIds.contains(objId)) {
                toRemove.add(obj);
                objectIdMap.remove(objId);
            }
        }
        
        for (GameObject obj : toRemove) {
            spawned.remove(obj);
            removeGameObjectFromScene(obj);
        }

        // 更新或创建对象
        for (Frame.ObjInfo oi : frame.objects) {
            String objectId = generateStableObjectId(oi);
            
            GameObject existingObj = objectIdMap.get(objectId);
            if (existingObj == null) {
                // 创建新对象
                GameObject go = createReplayObject(oi);
                if (go != null) {
                    setupObjectTransform(go, oi);
                    addGameObject(go);
                    spawned.add(go);
                    objectIdMap.put(objectId, go);
                }
            } else {
                // 更新现有对象
                setupObjectTransform(existingObj, oi);
            }
        }
    }

    /**
     * 清空所有对象
     */
    private void clearAllObjects() {
        for (GameObject obj : new ArrayList<>(spawned)) {
            removeGameObjectFromScene(obj);
        }
        spawned.clear();
        objectIdMap.clear();
    }

    /**
     * 从场景中移除游戏对象
     */
    private void removeGameObjectFromScene(GameObject obj) {
        // 先将对象回收到对象池
        String type = determineObjectType(obj);
        returnObjectToPool(obj, type);
        
        // 从场景中移除
        List<GameObject> gameObjects = getGameObjects();
        gameObjects.remove(obj);
    }

    /**
     * 生成更稳定的对象ID（避免因微小位置变化导致ID不同）
     */
    private String generateStableObjectId(Frame.ObjInfo oi) {
        // 使用名称和类型作为ID，忽略微小位置差异
        return oi.name + "_" + oi.type + "_" + (int)(oi.x / 10) + "_" + (int)(oi.y / 10);
    }

    /**
     * 获取游戏对象的ID
     */
    private String getObjectId(GameObject obj) {
        for (Map.Entry<String, GameObject> entry : objectIdMap.entrySet()) {
            if (entry.getValue() == obj) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 设置对象变换
     */
    private void setupObjectTransform(GameObject obj, Frame.ObjInfo oi) {
        TransformComponent tc = obj.getComponent(TransformComponent.class);
        if (tc != null) {
            tc.setPosition(new Vector2(oi.x, oi.y));
            tc.setRotation(oi.rot);
        }
        
        // 更新渲染属性（如果变化不大可以省略以提高性能）
        RenderComponent rc = obj.getComponent(RenderComponent.class);
        if (rc != null && (oi.w > 0 && oi.h > 0)) {
            if (Math.abs(oi.w - rc.getSize().x) > 0.1f || Math.abs(oi.h - rc.getSize().y) > 0.1f) {
                rc.setSize(new Vector2(oi.w, oi.h));
            }
        }
    }

    /**
     * 为回放创建正确的游戏对象
     */
    private GameObject createReplayObject(Frame.ObjInfo oi) {
        String objectType = determineObjectTypeFromInfo(oi);
        
        // 首先尝试从对象池获取
        GameObject pooledObj = getObjectFromPool(objectType);
        if (pooledObj != null) {
            // 重置对象状态
            pooledObj.setActive(true);
            TransformComponent tc = pooledObj.getComponent(TransformComponent.class);
            if (tc != null) {
                tc.setPosition(new Vector2(oi.x, oi.y));
                tc.setRotation(oi.rot);
            }
            return pooledObj;
        }
        
        // 对象池中没有，创建新对象
        if ("Player".equals(oi.name) || "PLAYER".equals(oi.type)) {
            return createPlayerReplayObject();
        } else if ("Bullet".equals(oi.name) || "BULLET".equals(oi.type)) {
            return createBulletReplayObject();
        } else if ("Enemy".equals(oi.name) || "ENEMY".equals(oi.type)) {
            return createEnemyReplayObject();
        } else if ("Decoration".equals(oi.name) || "DECORATION".equals(oi.type)) {
            return createDecorationReplayObject();
        } else {
            GameObject go = EntityFactory.create(oi.name, renderer);
            RenderComponent rc = go.getComponent(RenderComponent.class);
            if (rc != null) {
                if (oi.w > 0 && oi.h > 0) {
                    rc.setSize(new Vector2(oi.w, oi.h));
                }
                rc.setColor(oi.r, oi.g, oi.b, oi.a);
            }
            return go;
        }
    }

    /**
     * 从对象信息确定对象类型
     */
    private String determineObjectTypeFromInfo(Frame.ObjInfo oi) {
        if ("Player".equals(oi.name) || "PLAYER".equals(oi.type)) {
            return "Player";
        } else if ("Bullet".equals(oi.name) || "BULLET".equals(oi.type)) {
            return "Bullet";
        } else if ("Enemy".equals(oi.name) || "ENEMY".equals(oi.type)) {
            return "Enemy";
        } else if ("Decoration".equals(oi.name) || "DECORATION".equals(oi.type)) {
            return "Decoration";
        } else {
            return "Generic";
        }
    }

    /**
     * 从游戏对象确定类型
     */
    private String determineObjectType(GameObject obj) {
        String name = obj.getName();
        if ("Player".equals(name)) {
            return "Player";
        } else if ("Bullet".equals(name)) {
            return "Bullet";
        } else if ("Enemy".equals(name)) {
            return "Enemy";
        } else if ("Decoration".equals(name)) {
            return "Decoration";
        } else {
            return "Generic";
        }
    }

    /**
     * 从对象池获取对象
     */
    private GameObject getObjectFromPool(String type) {
        List<GameObject> pool = objectPool.get(type);
        if (pool != null && !pool.isEmpty()) {
            GameObject obj = pool.remove(pool.size() - 1);
            obj.setActive(true);
            return obj;
        }
        return null;
    }

    /**
     * 将对象回收到对象池
     */
    private void returnObjectToPool(GameObject obj, String type) {
        obj.setActive(false);
        List<GameObject> pool = objectPool.computeIfAbsent(type, k -> new ArrayList<>());
        pool.add(obj);
    }

    /**
     * 创建玩家回放对象（使用自定义渲染）
     */
    private GameObject createPlayerReplayObject() {
        final Scene outer = this;
        GameObject player = new GameObject("Player") {
            private Vector2 basePosition;
            private final float rotationOffset = (float)(Math.PI / 2.0);

            @Override 
            public void update(float deltaTime) { 
                super.update(deltaTime); 
                updateComponents(deltaTime); 
                updateBodyParts();
            }
            
            @Override 
            public void render(){ 
                renderBodyParts(); 
            }
            
            private void updateBodyParts(){ 
                TransformComponent t = getComponent(TransformComponent.class); 
                if (t != null) basePosition = t.getPosition(); 
            }
            
            private void renderBodyParts(){ 
                if (basePosition == null) return; 
                TransformComponent transform = getComponent(TransformComponent.class); 
                float angle = transform != null ? transform.getRotation() + rotationOffset : 0f; 
                float cx = basePosition.x, cy = basePosition.y; 
                float cos = (float)Math.cos(angle), sin = (float)Math.sin(angle);
                
                float rx = 0f * cos - 0f * sin; 
                float ry = 0f * sin + 0f * cos;
                if (Math.abs(cos) < Math.abs(sin)) 
                    renderer.drawRect(cx + rx - 10, cy + ry - 8, 20, 16, 1f, 0f, 0f, 1f);
                else 
                    renderer.drawRect(cx + rx - 8, cy + ry - 10, 16, 20, 1f, 0f, 0f, 1f);
                
                rx = 0f * cos - (-16f) * sin; 
                ry = 0f * sin + (-16f) * cos; 
                renderer.drawRect(cx + rx - 6, cy + ry - 6, 12, 12, 1f, 0.5f, 0f, 1f);
                
                float armW, armH; 
                if (Math.abs(cos) < Math.abs(sin)) { 
                    armW = 12f; armH = 6f; 
                } else { 
                    armW = 6f; armH = 12f; 
                }
                rx = -10f * cos - 1f * sin; 
                ry = -10f * sin + 1f * cos; 
                renderer.drawRect(cx + rx - armW/2, cy + ry - armH/2, armW, armH, 1f, 0.8f, 0f, 1f);
                
                rx = 10f * cos - 1f * sin; 
                ry = 10f * sin + 1f * cos; 
                renderer.drawRect(cx + rx - armW/2, cy + ry - armH/2, armW, armH, 0f, 1f, 0f, 1f);
            }
        };
        player.addComponent(new TransformComponent());
        return player;
    }

    /**
     * 创建子弹回放对象
     */
    private GameObject createBulletReplayObject() {
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
        RenderComponent render = bullet.addComponent(new RenderComponent(
            RenderComponent.RenderType.CIRCLE, 
            new Vector2(10, 10), 
            new RenderComponent.Color(1f, 1f, 1f, 1f)
        ));
        render.setRenderer(renderer);
        return bullet;
    }

    /**
     * 创建敌人回放对象
     */
    private GameObject createEnemyReplayObject() {
        GameObject enemy = new GameObject("Enemy") { 
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
        enemy.addComponent(new TransformComponent());
        RenderComponent render = enemy.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE, 
            new Vector2(20, 20), 
            new RenderComponent.Color(1f, 0.5f, 0f, 1f)
        ));
        render.setRenderer(renderer);
        return enemy;
    }

    /**
     * 创建装饰物回放对象
     */
    private GameObject createDecorationReplayObject() {
        GameObject decoration = new GameObject("Decoration") { 
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
        decoration.addComponent(new TransformComponent());
        RenderComponent render = decoration.addComponent(new RenderComponent(
            RenderComponent.RenderType.CIRCLE, 
            new Vector2(6, 6), 
            new RenderComponent.Color(0.5f, 0.5f, 1f, 0.8f)
        ));
        render.setRenderer(renderer);
        return decoration;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        
        if (!initialized || frames.isEmpty()) {
            return;
        }
        
        InputManager input = InputManager.getInstance();
        if (input.isKeyJustPressed(27)) {
            engine.setScene(new MenuScene(engine));
            return;
        }
        
        long t = System.currentTimeMillis() - playbackStartMs;
        
        // 更精确的帧查找和插值
        findCurrentFrameIndex(t);
        
        // 应用平滑插值
        applySmoothInterpolation(t);
        
        // 循环播放
        if (currentIndex >= frames.size() - 1 && t > frames.get(frames.size() - 1).time + 1000) {
            playbackStartMs = System.currentTimeMillis();
            currentIndex = 0;
            // 重置对象状态
            syncObjectsToFrame(frames.get(0));
        }
    }
    
    /**
     * 查找当前帧索引
     */
    private void findCurrentFrameIndex(long currentTime) {
        // 如果时间已经超过所有帧，停留在最后一帧
        if (currentTime >= frames.get(frames.size() - 1).time) {
            currentIndex = frames.size() - 1;
            return;
        }
        
        // 从当前帧开始向后查找（优化性能）
        for (int i = Math.max(0, currentIndex); i < frames.size() - 1; i++) {
            if (currentTime >= frames.get(i).time && currentTime < frames.get(i + 1).time) {
                currentIndex = i;
                return;
            }
        }
        
        // 如果没找到，向前查找
        for (int i = Math.min(currentIndex, frames.size() - 2); i >= 0; i--) {
            if (currentTime >= frames.get(i).time && currentTime < frames.get(i + 1).time) {
                currentIndex = i;
                return;
            }
        }
        
        // 默认使用第一帧
        currentIndex = 0;
    }
    
    /**
     * 应用平滑插值
     */
    private void applySmoothInterpolation(long currentTime) {
        if (currentIndex >= frames.size() - 1) {
            // 最后一帧，直接同步（不插值）
            syncObjectsToFrame(frames.get(currentIndex));
            return;
        }
        
        Frame a = frames.get(currentIndex);
        Frame b = frames.get(currentIndex + 1);
        
        if (a.time >= b.time || a.objects.isEmpty()) {
            syncObjectsToFrame(a);
            return;
        }
        
        float span = b.time - a.time;
        if (span <= 0) {
            syncObjectsToFrame(a);
            return;
        }
        
        float local = (float)(currentTime - a.time) / span;
        local = Math.max(0, Math.min(1, local));
        
        // 使用更平滑的缓动函数
        float easedLocal = smoothStep(local);
        
        // 先同步到基准帧A
        syncObjectsToFrame(a);
        
        // 然后对每个对象应用插值
        for (Frame.ObjInfo oa : a.objects) {
            String objectId = generateStableObjectId(oa);
            GameObject obj = objectIdMap.get(objectId);
            
            if (obj != null) {
                Frame.ObjInfo ob = findObjectInFrame(b, objectId);
                if (ob != null) {
                    applyObjectInterpolation(obj, oa, ob, easedLocal);
                }
            }
        }
    }
    
    /**
     * 更平滑的插值函数
     */
    private float smoothStep(float x) {
        return x * x * (3 - 2 * x);
    }
    
    /**
     * 应用对象插值
     */
    private void applyObjectInterpolation(GameObject obj, Frame.ObjInfo from, Frame.ObjInfo to, float t) {
        TransformComponent tc = obj.getComponent(TransformComponent.class);
        if (tc != null) {
            float x = from.x + (to.x - from.x) * t;
            float y = from.y + (to.y - from.y) * t;
            
            // 角度插值（处理角度环绕）
            float rot = interpolateRotation(from.rot, to.rot, t);
            
            tc.setPosition(new Vector2(x, y));
            tc.setRotation(rot);
        }
    }
    
    /**
     * 角度插值（处理360度环绕）
     */
    private float interpolateRotation(float from, float to, float t) {
        float diff = to - from;
        
        // 处理角度环绕，选择最短路径
        if (diff > Math.PI) {
            diff -= 2 * Math.PI;
        } else if (diff < -Math.PI) {
            diff += 2 * Math.PI;
        }
        
        return from + diff * t;
    }
    
    /**
     * 在帧中查找指定ID的对象
     */
    private Frame.ObjInfo findObjectInFrame(Frame frame, String objectId) {
        for (Frame.ObjInfo oi : frame.objects) {
            if (objectId.equals(generateStableObjectId(oi))) {
                return oi;
            }
        }
        return null;
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, 800, 600, 0.1f, 0.1f, 0.2f, 1.0f);
        super.render();
    }

    private void loadFile() {
        File f = new File(filePath);
        if (!f.exists()) {
            return;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("KF|")) {
                    Frame fr = parseKF(line);
                    if (fr != null) {
                        frames.add(fr);
                    }
                }
            }
        } catch (Exception e) { 
        }
    }

    private Frame parseKF(String line) {
        try {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) {
                return null;
            }
            
            long time = Long.parseLong(parts[1]);
            String rest = parts[2];
            String[] objs = rest.split(";");
            Frame f = new Frame();
            f.time = time;
            
            for (String o : objs) {
                if (o.trim().isEmpty()) continue;
                
                String[] kvs = o.split(",");
                Frame.ObjInfo oi = new Frame.ObjInfo();
                
                oi.name = "Unknown";
                oi.type = "UNKNOWN";
                oi.x = 0; oi.y = 0; oi.rot = 0;
                oi.w = 20; oi.h = 20;
                oi.r = 1; oi.g = 1; oi.b = 1; oi.a = 1;
                
                for (String kv : kvs) {
                    String[] pair = kv.split("=", 2);
                    if (pair.length < 2) continue;
                    
                    String k = pair[0].trim();
                    String v = pair[1].trim();
                    
                    try {
                        switch (k) {
                            case "name": oi.name = v; break;
                            case "type": oi.type = v; break;
                            case "x": oi.x = Float.parseFloat(v); break;
                            case "y": oi.y = Float.parseFloat(v); break;
                            case "rot": oi.rot = Float.parseFloat(v); break;
                            case "w": oi.w = Float.parseFloat(v); break;
                            case "h": oi.h = Float.parseFloat(v); break;
                            case "c": {
                                String[] comps = v.split(",");
                                if (comps.length >= 4) {
                                    oi.r = Float.parseFloat(comps[0]); 
                                    oi.g = Float.parseFloat(comps[1]); 
                                    oi.b = Float.parseFloat(comps[2]); 
                                    oi.a = Float.parseFloat(comps[3]);
                                }
                            } break;
                        }
                    } catch (NumberFormatException nfe) {
                    }
                }
                
                f.objects.add(oi);
            }
            return f;
            
        } catch (Exception e) { 
            return null; 
        }
    }

    private static class Frame {
        long time;
        List<ObjInfo> objects = new ArrayList<>();
        static class ObjInfo { 
            String name; 
            String type;
            float x, y, rot, w, h; 
            float r = 1, g = 1, b = 1, a = 1;
        }
    }
}