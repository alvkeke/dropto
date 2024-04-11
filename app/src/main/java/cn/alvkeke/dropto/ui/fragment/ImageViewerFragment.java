package cn.alvkeke.dropto.ui.fragment;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.storage.ImageLoader;


public class ImageViewerFragment extends Fragment {


    private ImageView imageView;
    private File imgFile;
    private Bitmap loadedBitmap = null;

    public ImageViewerFragment() {
    }

    public void setImgFile(File imgFile) {
        this.imgFile = imgFile;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (imgFile == null) {
            Toast.makeText(requireContext(), "no Image view, exit", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imageView = view.findViewById(R.id.img_viewer_image);
        imageView.setBackgroundColor(Color.BLACK);
        ImageLoader.getInstance().loadOriginalImageAsync(imgFile, bitmap -> {
            loadedBitmap = bitmap;
            imageView.setImageBitmap(bitmap);
        });

        view.setOnClickListener(view1 -> finish());
    }

    void finish() {
        getParentFragmentManager().beginTransaction()
                .remove(this).commit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (loadedBitmap != null) {
            loadedBitmap.recycle();
            loadedBitmap = null;
        }
    }
}