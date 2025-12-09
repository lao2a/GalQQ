package top.galqq.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import top.galqq.R;
import top.galqq.config.ConfigImporter;
import top.galqq.config.ConfigManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 导入预览对话框
 * 显示导入预览，允许用户选择要导入的分类
 */
public class ImportPreviewDialog extends DialogFragment {
    
    private static final String TAG = "ImportPreviewDialog";
    
    private ConfigImporter.ImportResult importResult;
    private ImportCallback callback;
    private Map<String, CheckBox> categoryCheckBoxes = new HashMap<>();
    
    /**
     * 导入回调接口
     */
    public interface ImportCallback {
        void onConfirm(Set<String> selectedCategories);
        void onCancel();
    }
    
    /**
     * 创建对话框实例
     * @param result 解析结果
     * @param callback 确认回调
     */
    public static ImportPreviewDialog newInstance(ConfigImporter.ImportResult result, ImportCallback callback) {
        ImportPreviewDialog dialog = new ImportPreviewDialog();
        dialog.importResult = result;
        dialog.callback = callback;
        return dialog;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        
        // 如果 importResult 为 null（Activity 重建后恢复的情况），直接关闭对话框
        if (importResult == null) {
            // 延迟关闭，避免在 onCreateDialog 中直接 dismiss 导致异常
            dismissAllowingStateLoss();
            // 返回一个空对话框，它会立即被关闭
            return new AlertDialog.Builder(context)
                .setMessage("")
                .create();
        }
        
        // 加载布局
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_import_preview, null);
        
        // 初始化视图
        initMetadataView(view);
        initCategoryCheckboxes(view);
        initSelectButtons(view);
        initWarningsView(view);
        
        // 创建对话框
        return new AlertDialog.Builder(context)
            .setTitle("导入配置预览")
            .setView(view)
            .setPositiveButton("确认导入", (dialog, which) -> {
                if (callback != null) {
                    callback.onConfirm(getSelectedCategories());
                }
            })
            .setNegativeButton("取消", (dialog, which) -> {
                if (callback != null) {
                    callback.onCancel();
                }
            })
            .create();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 如果数据丢失（Activity 重建后），关闭对话框
        if (importResult == null) {
            dismissAllowingStateLoss();
        }
    }
    
    /**
     * 初始化元数据显示
     */
    private void initMetadataView(View view) {
        TextView tvMetadata = view.findViewById(R.id.tv_metadata);
        
        if (importResult == null || importResult.metadata == null) {
            tvMetadata.setText("无元数据信息");
            return;
        }
        
        ConfigImporter.ConfigMetadata metadata = importResult.metadata;
        StringBuilder sb = new StringBuilder();
        
        if (metadata.exportTime != null && !metadata.exportTime.isEmpty()) {
            sb.append("导出时间: ").append(metadata.exportTime).append("\n");
        }
        if (metadata.appVersion != null && !metadata.appVersion.isEmpty()) {
            sb.append("应用版本: ").append(metadata.appVersion).append("\n");
        }
        sb.append("配置版本: v").append(metadata.schemaVersion).append("\n");
        if (metadata.deviceInfo != null && !metadata.deviceInfo.isEmpty()) {
            sb.append("来源设备: ").append(metadata.deviceInfo);
        }
        
        tvMetadata.setText(sb.toString().trim());
    }
    
    /**
     * 初始化分类复选框
     */
    private void initCategoryCheckboxes(View view) {
        LinearLayout llCategories = view.findViewById(R.id.ll_categories);
        llCategories.removeAllViews();
        categoryCheckBoxes.clear();
        
        if (importResult == null || importResult.configsByCategory == null) {
            return;
        }
        
        for (String category : ConfigManager.ALL_CATEGORIES) {
            Map<String, Object> configs = importResult.configsByCategory.get(category);
            int count = configs != null ? configs.size() : 0;
            
            // 只显示有配置项的分类
            if (count > 0) {
                CheckBox checkBox = new CheckBox(requireContext());
                String displayName = ConfigManager.getCategoryDisplayName(category);
                
                // 对提示词分类进行特殊处理，显示提示词数量而不是配置键数量
                String countText = getCountText(category, configs, count);
                
                checkBox.setText(displayName + " (" + countText + ")");
                checkBox.setChecked(true);  // 默认全选
                checkBox.setTag(category);
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 4, 0, 4);
                checkBox.setLayoutParams(params);
                
                llCategories.addView(checkBox);
                categoryCheckBoxes.put(category, checkBox);
            }
        }
    }
    
    /**
     * 获取分类的数量显示文本
     * 对提示词分类进行特殊处理，显示提示词数量
     */
    private String getCountText(String category, Map<String, Object> configs, int defaultCount) {
        if (ConfigManager.CATEGORY_PROMPTS.equals(category) && configs != null) {
            // 尝试解析提示词列表获取实际数量
            Object promptListObj = configs.get(ConfigManager.KEY_PROMPT_LIST);
            if (promptListObj != null) {
                try {
                    String promptListJson = String.valueOf(promptListObj);
                    org.json.JSONArray arr = new org.json.JSONArray(promptListJson);
                    int promptCount = arr.length();
                    return promptCount + " 个提示词";
                } catch (Exception e) {
                    // 解析失败，使用默认显示
                }
            }
        }
        return defaultCount + " 项";
    }
    
    /**
     * 初始化全选/取消全选按钮
     */
    private void initSelectButtons(View view) {
        Button btnSelectAll = view.findViewById(R.id.btn_select_all);
        Button btnDeselectAll = view.findViewById(R.id.btn_deselect_all);
        
        btnSelectAll.setOnClickListener(v -> {
            for (CheckBox checkBox : categoryCheckBoxes.values()) {
                checkBox.setChecked(true);
            }
        });
        
        btnDeselectAll.setOnClickListener(v -> {
            for (CheckBox checkBox : categoryCheckBoxes.values()) {
                checkBox.setChecked(false);
            }
        });
    }
    
    /**
     * 初始化警告信息显示
     */
    private void initWarningsView(View view) {
        TextView tvWarningsTitle = view.findViewById(R.id.tv_warnings_title);
        TextView tvWarnings = view.findViewById(R.id.tv_warnings);
        
        if (importResult == null || importResult.warnings == null || importResult.warnings.isEmpty()) {
            tvWarningsTitle.setVisibility(View.GONE);
            tvWarnings.setVisibility(View.GONE);
            return;
        }
        
        tvWarningsTitle.setVisibility(View.VISIBLE);
        tvWarnings.setVisibility(View.VISIBLE);
        
        StringBuilder sb = new StringBuilder();
        for (String warning : importResult.warnings) {
            sb.append("• ").append(warning).append("\n");
        }
        tvWarnings.setText(sb.toString().trim());
    }
    
    /**
     * 获取选中的分类
     */
    private Set<String> getSelectedCategories() {
        Set<String> selected = new HashSet<>();
        for (Map.Entry<String, CheckBox> entry : categoryCheckBoxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }
}
