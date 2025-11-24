package com.gameengine.recording;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileRecordingStorage implements RecordingStorage {
    private BufferedWriter writer;
    private File outFile;

    @Override
    public void openForWrite(String name) throws IOException {
        File dir = new File("recordings");
        if (!dir.exists()) dir.mkdirs();
        outFile = new File(dir, name + ".jsonl");
        writer = new BufferedWriter(new FileWriter(outFile, false));
    }

    @Override
    public void writeLine(String line) throws IOException {
        if (writer == null) throw new IOException("Storage not opened");
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
