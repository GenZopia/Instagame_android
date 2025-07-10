package com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview;

import android.content.Context;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class StoryGridLayoutManager extends GridLayoutManager {
    
    public StoryGridLayoutManager(Context context) {
        // Create a grid with 2 rows, scrolling horizontally
        super(context, 2, RecyclerView.HORIZONTAL, false);
    }

    @Override
    public boolean canScrollVertically() {
        // Disable vertical scrolling
        return false;
    }
} 