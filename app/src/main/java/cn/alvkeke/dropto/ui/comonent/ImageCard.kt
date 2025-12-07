package cn.alvkeke.dropto.ui.comonent;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import cn.alvkeke.dropto.R;


public class ImageCard extends ConstraintLayout {

    private final ImageView imageView;
    private final ImageView btnRemove;
    private final TextView tvImageName;
    private final TextView tvImageMd5;

    public ImageCard(@NonNull Context context) {
        super(context);

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.component_image_card, this, false);

        imageView = view.findViewById(R.id.note_detail_image_view);
        btnRemove = view.findViewById(R.id.note_detail_image_remove);
        tvImageMd5 = view.findViewById(R.id.note_detail_image_md5);
        tvImageName = view.findViewById(R.id.note_detail_image_name);

        this.addView(view);
    }

    public void setImageName(String name) {
        tvImageName.setText(name);
    }

    public void setImageMd5(String md5) {
        tvImageMd5.setText(md5);
    }

    public void setImage(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }

    public void setImageResource(int resId) {
        imageView.setImageResource(resId);
    }

    public void setRemoveButtonClickListener(OnClickListener listener) {
        btnRemove.setOnClickListener(listener);
    }

    public void setImageClickListener(OnClickListener listener) {
        imageView.setOnClickListener(listener);
    }

}
