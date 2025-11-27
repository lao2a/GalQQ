package top.galqq.ui;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.widget.Toolbar;
import top.galqq.R;
import top.galqq.utils.HostInfo;

public class SettingsUiFragmentHostActivity extends AppCompatTransferActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (HostInfo.isInHostProcess()) {
            setTheme(R.style.Theme_GalQQ_DayNight);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_host);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        requestTranslucentStatusBar();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new GalSettingsFragment())
                .commit();
        }
    }

    protected void requestTranslucentStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        
        View decorView = window.getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(option);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
             // Check if night mode is not enabled to set light status bar
             // For simplicity, assuming light theme for now or handle dynamic theme
             // window.getDecorView().setSystemUiVisibility(option | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }
}
