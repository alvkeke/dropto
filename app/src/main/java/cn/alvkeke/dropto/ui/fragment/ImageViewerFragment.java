package cn.alvkeke.dropto.ui.fragment;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import cn.alvkeke.dropto.ui.listener.GestureListener;


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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (imgFile == null) {
            Toast.makeText(requireContext(), "no Image view, exit", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        view.setBackgroundColor(Color.BLACK);
        imageView = view.findViewById(R.id.img_viewer_image);
        ImageLoader.getInstance().loadOriginalImageAsync(imgFile, bitmap -> {
            loadedBitmap = bitmap;
            imageView.setImageBitmap(bitmap);
        });
        view.setOnTouchListener(new ImageGestureListener());
    }

    class ImageGestureListener extends GestureListener {

        @Override
        public void onClick(View v, MotionEvent e) {
            Log.e(this.toString(), "single");
            if (scaleFactor == 1) {
                finish();
            }
        }

        @Override
        public void onDoubleClick(View v, MotionEvent e) {
            Log.e(this.toString(), "double");
            if (scaleFactor > 1) {
                animaScaleImage(1);
            } else {
                animaScaleImage(2);
            }
        }

        @Override
        public boolean onScrollVertical(View view, float deltaY) {
            if (scaleFactor != 1) return false;
            imageView.setTranslationY(imageView.getTranslationY() + deltaY);
            return true;
        }

        @Override
        public boolean onScrollVerticalEnd(View view, MotionEvent motionEvent) {
            if (scaleFactor != 1) return false;
            animeTranslateImage(imageView.getTranslationX(), 0);
            return true;
        }

        @Override
        public boolean onScrollHorizontal(View view, float deltaX) {
            return scaleFactor == 1;
        }

        @Override
        public boolean onScrollHorizontalEnd(View view, MotionEvent motionEvent) {
            return scaleFactor == 1;
        }

        @Override
        public void onDrag(View view, float deltaX, float deltaY) {
            if (scaleFactor == 1) return;
            imageView.setTranslationX(imageView.getTranslationX() + deltaX);
            imageView.setTranslationY(imageView.getTranslationY() + deltaY);
        }

        @Override
        public void onDragEnd(View view, MotionEvent motionEvent) {
            boolean needReset = false;

            // TODO: fix wrong translation
            float targetX = imageView.getTranslationX();
            float targetY = imageView.getTranslationY();
            if (targetX > 0) {
                targetX = 0;
                needReset = true;
            }
            if (targetY > 0) {
                targetY = 0;
                needReset = true;
            }
            if (needReset)
                animeTranslateImage(targetX, targetY);
        }

        @Override
        public void onZoom(View view, float ratio) {
            scaleFactor *= ratio;
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));
            scaleImage(scaleFactor);
        }

        @Override
        public void onZoomEnd(View view) {
            Log.e(this.toString(), "zoom end");
            if (scaleFactor< 1) {
                animaScaleImage(1);
            }
        }
    }

    private float scaleFactor = 1;
    private void scaleImage(float scale) {
        imageView.setScaleX(scale);
        imageView.setScaleY(scale);
    }
    private void animaScaleImage(float targetScale) {
        ValueAnimator animator = ValueAnimator.ofFloat(scaleFactor, targetScale);
        animator.addUpdateListener(valueAnimator -> {
            scaleFactor = (float) valueAnimator.getAnimatedValue();
            scaleImage(scaleFactor);
        });
        animator.start();
    }
    private void translateImage(float deltaX, float deltaY) {
        imageView.setTranslationX(deltaX);
        imageView.setTranslationY(deltaY);
    }
    private void animeTranslateImage(float targetX, float targetY) {
        float dx = imageView.getTranslationX() - targetX;
        float dy = imageView.getTranslationY() - targetY;
        float distance = (float) Math.sqrt(dx*dx + dy*dy);
        ValueAnimator animator = ValueAnimator.ofFloat(distance, 0);
        animator.addUpdateListener(valueAnimator -> {
            float progress = 1 - valueAnimator.getAnimatedFraction();
            float newX = dx * progress;
            float newY = dy * progress;
            translateImage(newX, newY);
        });
        animator.start();
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