package edu.plus.cs;

import java.io.File;
import java.io.OutputStream;

public class FileReference {
    File file;
    OutputStream outputStream;

    public FileReference(File file, OutputStream outputStream) {
        this.file = file;
        this.outputStream = outputStream;
    }

    public File getFile() {
        return file;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }
}
