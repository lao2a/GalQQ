package top.galqq.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import top.galqq.R;
import top.galqq.utils.AiLogManager;

import top.galqq.utils.HostInfo;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AI日志查看Activity
 */
public class AiLogViewerActivity extends AppCompatTransferActivity {
    
    private TextView logContent;
    private Button btnExport;
    private Button btnClear;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置主题（与SettingsUiFragmentHostActivity保持一致）
        if (HostInfo.isInHostProcess()) {
            setTheme(R.style.Theme_GalQQ_DayNight);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_log_viewer);
        
        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.ai_log_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        logContent = findViewById(R.id.log_content);
        btnExport = findViewById(R.id.btn_export);
        btnClear = findViewById(R.id.btn_clear);
        
        // 导出按钮
        btnExport.setOnClickListener(v -> exportLogs());
        
        // 清除按钮
        btnClear.setOnClickListener(v -> {
            AiLogManager.clearLogs(this);
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show();
            loadLogs();
        });
        
        // 初始加载日志
        loadLogs();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    private void loadLogs() {
        String logs = AiLogManager.getLogs(this);
        logContent.setText(logs);
    }
    
    private void exportLogs() {
        try {
            String logs = AiLogManager.getLogs(this);
            
            // 创建文件名（带时间戳）
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "galqq_ai_logs_" + timestamp + ".txt";
            
            // 保存到外部存储的Download目录
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File logFile = new File(downloadsDir, filename);
            
            FileWriter writer = new FileWriter(logFile);
            writer.write(logs);
            writer.close();
            
            // 分享文件
            Uri fileUri = FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".fileprovider", logFile);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GalQQ AI Logs");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_logs)));
            
            Toast.makeText(this, "日志已保存到: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
