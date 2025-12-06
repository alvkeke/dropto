package cn.alvkeke.dropto.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

public class FileHelper {

    private static final int BUFFER_SIZE = 1024;

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static byte[] calculateMD5(FileDescriptor fd) throws Exception{
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(fd)){
            fis.getChannel().position(0);
            byte[] buffer = new byte[BUFFER_SIZE];
            int n_bytes;
            while ((n_bytes = fis.read(buffer)) != -1) {
                md.update(buffer, 0, n_bytes);
            }
        }

        return md.digest();
    }

    public static File md5ToFile(File folder, byte[] md5) {
        String name = bytesToHex(md5);
        return new File(folder, name);
    }

    public static void copyFileTo(FileDescriptor fd, File dest) throws IOException {
        FileInputStream fis = new FileInputStream(fd);
        FileChannel srcChannel = fis.getChannel().position(0);
        FileOutputStream fos = new FileOutputStream(dest);
        FileChannel destChannel = fos.getChannel();
        // do data copy
        destChannel.transferFrom(srcChannel, 0, srcChannel.size());
        srcChannel.close();
        destChannel.close();
        fis.close();
        fos.close();
    }

    public static String getFileNameFromUri(Context context, Uri uri) {
        // ContentResolver to resolve the content Uri
        ContentResolver resolver = context.getContentResolver();
        // Query the file name from the content Uri
        Cursor cursor = resolver.query(uri, null, null, null, null);
        String fileName = null;
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index != -1) {
                fileName = cursor.getString(index);
            }
            cursor.close();
        }
        return fileName;
    }

    public static File saveUriToFile(Context context, Uri uri, File storeFolder) {
        if (storeFolder == null) {
            return null;
        }

        try (ParcelFileDescriptor inputPFD = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (inputPFD == null) {
                return null;
            }
            FileDescriptor fd = inputPFD.getFileDescriptor();
            byte[] md5sum = calculateMD5(fd);
            File retFile = md5ToFile(storeFolder, md5sum);
            if (!(retFile.isFile() && retFile.exists())) {
                copyFileTo(fd, retFile);
            }
            return retFile;
        } catch (Exception e) {
            return null;
        }
    }

}
