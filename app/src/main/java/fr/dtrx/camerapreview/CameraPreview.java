package fr.dtrx.camerapreview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;

import androidx.core.content.FileProvider;
import androidx.databinding.BindingAdapter;
import androidx.databinding.BindingMethod;
import androidx.databinding.BindingMethods;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import fr.dtrx.androidcore.utils.FileUtils;
import rebus.permissionutils.PermissionEnum;
import rebus.permissionutils.PermissionManager;

@BindingMethods({
        @BindingMethod(type = CameraPreview.class, attribute = "onPictureTook", method = "setOnPictureTookListener"),
        @BindingMethod(type = CameraPreview.class, attribute = "onActionButtonPressed", method = "setOnActionButtonPressedListener"),
        @BindingMethod(type = CameraPreview.class, attribute = "onDeleteButtonPressed", method = "setOnDeleteButtonPressedListener")
})
public class CameraPreview extends LinearLayout {

    View parentView;

    ImageView imgPreview;
    Button btnAction;
    Button btnDelete;

    private int attrPlaceHolder;
    private float attrPlaceHolderPadding;
    private int attrPlaceHolderTint;
    private float attrSourcePadding;
    private int attrSourceTint;
    private boolean attrEnableActionButton;
    private boolean attrEnableDeleteButton;
    private boolean attrEnableTakePhotoButton;
    private int attrActionButtonDrawable;
    private int attrDeleteButtonDrawable;

    private Uri sourceUri;
    private File sourceFile;

    private OnPictureTookListener onPictureTookListener;
    private OnDeleteButtonPressedListener onDeleteButtonPressedListener;
    private OnActionButtonPressedListener onActionButtonPressedListener;

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);

        createView(context, attrs);
    }

    private void createView(Context context, AttributeSet attrs) {
        parentView = inflate(context, R.layout.component_camera_preview, this);

        imgPreview = findViewById(R.id.imgPreview);
        btnAction = findViewById(R.id.btnAction);
        btnDelete = findViewById(R.id.btnDelete);

        initializeData(context, attrs);
        initializeFirst();
        initializeView();
        initializeListeners();
    }

    private void initializeData(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraPreview);
        attrPlaceHolder = typedArray.getResourceId(R.styleable.CameraPreview_placeholder, -1);
        attrPlaceHolderPadding = typedArray.getDimension(R.styleable.CameraPreview_placeholderPadding, 0.0f);
        attrPlaceHolderTint = typedArray.getColor(R.styleable.CameraPreview_placeholderTint, getContext().getResources().getColor(android.R.color.white));
        attrSourcePadding = typedArray.getDimension(R.styleable.CameraPreview_sourcePadding, 0.0f);
        attrSourceTint = typedArray.getColor(R.styleable.CameraPreview_sourceTint, getContext().getResources().getColor(android.R.color.white));
        attrEnableActionButton = typedArray.getBoolean(R.styleable.CameraPreview_enableActionButton, true);
        attrEnableDeleteButton = typedArray.getBoolean(R.styleable.CameraPreview_enableDeleteButton, true);
        attrEnableTakePhotoButton = typedArray.getBoolean(R.styleable.CameraPreview_enableTakePhotoButton, true);
        attrActionButtonDrawable = typedArray.getResourceId(R.styleable.CameraPreview_actionButtonDrawable, -1);
        attrDeleteButtonDrawable = typedArray.getResourceId(R.styleable.CameraPreview_deleteButtonDrawable, -1);
        typedArray.recycle();
    }

    private void initializeFirst() {
        if (attrActionButtonDrawable >= 0) {
            btnAction.setBackgroundResource(attrActionButtonDrawable);
        }
        if (attrDeleteButtonDrawable >= 0) {
            btnDelete.setBackgroundResource(attrDeleteButtonDrawable);
        }
    }

    private void initializeView() {
        if (sourceUri != null) {
            drawImagePreview(sourceUri);
            imgPreview.setPadding((int) attrSourcePadding, (int) attrSourcePadding, (int) attrSourcePadding, (int) attrSourcePadding);
            setColorFilter(imgPreview, attrSourceTint);
            btnAction.setVisibility(attrEnableActionButton ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(attrEnableDeleteButton ? View.VISIBLE : View.GONE);
        }
        else {
            if (attrPlaceHolder >= 0) {
                drawImagePreview(attrPlaceHolder);
                imgPreview.setPadding((int) attrPlaceHolderPadding, (int) attrPlaceHolderPadding, (int) attrPlaceHolderPadding, (int) attrPlaceHolderPadding);
                setColorFilter(imgPreview, attrPlaceHolderTint);
            }
            btnAction.setVisibility(attrEnableActionButton ? View.INVISIBLE : View.GONE);
            btnDelete.setVisibility(attrEnableDeleteButton ? View.INVISIBLE : View.GONE);
        }
    }

    private void initializeListeners() {
        if (attrEnableTakePhotoButton) {
            imgPreview.setOnClickListener(view -> startCameraActivity());
        }
        btnAction.setOnClickListener(view -> actionOnImage());
        btnDelete.setOnClickListener(view -> deleteImage());
    }

    public void startCameraActivity() {
        final String TAG_FRAGMENT_CAMERA = "CameraPreviewView";
        final int REQUEST_CODE_CAMERA = 3333;

        final FragmentManager fm = ((FragmentActivity) getContext()).getSupportFragmentManager();
        fr.dtrx.androidcore.fragments.ActivityResultFragment auxiliary = new fr.dtrx.androidcore.fragments.ActivityResultFragment();
        auxiliary.setActivityResultListener((requestCode, resultCode, data) -> {
            switch (requestCode) {
                case REQUEST_CODE_CAMERA: {
                    if (resultCode == Activity.RESULT_OK) {
                        onCameraResult();
                    }
                }
            }
        });
        fm.beginTransaction().add(auxiliary, TAG_FRAGMENT_CAMERA).commit();
        fm.executePendingTransactions();

        PermissionManager.Builder()
                .permission(PermissionEnum.CAMERA)
                .askAgain(true)
                .callback(allPermissionsGranted -> {
                    if (allPermissionsGranted) {
                        if (initializeSourceUri()) {
                            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            intent.putExtra(MediaStore.EXTRA_OUTPUT, sourceUri);
                            auxiliary.startActivityForResult(intent, REQUEST_CODE_CAMERA);
                        }
                    }
                })
                .ask((Activity) getContext());
    }

    public void actionOnImage() {
        if (onActionButtonPressedListener != null) {
            onActionButtonPressedListener.onAction(sourceFile, sourceUri);
        }
    }

    public void deleteImage() {
        if (sourceFile != null) {
            sourceFile.delete();
        }
        sourceFile = null;
        sourceUri = null;

        initializeView();

        if (onDeleteButtonPressedListener != null) {
            onDeleteButtonPressedListener.onDelete();
        }
    }

    private void onCameraResult() {
        initializeView();

        if (onPictureTookListener != null) {
            onPictureTookListener.onPictureTook(sourceFile, sourceUri);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        imgPreview.draw(canvas);
        btnAction.draw(canvas);
        btnDelete.draw(canvas);
    }

    @BindingAdapter("source")
    public static void bindSource(CameraPreview view, File source) {
        if (source != null) {
            view.setSourceFile(source);
        }
    }

    private void setColorFilter(ImageView imageView, int colorFilter) {
        if (colorFilter == -1) {
            imageView.clearColorFilter();
        }
        else {
            imageView.setColorFilter(colorFilter);
        }
    }

    private void drawImagePreview(Uri uri) {
        Glide.with(this)
                .load(uri)
                .into(imgPreview);
    }

    private void drawImagePreview(int resId) {
        Glide.with(this)
                .load(resId)
                .into(imgPreview);
    }

    /**
     * Set the sourceUri path
     *
     * @return true if sourceUri is set
     */
    public boolean initializeSourceUri() {
        try {
            sourceFile = FileUtils.createImageFile(getContext());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (sourceFile != null) {
            sourceUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getApplicationContext().getPackageName() + ".provider",
                    sourceFile
            );

            return true;
        }

        return false;
    }

    @SuppressWarnings("unused")
    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;

        if (sourceFile != null) {
            setSourceUri(
                    FileProvider.getUriForFile(
                            getContext(),
                            getContext().getApplicationContext().getPackageName() + ".provider",
                            sourceFile
                    )
            );
        }
    }

    @SuppressWarnings("unused")
    private void setSourceUri(Uri sourceUri) {
        this.sourceUri = sourceUri;

        initializeView();
    }

    @SuppressWarnings("unused")
    public void setOnPictureTookListener(OnPictureTookListener onPictureTookListener) {
        this.onPictureTookListener = onPictureTookListener;
    }

    @SuppressWarnings("unused")
    public void setOnDeleteButtonPressedListener(OnDeleteButtonPressedListener onDeleteButtonPressedListener) {
        this.onDeleteButtonPressedListener = onDeleteButtonPressedListener;
    }

    @SuppressWarnings("unused")
    public void setOnActionButtonPressedListener(OnActionButtonPressedListener onActionButtonPressedListener) {
        this.onActionButtonPressedListener = onActionButtonPressedListener;
    }

    public interface OnPictureTookListener {
        void onPictureTook(File sourceFile, Uri sourceUri);
    }

    public interface OnDeleteButtonPressedListener {
        void onDelete();
    }

    public interface OnActionButtonPressedListener {
        void onAction(File sourceFile, Uri sourceUri);
    }

}

