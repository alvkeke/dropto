package cn.alvkeke.dropto.ui.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.ImageFile;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.ImageLoader;
import cn.alvkeke.dropto.ui.comonent.ImageCard;
import cn.alvkeke.dropto.ui.intf.NoteAttemptListener;

public class NoteDetailFragment extends BottomSheetDialogFragment {

    private NoteAttemptListener listener;
    private EditText etNoteItemText;
    private LinearLayout scrollContainer;
    private NoteItem item;
    private boolean isImageChanged = false;
    private final ArrayList<ImageFile> imageList = new ArrayList<>();

    public NoteDetailFragment() {
    }

    public void setNoteItem(NoteItem item) {
        this.item = item;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listener = (NoteAttemptListener) requireContext();

        MaterialToolbar toolbar = view.findViewById(R.id.note_detail_toolbar);
        etNoteItemText = view.findViewById(R.id.note_detail_text);
        ScrollView scroll_view = view.findViewById(R.id.note_detail_scroll);
        scrollContainer = view.findViewById(R.id.note_detail_scroll_container);

        initEssentialVars();
        setPeekHeight(view);

        if (item != null) loadItemData();

        toolbar.inflateMenu(R.menu.note_detail_toolbar);
        toolbar.setOnMenuItemClickListener(new NoteDetailMenuListener());
        toolbar.setNavigationIcon(R.drawable.icon_common_cross);
        toolbar.setNavigationOnClickListener(new BackNavigationClick());
        scroll_view.setOnScrollChangeListener(new ScrollViewListener());

    }

    private boolean isDraggable = true;

    private class ScrollViewListener implements View.OnScrollChangeListener {

        @Override
        public void onScrollChange(View view, int x, int y, int i2, int i3) {
            if (y <= 0) {
                if (!isDraggable) {
                    isDraggable = true;
                    behavior.setDraggable(true);
                }
            } else {
                if (isDraggable) {
                    isDraggable = false;
                    behavior.setDraggable(false);
                }
            }
        }
    }

    private BottomSheetBehavior<FrameLayout> behavior;
    private void initEssentialVars() {
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        behavior = dialog.getBehavior();
    }

    private void setPeekHeight(View view) {
        int displayHei= requireActivity().getResources().getDisplayMetrics().heightPixels;
        int peekHei = displayHei * 35 / 100;
        behavior.setPeekHeight(peekHei);
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = displayHei;
    }

    private void finish() {
        this.dismiss();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    private class BackNavigationClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            finish();
        }
    }

    private void handleOk() {
        String text = etNoteItemText.getText().toString();
        if (item == null) {
            item = new NoteItem(text);
            listener.onAttempt(NoteAttemptListener.Attempt.CREATE, item);
            return;
        }
        if (text.isEmpty() && isImageChanged && imageList.isEmpty()) {
            Toast.makeText(requireContext(),
                    "Got empty item after modifying, remove it", Toast.LENGTH_SHORT).show();
            listener.onAttempt(NoteAttemptListener.Attempt.REMOVE, item);
            return;
        }
        item.setText(text, true);
        if (isImageChanged) {
            item.useImageFiles(imageList);
        }
        listener.onAttempt(NoteAttemptListener.Attempt.UPDATE, item);
    }

    private class NoteDetailMenuListener implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            int menuId = menuItem.getItemId();
            if (R.id.note_detail_menu_item_ok == menuId) {
                handleOk();
            } else if (R.id.note_detail_menu_item_delete == menuId) {
                Log.d(this.toString(), "remove item");
                listener.onAttempt(NoteAttemptListener.Attempt.REMOVE, item);
            } else {
                Log.e(this.toString(), "got unknown menu id: " + menuId);
                return false;
            }
            finish();
            return true;
        }
    }

    /**
     * load item info to View, input item cannot be null,
     * there is no valid-check for the item.
     */
    private void loadItemData() {
        etNoteItemText.setText(item.getText());

        if (item.isNoImage())
            return;

        Context context = requireContext();
        item.iterateImages().forEachRemaining(imageFile -> {
            imageList.add(imageFile);

            ImageCard card = new ImageCard(context);
            card.setImageMd5(imageFile.getMd5());
            card.setImageName(imageFile.getName());

            ImageLoader.getInstance().loadImageAsync(imageFile.getMd5file(), (bitmap -> {
                if (bitmap == null) {
                    String errMsg = "Failed to get image file, skip this item";
                    Log.e(this.toString(), errMsg);
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show();
                    card.setImageResource(R.drawable.img_load_error);
                    return;
                }
                card.setImage(bitmap);
            }));
            card.setRemoveButtonClickListener(view -> {
                int hei = card.getHeight();
                ValueAnimator animator = ValueAnimator.ofInt(hei, 0);
                animator.addUpdateListener(valueAnimator -> {
                    ViewGroup.LayoutParams params = card.getLayoutParams();
                    params.height = (int) valueAnimator.getAnimatedValue();
                    card.setLayoutParams(params);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        imageList.remove(imageFile);
                        isImageChanged = true;
                        scrollContainer.removeView(card);
                    }
                });
                animator.start();
            });
            int imageIndex = item.indexOf(imageFile);
            card.setImageClickListener(view1 ->
                    listener.onAttempt(NoteAttemptListener.Attempt.SHOW_IMAGE, item, imageIndex));

            scrollContainer.addView(card);
        });

    }

}