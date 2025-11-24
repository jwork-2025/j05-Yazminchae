package com.gameengine.example;

import com.gameengine.scene.Scene;
import com.gameengine.graphics.Renderer;
import com.gameengine.core.GameEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动菜单：开始游戏 / 回放选择 / 退出
 */
public class MenuScene extends Scene {
    private final GameEngine engine;
    private Renderer renderer;
    private List<String> recordings = new ArrayList<>();
    private int menuIndex = 0;
    private int replayIndex = 0;
    private boolean inReplayList = false;

    public MenuScene(GameEngine engine) {
        super("Menu");
        this.engine = engine;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        scanRecordings();
    }

    private void scanRecordings() {
        recordings.clear();
        File dir = new File("recordings");
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".jsonl"));
        if (files == null) return;
        for (File f : files) recordings.add(f.getAbsolutePath());
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        com.gameengine.input.InputManager input = com.gameengine.input.InputManager.getInstance();
        if (!inReplayList) {
            if (input.isKeyJustPressed(38) || input.isKeyJustPressed(87)) { menuIndex = Math.max(0, menuIndex-1); }
            if (input.isKeyJustPressed(40) || input.isKeyJustPressed(83)) { menuIndex = Math.min(2, menuIndex+1); }
            if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32)) {
                if (menuIndex == 0) { engine.setScene(new GameScene(engine)); }
                else if (menuIndex == 1) { inReplayList = true; replayIndex = 0; }
                else if (menuIndex == 2) { System.exit(0); }
            }
        } else {
            if (input.isKeyJustPressed(38) || input.isKeyJustPressed(87)) { replayIndex = Math.max(0, replayIndex-1); }
            if (input.isKeyJustPressed(40) || input.isKeyJustPressed(83)) { replayIndex = Math.min(recordings.size()-1, replayIndex+1); }
            if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32)) { 
                if (!recordings.isEmpty()) {
                    engine.setScene(new ReplayScene(engine, recordings.get(replayIndex)));
                }
            }
            if (input.isKeyJustPressed(27)) { inReplayList = false; }
        }
    }

    @Override
    public void render() {
        renderer.drawRect(0,0, engine.getRenderer().getWidth(), engine.getRenderer().getHeight(), 0.05f, 0.05f, 0.08f, 1f);
        renderer.drawText("Game Engine", 320, 80, 36f, 1f, 1f, 1f);
        renderer.drawText((menuIndex==0?"> ":"  ") + "Start Game", 100, 180, 20f, 1f,1f,1f);
        renderer.drawText((menuIndex==1?"> ":"  ") + "Replays", 100, 220, 20f, 1f,1f,1f);
        renderer.drawText((menuIndex==2?"> ":"  ") + "Quit", 100, 260, 20f, 1f,1f,1f);

        if (inReplayList) {
            renderer.drawText("Select replay (ESC to go back):", 100, 320, 18f, 1f,1f,1f);
            int y = 360;
            if (recordings.isEmpty()) renderer.drawText("(no recordings)", 120, y, 16f, 0.8f,0.8f,0.8f);
            for (int i=0;i<recordings.size();i++) {
                String name = new File(recordings.get(i)).getName();
                String prefix = (i==replayIndex)?"> ":"  ";
                renderer.drawText(prefix + name, 120, y + i*28, 16f, (i==replayIndex)?1f:0.9f, (i==replayIndex)?1f:0.9f, (i==replayIndex)?0.6f:0.9f);
            }
        }
    }
}
