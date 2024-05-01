package cn.alvkeke.dropto.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.ui.adapter.ImageListAdapter;

public class MgmtStorageFragment extends Fragment {

    private CheckBox cbImage;
    private CheckBox cbCache;
    private Button buttonClear;
    private ImageListAdapter imageListAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mgmt_storage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cbImage = view.findViewById(R.id.mgmt_storage_image);
        cbCache = view.findViewById(R.id.mgmt_storage_cache);
        buttonClear = view.findViewById(R.id.mgmt_storage_btn_clear);
        RecyclerView listFilename = view.findViewById(R.id.mgmt_storage_list_filename);

        buttonClear.setOnClickListener(new OnClearClick());

        Context context = requireContext();
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        listFilename.setLayoutManager(layoutManager);
        listFilename.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        imageListAdapter = new ImageListAdapter(context);
        listFilename.setAdapter(imageListAdapter);
        imageListAdapter.setItemClickListener(this::showImageAt);

        buttonClear.setOnClickListener(this::clearSelectedData);

        cbImage.setChecked(true);
        cbCache.setChecked(true);

        initFolders();
        new Thread(taskCalcCache).start();
        new Thread(taskCalcImage).start();
    }

    File folderImage;
    File folderCache;
    private void initFolders() {
        folderCache = this.requireActivity().getExternalFilesDir("share");
        folderImage = Global.getInstance().getFileStoreFolder();
    }

    private interface FolderIterator {
        void on(File file);
    }
    private void iterateFolder(File folder, FolderIterator iterator) {
        if (folder.isFile()) {
            iterator.on(folder);
            return;
        }
        LinkedList<File> folders = new LinkedList<>();
        folders.push(folder);

        while (true) {
            try {
                folder = folders.pop();
            } catch (NoSuchElementException e) {
                return;
            }

            File[] files = folder.listFiles();
            if (files == null)
                continue;

            for (File ff : files) {
                if (ff.isDirectory()) {
                    folders.push(ff);
                    continue;
                }
                iterator.on(ff);
            }
            iterator.on(folder);
        }
    }

    private void emptyFolder(File folder) {
        iterateFolder(folder, file -> {
            if (file == folder) return;
            boolean ret = file.delete();
            if (!ret) {
                Log.e(this.toString(), "failed to remove " + file.getAbsolutePath());
            }
        });
    }

    private void clearSelectedData(View ignored) {
        buttonClear.setEnabled(false);
        if (cbCache.isChecked()) {
            emptyFolder(folderCache);
            try {
                new Thread(taskCalcCache).start();
            } catch (Exception e) {
                Log.e(this.toString(), "Failed to start thread to calc cache size" + e);
            }
        }
        if (cbImage.isChecked()) {
            emptyFolder(folderImage);
            imageListAdapter.emptyList();
            try {
                new Thread(taskCalcImage).start();
            } catch (Exception e) {
                Log.e(this.toString(), "Failed to start thread to calc Images size" + e);
            }
        }
        buttonClear.setEnabled(true);
    }

    private void showImageAt(int index) {
        String name = imageListAdapter.get(index);
        if (name == null) return;

        Log.e(this.toString(), "list clicked at: " + name);
    }

    private final Handler handler = new Handler();
    private long sizeCache = 0;
    private long sizeImage = 0;
    private int getSizeType(long size) {
        if (size < 1000L)
            return 0;
        else if (size < 1000000L)
            return 1;
        else if (size < 1000000000L)
            return 2;
        else
            return 3;
    }
    private String getUnitString(int div) {
        switch (div) {
            case 0:
                return "B";
            case 1:
                return "KB";
            case 2:
                return "MB";
            default:
                return "GB";
        }
    }

    private int getDivider(int div) {
        switch (div) {
            case 0:
                return 1;
            case 1:
                return 1000;
            case 2:
                return 1000000;
            default:
                return 1000000000;
        }
    }
    private String getSizeString(long size) {
        int type = getSizeType(size);
        int divider = getDivider(type);
        return size/divider + getUnitString(type);
    }

    private void setCbCacheText() {
        String text = getResources().getString(R.string.string_cache_storage_usage_prompt);
        text += " " + getSizeString(sizeCache);
        cbCache.setText(text);
    }
    private void setCbImageText() {
        String text = getResources().getString(R.string.string_image_storage_usage_prompt);
        text += " " + getSizeString(sizeImage);
        cbImage.setText(text);
    }

    private final Runnable taskCalcCache = () -> {
        sizeCache = 0;
        if (folderCache == null) {
            handler.post(this::setCbCacheText);
            return;
        }
        iterateFolder(folderCache, file -> {
            sizeImage += file.length();
            handler.post(this::setCbCacheText);
        });
    };

    private final Runnable taskCalcImage = () -> {
        sizeImage = 0;
        if (folderImage == null) {
            handler.post(this::setCbImageText);
            return;
        }
        iterateFolder(folderImage, file -> {
            sizeImage += file.length();
            handler.post(() -> {
                if (file.isFile())
                    imageListAdapter.add(file.getName());
                setCbImageText();
            });
        });
    };

    private class OnClearClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Log.e(this.toString(), "check status: " +
                    cbCache.isChecked() + ", " + cbImage.isChecked());
        }
    }


}
