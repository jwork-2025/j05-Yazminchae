package com.gameengine.recording;

import com.gameengine.scene.Scene;
import com.gameengine.core.GameObject;
import com.gameengine.components.TransformComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.math.Vector2;

import java.io.IOException;
import java.util.List;

/**
 * 负责写入录制流（基于简单 JSONL + 便于解析的 KF 行）
 */
public class RecordingService {
    private RecordingStorage storage;
    private boolean recording = false;
    private long startTimeMs;
    private float recordAccumulator = 0f;

    public RecordingService(RecordingStorage storage) {
        this.storage = storage;
    }

    public void startRecording(String name, int windowW, int windowH) throws IOException {
        storage.openForWrite(name);
        startTimeMs = System.currentTimeMillis();
        String header = String.format("{\"type\":\"header\",\"time\":%d,\"w\":%d,\"h\":%d}", startTimeMs, windowW, windowH);
        storage.writeLine(header);
        recording = true;
        recordAccumulator = 0f;
    }

    public void stopRecording() throws IOException {
        if (!recording) return;
        storage.writeLine("{\"type\":\"end\"}");
        storage.close();
        recording = false;
    }

    public boolean isRecording() { return recording; }

    public void recordInputEvent(int keyCode) {
        if (!recording) return;
        long t = System.currentTimeMillis() - startTimeMs;
        String line = String.format("{\"type\":\"input\",\"time\":%d,\"key\":%d}", t, keyCode);
        try { storage.writeLine(line); } catch (IOException e) { }
    }

    /**
     * 更新录制状态，提高录制频率以获得更流畅的回放
     */
    public void update(float deltaTime) {
        if (!recording) return;
        
        recordAccumulator += deltaTime;
        // 提高到每秒30帧的录制频率
        if (recordAccumulator >= 0.033f) { // 约30fps
            recordAccumulator = 0f;
        }
    }

    /**
     * 写入当前场景的关键帧（提高录制频率）
     */
    public void writeKeyframe(Scene scene) {
        if (!recording) return;
        long t = System.currentTimeMillis() - startTimeMs;
        StringBuilder sb = new StringBuilder();
        sb.append("KF|").append(t).append("|");
        List<GameObject> objects = scene.getGameObjects();
        boolean first = true;
        
        for (GameObject obj : objects) {
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc == null) continue;
            
            if (!first) sb.append(";");
            first = false;
            
            String nm = obj.getName();
            Vector2 pos = tc.getPosition();
            float rot = tc.getRotation();
            float w = 20, h = 20;
            float r = 1, g = 1, b = 1, a = 1;
            String rt = "CUSTOM";
            
            RenderComponent rc = obj.getComponent(RenderComponent.class);
            if (rc != null) {
                Vector2 size = rc.getSize();
                if (size != null) {
                    w = size.x; 
                    h = size.y;
                }
                RenderComponent.Color c = rc.getColor();
                if (c != null) { 
                    r = c.r; g = c.g; b = c.b; a = c.a; 
                }
                rt = rc.getRenderType().name();
            }
            
            if ("Player".equals(nm)) {
                rt = "PLAYER";
            } else if ("Bullet".equals(nm)) {
                rt = "BULLET";
            } else if ("Enemy".equals(nm)) {
                rt = "ENEMY";
            } else if ("Decoration".equals(nm)) {
                rt = "DECORATION";
            }
            
            sb.append("name=").append(nm);
            sb.append(",type=").append(rt);
            sb.append(",x=").append(String.format("%.2f", pos.x));
            sb.append(",y=").append(String.format("%.2f", pos.y));
            sb.append(",rot=").append(String.format("%.2f", rot));
            sb.append(",w=").append(String.format("%.2f", w));
            sb.append(",h=").append(String.format("%.2f", h));
            sb.append(",c=").append(String.format("%.2f", r))
              .append(",").append(String.format("%.2f", g))
              .append(",").append(String.format("%.2f", b))
              .append(",").append(String.format("%.2f", a));
        }
        
        try { 
            storage.writeLine(sb.toString()); 
        } catch (IOException e) { 
        }
    }
}