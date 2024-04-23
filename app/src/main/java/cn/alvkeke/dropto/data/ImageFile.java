package cn.alvkeke.dropto.data;

import androidx.annotation.NonNull;

import java.io.File;

public class ImageFile {

    private final File md5file;
    private String name;

    public ImageFile(@NonNull File folder, String md5, String name) {
        this.md5file = new File(folder, md5);
        this.name = name;
    }

    public ImageFile(@NonNull File md5File, @NonNull String imgName) {
        this.md5file = md5File;
        this.name = imgName;
    }

    public File getMd5file() {
        return md5file;
    }

    public String getMd5() {
        return md5file.getName();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public static ImageFile from(File md5file, String name) {
        if (md5file == null) return null;
        return new ImageFile(md5file, name==null? "" : name);
    }

    public static ImageFile from(File folder, String md5, String name) {
        if (folder == null) return null;
        return new ImageFile(folder, md5, name);
    }

}
