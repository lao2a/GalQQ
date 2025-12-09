package top.galqq.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import top.galqq.R;
import top.galqq.utils.ButtonStyleManager;

/**
 * 颜色选择器对话框
 * 支持 HEX 输入和预设颜色选择
 */
public class ColorPickerDialog extends Dialog {

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
        void onCancel();
    }

    private static final int[] PRESET_COLORS = {
        0xFFF2F2F2, // 默认浅灰
        0xFFFFFFFF, // 白色
        0xFF000000, // 黑色
        0xFFE0E0E0, // 浅灰
        0xFF9E9E9E, // 中灰
        0xFF616161, // 深灰
        0xFFE3F2FD, // 浅蓝
        0xFF90CAF9, // 蓝色
        0xFF2196F3, // 深蓝
        0xFFE8F5E9, // 浅绿
        0xFFA5D6A7, // 绿色
        0xFF4CAF50, // 深绿
        0xFFFFF3E0, // 浅橙
        0xFFFFCC80, // 橙色
        0xFFFF9800, // 深橙
        0xFFFFEBEE, // 浅红
        0xFFEF9A9A, // 红色
        0xFFF44336, // 深红
    };

    private int mCurrentColor;
    private OnColorSelectedListener mListener;
    private String mTitle;

    private View mColorPreview;
    private TextView mHexText;
    private TextView mButtonPreview;
    private EditText mHexInput;

    public ColorPickerDialog(Context context, String title, int initialColor, OnColorSelectedListener listener) {
        super(context);
        this.mTitle = title;
        this.mCurrentColor = initialColor;
        this.mListener = listener;
    }

    public static ColorPickerDialog newInstance(Context context, String title, int initialColor, OnColorSelectedListener listener) {
        return new ColorPickerDialog(context, title, initialColor, listener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_color_picker);

        // 设置对话框宽度
        if (getWindow() != null) {
            getWindow().setLayout(
                (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.9),
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        initViews();
        setupPresetColors();
        updatePreview(mCurrentColor);
    }

    private void initViews() {
        TextView titleView = findViewById(R.id.tv_title);
        titleView.setText(mTitle);

        mColorPreview = findViewById(R.id.view_color_preview);
        mHexText = findViewById(R.id.tv_color_hex);
        mButtonPreview = findViewById(R.id.tv_button_preview);
        mHexInput = findViewById(R.id.et_hex_color);

        // 设置初始 HEX 值
        mHexInput.setText(colorToHex(mCurrentColor));

        // HEX 输入监听
        mHexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String hex = s.toString().trim();
                if (hex.startsWith("#") && (hex.length() == 7 || hex.length() == 9)) {
                    try {
                        int color = Color.parseColor(hex);
                        mCurrentColor = color;
                        updatePreview(color);
                    } catch (Exception ignored) {}
                }
            }
        });

        // 取消按钮
        Button cancelBtn = findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onCancel();
            }
            dismiss();
        });

        // 确定按钮
        Button confirmBtn = findViewById(R.id.btn_confirm);
        confirmBtn.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onColorSelected(mCurrentColor);
            }
            dismiss();
        });
    }


    private void setupPresetColors() {
        GridLayout grid = findViewById(R.id.grid_preset_colors);
        int size = dpToPx(36);
        int margin = dpToPx(4);

        for (int color : PRESET_COLORS) {
            View colorView = new View(getContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(margin, margin, margin, margin);
            colorView.setLayoutParams(params);

            // 创建圆角背景
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(dpToPx(4));
            drawable.setColor(color);
            drawable.setStroke(dpToPx(1), Color.parseColor("#E0E0E0"));
            colorView.setBackground(drawable);

            colorView.setOnClickListener(v -> {
                mCurrentColor = color;
                mHexInput.setText(colorToHex(color));
                updatePreview(color);
            });

            grid.addView(colorView);
        }
    }

    private void updatePreview(int color) {
        // 更新颜色预览块
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.RECTANGLE);
        previewBg.setCornerRadius(dpToPx(4));
        previewBg.setColor(color);
        previewBg.setStroke(dpToPx(1), Color.parseColor("#E0E0E0"));
        mColorPreview.setBackground(previewBg);

        // 更新 HEX 文本
        mHexText.setText(colorToHex(color));

        // 更新按钮预览
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setShape(GradientDrawable.RECTANGLE);
        buttonBg.setCornerRadius(dpToPx(12));
        buttonBg.setColor(color);
        
        int borderWidth = ButtonStyleManager.getBorderWidth();
        if (borderWidth > 0) {
            buttonBg.setStroke(dpToPx(borderWidth), ButtonStyleManager.getBorderColor());
        }
        
        mButtonPreview.setBackground(buttonBg);
        mButtonPreview.setTextColor(ButtonStyleManager.getTextColor());
    }

    private String colorToHex(int color) {
        return String.format("#%08X", color);
    }

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
