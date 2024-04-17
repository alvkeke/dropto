package cn.alvkeke.dropto.ui.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
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


    private View parentView;
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
        parentView = inflater.inflate(R.layout.fragment_image_viewer, container, false);
        return parentView;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (imgFile == null || !imgFile.exists() || !imgFile.isFile()) {
            Toast.makeText(requireContext(), "no Image view, exit", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        view.setBackgroundColor(Color.BLACK);
        imageView = view.findViewById(R.id.img_viewer_image);
        ImageLoader.getInstance().loadOriginalImageAsync(imgFile, bitmap -> {
            loadedBitmap = bitmap;
            imageView.setImageBitmap(bitmap);
            calcImageFixSize();
        });
        view.setOnTouchListener(new ImageGestureListener());
    }

    class ImageGestureListener extends GestureListener {

        @Override
        public void onClick(View v, MotionEvent e) {
            if (scaleFactor == 1) {
                finish();
            }
        }

        @Override
        public void onDoubleClick(View v, MotionEvent e) {
            if (scaleFactor > 1) {
                animeScaleImageTo(1);
                animeTranslateImageTo(0, 0);
            } else {
                animeScaleImageTo(2);
            }
        }

        @Override
        public boolean onScrollVertical(View view, float deltaY) {
            if (scaleFactor != 1) return false;
            float length = (float) imageView.getHeight() /3;
            float y = Math.abs(imageView.getTranslationY());
            float current = Math.min(y, length);
            float ratio = 1 - (current / length);
            ratio = Math.max(ratio, 0.3f);    // limit background transparent

            parentView.getBackground().setAlpha((int) (ratio * 0xff));
            imageView.setTranslationY(imageView.getTranslationY() + deltaY);
            return true;
        }

        @Override
        public boolean onScrollVerticalEnd(View view, MotionEvent motionEvent) {
            if (scaleFactor != 1) return false;
            if (Math.abs(imageView.getTranslationY()) > (float) imageView.getHeight() /3) {
                finish();
            } else {
                float dy = imageView.getTranslationY();
                ValueAnimator animator = ValueAnimator.ofFloat(dy, 0);
                animator.addUpdateListener(valueAnimator ->
                        imageView.setTranslationY((Float) valueAnimator.getAnimatedValue()));
                animator.start();
                parentView.getBackground().setAlpha(0xff);
            }
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
            translateImage(deltaX, deltaY);
        }

        private final PointF pointF = new PointF();
        @Override
        public void onDragEnd(View view, MotionEvent motionEvent) {
            if (scaleFactor == 1) return;
            adjustImagePosition(pointF);
            animeTranslateImageTo(pointF.x, pointF.y);
        }

        @Override
        public void onZoom(View view, float ratio) {
            scaleImage(ratio);
        }

        @Override
        public void onZoomEnd(View view) {
            if (scaleFactor< 1) {
                animeScaleImageTo(1);
                animeTranslateImageTo(0, 0);
            } else {
                adjustImagePosition(pointF);
                animeTranslateImageTo(pointF.x, pointF.y);
            }
        }
    }

    private float imageFixHeight;
    private float imageFixWidth;
    private void calcImageFixSize() {
        float ratio1 = (float) imageView.getHeight() /imageView.getWidth();
        float ratio2 = (float) loadedBitmap.getHeight() /loadedBitmap.getWidth();
        if (ratio1 > ratio2) {
            imageFixWidth = imageView.getWidth();
            imageFixHeight = imageFixWidth * ratio2;
        } else {
            imageFixHeight = imageView.getHeight();
            imageFixWidth = imageFixHeight / ratio2;
        }
    }

    private float getImageCenterX() {
        return imageView.getTranslationX() + (float) imageView.getWidth() /2;
    }
    private float getImageCenterY() {
        return imageView.getTranslationY() + (float) imageView.getHeight() / 2;
    }
    private void getVisibleRect(RectF rect) {
        float centerX = getImageCenterX();
        float centerY = getImageCenterY();
        float width_half = imageFixWidth * scaleFactor / 2;
        float height_half = imageFixHeight * scaleFactor / 2;
        float left = centerX - width_half;
        float right = centerX + width_half;
        float top = centerY - height_half;
        float bottom = centerY + height_half;
        rect.set(left, top, right, bottom);
    }
    private void centerToTranslation(PointF point) {
        point.x -= (float) imageView.getWidth() /2;
        point.y -= (float) imageView.getHeight() /2;
    }
    private final RectF rect = new RectF();
    private void adjustImagePosition(PointF point) {
        float maxRight = parentView.getWidth();
        float maxBottom = parentView.getHeight();
        getVisibleRect(rect);

        float diff;
        float length;
        if ((length = rect.width()) < maxRight) {
            diff = maxRight - length;
            diff /= 2;
            rect.offsetTo(diff, rect.top);
        } else {
            if (rect.left > 0)
                rect.offset(-rect.left, 0);
            if ((diff = maxRight - rect.right) > 0)
                rect.offset(diff, 0);
        }

        if ((length = rect.height()) < maxBottom) {
            diff = maxBottom - length;
            diff /= 2;
            rect.offsetTo(rect.left, diff);
        } else {
            if (rect.top > 0)
                rect.offset(0, -rect.top);
            if ((diff = maxBottom - rect.bottom) > 0)
                rect.offset(0, diff);
        }

        point.set(rect.centerX(), rect.centerY());
        centerToTranslation(point);
    }

    private float scaleFactor = 1;
    private void scaleImage() {
        imageView.setScaleX(scaleFactor);
        imageView.setScaleY(scaleFactor);
    }
    private void scaleImage(float scale) {
        scaleFactor *= scale;
        if (scaleFactor > 5) scaleFactor = 5f;
        if (scaleFactor < 0.1) scaleFactor = 0.1f;
        scaleImage();
    }
    private void scaleImageTo(float targetScale) {
        scaleFactor = targetScale;
        scaleImage();
    }
    private void animeScaleImageTo(float targetScale) {
        ValueAnimator animator = ValueAnimator.ofFloat(scaleFactor, targetScale);
        animator.addUpdateListener(valueAnimator ->
                scaleImageTo((Float) valueAnimator.getAnimatedValue()));
        animator.start();
    }
    private void translateImage(float x, float y) {
        translateImageTo(x + imageView.getTranslationX(),
                y + imageView.getTranslationY());
    }
    private void translateImageTo(float targetX, float targetY) {
        imageView.setTranslationX(targetX);
        imageView.setTranslationY(targetY);
    }
    private void animeTranslateImageTo(float targetX, float targetY) {
        float startX = imageView.getTranslationX();
        float startY = imageView.getTranslationY();
        float diffX = targetX - startX;
        float diffY = targetY - startY;
        if (diffY == 0 && diffX == 0) return;
        boolean useY;
        float start, end;
        if (diffY != 0) {
            useY = true;
            start = startY;
            end = targetY;
        } else {
            useY = false;
            start = startX;
            end = targetX;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);
        animator.addUpdateListener(valueAnimator -> {
            float x, y;
            if (useY) {
                y = (float) valueAnimator.getAnimatedValue();
                x = startX + diffX * valueAnimator.getAnimatedFraction();
            } else {
                x = (float) valueAnimator.getAnimatedValue();
                y = startY + diffY * valueAnimator.getAnimatedFraction();
            }
            translateImageTo(x, y);
        });
        animator.start();
    }

    void finish() {
        float startY = imageView.getTranslationY();
        float endY = parentView.getHeight();
        float startT = parentView.getAlpha();
        ValueAnimator animator = ValueAnimator.ofFloat(startY, endY);
        animator.addUpdateListener(valueAnimator -> {
            imageView.setTranslationY((Float) valueAnimator.getAnimatedValue());
            float progress = valueAnimator.getAnimatedFraction();
            parentView.setAlpha(startT - startT*progress);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                getParentFragmentManager().beginTransaction()
                        .remove(ImageViewerFragment.this).commit();
            }
        });
        animator.start();
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