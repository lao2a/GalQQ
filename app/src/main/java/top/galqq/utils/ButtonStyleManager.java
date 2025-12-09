package top.galqq.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

import top.galqq.config.ConfigManager;

/**
 * 按钮样式管理器
 * 负责管理选项按钮的样式配置，包括填充颜色、边框颜色、边框宽度和字体颜色
 */
public class ButtonStyleManager {

    // 默认值常量（与 ConfigManager 保持一致）
    public static final int DEFAULT_FILL_COLOR = ConfigManager.DEFAULT_BUTTON_FILL_COLOR;
    public static final int DEFAULT_BORDER_COLOR = ConfigManager.DEFAULT_BUTTON_BORDER_COLOR;
    public static final int DEFAULT_TEXT_COLOR = ConfigManager.DEFAULT_BUTTON_TEXT_COLOR;
    public static final int DEFAULT_BORDER_WIDTH = ConfigManager.DEFAULT_BUTTON_BORDER_WIDTH;

    /**
     * 获取填充颜色
     * @return 颜色值（ARGB格式）
     */
    public static int getFillColor() {
        return ConfigManager.getButtonFillColor();
    }

    /**
     * 设置填充颜色
     * @param color 颜色值（ARGB格式）
     */
    public static void setFillColor(int color) {
        ConfigManager.setButtonFillColor(color);
    }

    /**
     * 获取边框颜色
     * @return 颜色值（ARGB格式）
     */
    public static int getBorderColor() {
        return ConfigManager.getButtonBorderColor();
    }

    /**
     * 设置边框颜色
     * @param color 颜色值（ARGB格式）
     */
    public static void setBorderColor(int color) {
        ConfigManager.setButtonBorderColor(color);
    }


    /**
     * 获取边框宽度
     * @return 宽度值（dp）
     */
    public static int getBorderWidth() {
        return ConfigManager.getButtonBorderWidth();
    }

    /**
     * 设置边框宽度
     * @param width 宽度值（dp）
     */
    public static void setBorderWidth(int width) {
        ConfigManager.setButtonBorderWidth(width);
    }

    /**
     * 获取字体颜色
     * @return 颜色值（ARGB格式）
     */
    public static int getTextColor() {
        return ConfigManager.getButtonTextColor();
    }

    /**
     * 设置字体颜色
     * @param color 颜色值（ARGB格式）
     */
    public static void setTextColor(int color) {
        ConfigManager.setButtonTextColor(color);
    }

    /**
     * 重置所有样式为默认值
     */
    public static void resetToDefaults() {
        ConfigManager.resetButtonStyles();
    }

    /**
     * 检查是否有自定义样式
     * @return 是否有任何自定义样式设置
     */
    public static boolean hasCustomStyles() {
        return ConfigManager.hasCustomButtonStyles();
    }

    /**
     * 创建带自定义样式的圆角背景
     * @param context 上下文
     * @param radiusPx 圆角半径（像素）
     * @return Drawable 背景
     */
    public static Drawable createStyledBackground(Context context, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(radiusPx);
        
        // 设置填充颜色
        drawable.setColor(getFillColor());
        
        // 设置边框
        int borderWidth = getBorderWidth();
        if (borderWidth > 0) {
            int borderWidthPx = dpToPx(context, borderWidth);
            drawable.setStroke(borderWidthPx, getBorderColor());
        }
        
        return drawable;
    }


    /**
     * 创建带按压状态的自定义样式圆角背景
     * @param context 上下文
     * @param radiusPx 圆角半径（像素）
     * @return StateListDrawable 背景
     */
    public static Drawable createSelectableStyledBackground(Context context, int radiusPx) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        
        // 按压状态 - 颜色加深 10%
        GradientDrawable pressedDrawable = new GradientDrawable();
        pressedDrawable.setShape(GradientDrawable.RECTANGLE);
        pressedDrawable.setCornerRadius(radiusPx);
        pressedDrawable.setColor(darkenColor(getFillColor(), 0.1f));
        
        int borderWidth = getBorderWidth();
        if (borderWidth > 0) {
            int borderWidthPx = dpToPx(context, borderWidth);
            pressedDrawable.setStroke(borderWidthPx, darkenColor(getBorderColor(), 0.1f));
        }
        
        // 普通状态
        GradientDrawable normalDrawable = new GradientDrawable();
        normalDrawable.setShape(GradientDrawable.RECTANGLE);
        normalDrawable.setCornerRadius(radiusPx);
        normalDrawable.setColor(getFillColor());
        
        if (borderWidth > 0) {
            int borderWidthPx = dpToPx(context, borderWidth);
            normalDrawable.setStroke(borderWidthPx, getBorderColor());
        }
        
        // 添加状态
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(new int[]{}, normalDrawable);
        
        return stateListDrawable;
    }

    /**
     * 将颜色加深指定比例
     * @param color 原始颜色
     * @param factor 加深比例 (0.0 - 1.0)
     * @return 加深后的颜色
     */
    public static int darkenColor(int color, float factor) {
        int alpha = Color.alpha(color);
        int red = (int) (Color.red(color) * (1 - factor));
        int green = (int) (Color.green(color) * (1 - factor));
        int blue = (int) (Color.blue(color) * (1 - factor));
        return Color.argb(alpha, 
                Math.max(0, red), 
                Math.max(0, green), 
                Math.max(0, blue));
    }

    /**
     * dp 转 px
     * @param context 上下文
     * @param dp dp 值
     * @return px 值
     */
    public static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    /**
     * 将颜色值转换为十六进制字符串
     * @param color 颜色值（ARGB格式）
     * @return 十六进制字符串，如 "#FF000000"
     */
    public static String colorToHex(int color) {
        return String.format("#%08X", color);
    }

    /**
     * 将十六进制字符串转换为颜色值
     * @param hex 十六进制字符串，如 "#FF000000" 或 "#000000"
     * @return 颜色值（ARGB格式），解析失败返回默认填充色
     */
    public static int hexToColor(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return DEFAULT_FILL_COLOR;
        }
    }
}
