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
import android.widget.TextView;

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
        RecyclerView listFilename = view.findViewById(R.id.mgmt_storage_list_files);

        Context context = requireContext();
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        listFilename.setLayoutManager(layoutManager);
        listFilename.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        imageListAdapter = new ImageListAdapter(context);
        listFilename.setAdapter(imageListAdapter);
        imageListAdapter.setItemClickListener(new OnItemClickListener());

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
            new Thread(() -> {
                emptyFolder(folderCache);
                taskCalcCache.run();
            }).start();
        }
        if (cbImage.isChecked()) {
            new Thread(() -> {
                emptyFolder(folderImage);
                handler.post(() -> imageListAdapter.emptyList());
                taskCalcImage.run();
            }).start();
        }
        buttonClear.setEnabled(true);
    }

    private class OnItemClickListener implements ImageListAdapter.OnItemClickListener {

        @Override
        public void OnClick(int index) {
            String name = imageListAdapter.get(index);
            if (name == null) return;

            File imageFile = new File(folderImage, name);
            ImageViewerFragment fragment = new ImageViewerFragment();
            fragment.setImgFile(imageFile);
            fragment.show(getParentFragmentManager(), null);
        }

        @Override
        public boolean OnLongClick(int index) {
            String name = imageListAdapter.get(index);
            if (name == null) return false;
            File imageFile = new File(folderImage, name);
            long tmp = imageFile.length();
            if (imageFile.delete()) {
                sizeImage -= tmp;
                imageListAdapter.remove(index);
                setTextSizeString(cbImage, R.string.string_image_storage_usage_prompt, sizeImage);
            }
            return true;
        }
    }


    private final Handler handler = new Handler();
    private long sizeCache = 0;
    private long sizeImage = 0;
    private static int getSizeType(long size) {
        if (size < 1000L)
            return 0;
        else if (size < 1000000L)
            return 1;
        else if (size < 1000000000L)
            return 2;
        else
            return 3;
    }
    private static String getUnitString(int div) {
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
    private static int getDivider(int div) {
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
    private static String getSizeString(long size) {
        int type = getSizeType(size);
        int divider = getDivider(type);
        return size/divider + getUnitString(type);
    }

    private void setTextSizeString(TextView view, int str_id, long size) {
        String string = getResources().getString(str_id);
        string += " " + getSizeString(size);
        view.setText(string);
    }

    private final Runnable taskCalcCache = () -> {
        sizeCache = 0;
        if (folderCache == null) {
            handler.post(() -> setTextSizeString(cbCache,
                    R.string.string_cache_storage_usage_prompt, sizeCache));
            return;
        }
        iterateFolder(folderCache, file -> {
            sizeCache += file.length();
            handler.post(() -> setTextSizeString(cbCache,
                    R.string.string_cache_storage_usage_prompt, sizeCache));
        });
    };

    private final Runnable taskCalcImage = () -> {
        sizeImage = 0;
        if (folderImage == null) {
            handler.post(() -> setTextSizeString(cbImage,
                    R.string.string_image_storage_usage_prompt, sizeImage));
            return;
        }
        iterateFolder(folderImage, file -> {
            sizeImage += file.length();
            handler.post(() -> {
                if (file.isFile())
                    imageListAdapter.add(file.getName());
                setTextSizeString(cbImage, R.string.string_image_storage_usage_prompt, sizeImage);
            });
        });
    };

}
