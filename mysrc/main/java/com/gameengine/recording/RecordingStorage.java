package com.gameengine.recording;

import java.io.IOException;

public interface RecordingStorage {
    void openForWrite(String name) throws IOException;
    void writeLine(String line) throws IOException;
    void close() throws IOException;
}
