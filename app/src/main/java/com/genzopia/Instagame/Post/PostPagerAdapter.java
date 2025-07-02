package com.genzopia.Instagame.Post;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class PostPagerAdapter extends FragmentStateAdapter {
    public PostPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new VideosFragment();
            case 1: return new ShortsFragment();
            default: return new VideosFragment();

        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}

