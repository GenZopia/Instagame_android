package com.genzopia.Instagame.Post;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.genzopia.Instagame.common.BaseActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.genzopia.Instagame.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;


public class Post_mainactivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_post_mainactivity);
        TabLayout tabLayout = findViewById(R.id.custom_bottom_tabs);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        // Apply navigation bar padding to the bottom tab so it sits above the gesture bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(tabLayout, (v, insets) -> {
            androidx.core.graphics.Insets navInsets = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars());
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            // 16dp base margin + nav bar height so the tab floats above the gesture bar
            int baseDp = (int) (16 * getResources().getDisplayMetrics().density);
            lp.bottomMargin = baseDp + navInsets.bottom;
            v.setLayoutParams(lp);
            return insets;
        });

        // Set adapter
        PostPagerAdapter adapter = new PostPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Set tab titles
        String[] titles = {"Upload", "Live"};

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(titles[position])
        ).attach();
    }
}
