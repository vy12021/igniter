package io.github.trojan_gfw.igniter.common.app;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseAppCompatActivity extends AppCompatActivity {
    protected Context mContext;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setupStatusBar();
        setupEdgeToEdge();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        setupStatusBar();
        setupEdgeToEdge();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        setupStatusBar();
        setupEdgeToEdge();
    }

    private void setupStatusBar() {
        // Ensure status bar color matches theme
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Use color resource directly for more reliable results
                int colorPrimaryDark = androidx.core.content.ContextCompat.getColor(this, 
                    io.github.trojan_gfw.igniter.R.color.colorPrimaryDark);
                
                android.util.Log.d("BaseAppCompatActivity", "Setting status bar color to: " + 
                    String.format("#%06X", (0xFFFFFF & colorPrimaryDark)));
                
                getWindow().setStatusBarColor(colorPrimaryDark);
                getWindow().setNavigationBarColor(colorPrimaryDark);
                
                // Set status bar text color to light (white) since we're using dark background
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    View decorView = getWindow().getDecorView();
                    androidx.core.view.WindowInsetsControllerCompat controller = 
                        androidx.core.view.WindowCompat.getInsetsController(getWindow(), decorView);
                    if (controller != null) {
                        controller.setAppearanceLightStatusBars(false); // Dark background, light text
                        controller.setAppearanceLightNavigationBars(false);
                        android.util.Log.d("BaseAppCompatActivity", "Set status bar text to light (white)");
                    }
                    
                    // Also set system UI flags for better compatibility
                    int flags = decorView.getSystemUiVisibility();
                    // Remove light status bar flag to ensure white text on dark background
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    decorView.setSystemUiVisibility(flags);
                }
                
                android.util.Log.d("BaseAppCompatActivity", "Status bar setup completed successfully");
            } catch (Exception e) {
                android.util.Log.e("BaseAppCompatActivity", "Error setting up status bar", e);
                // Fallback to theme-based approach
                android.util.TypedValue typedValue = new android.util.TypedValue();
                if (getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, typedValue, true)) {
                    getWindow().setStatusBarColor(typedValue.data);
                    getWindow().setNavigationBarColor(typedValue.data);
                    android.util.Log.d("BaseAppCompatActivity", "Fallback: Set status bar color from theme");
                }
            }
        }
    }

    private void setupEdgeToEdge() {
        // Only apply edge-to-edge for Android 15+ where we have better control
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Enable edge-to-edge display using WindowCompat for better compatibility
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            
            // Find the root view and apply window insets
            View rootView = findViewById(android.R.id.content);
            if (rootView != null) {
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView, new androidx.core.view.OnApplyWindowInsetsListener() {
                    @Override
                    public androidx.core.view.WindowInsetsCompat onApplyWindowInsets(View v, androidx.core.view.WindowInsetsCompat insets) {
                        androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                        androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
                        
                        // Apply padding to the first child view to avoid content being hidden
                        if (v instanceof ViewGroup) {
                            ViewGroup viewGroup = (ViewGroup) v;
                            if (viewGroup.getChildCount() > 0) {
                                View firstChild = viewGroup.getChildAt(0);
                                // Since we have a colored status bar, we still need some top padding
                                // to ensure content doesn't overlap with status bar in edge-to-edge mode
                                firstChild.setPadding(
                                    systemBars.left,
                                    systemBars.top, // Keep top padding for proper spacing
                                    systemBars.right,
                                    Math.max(systemBars.bottom, ime.bottom)
                                );
                            }
                        }
                        return insets;
                    }
                });
            }
        }
    }
}
