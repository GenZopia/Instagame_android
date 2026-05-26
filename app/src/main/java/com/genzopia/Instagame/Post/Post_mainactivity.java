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
