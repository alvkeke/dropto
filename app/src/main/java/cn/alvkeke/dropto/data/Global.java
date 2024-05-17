package cn.alvkeke.dropto.data;

import java.io.File;

public class Global {
    private static final Global _instance = new Global();
    private File fileStorage;

    private Global() {
    }

    public static Global getInstance() {
        return _instance;
    }

    public File getFileStoreFolder() {
        return fileStorage;
    }

    public void setFileStoreFolder(File fileStorage) {
        this.fileStorage = fileStorage;
    }

}
