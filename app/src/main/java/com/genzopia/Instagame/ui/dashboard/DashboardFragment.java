package com.genzopia.Instagame.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.databinding.FragmentDashboardBinding;
import com.genzopia.Instagame.reelview.ReelAdapter;
import com.genzopia.Instagame.reelview.ReelItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private ReelAdapter reelAdapter;
    private List<ReelItem> reelItems = new ArrayList<>();
    private RecyclerView reelView;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize RecyclerView
        reelView = root.findViewById(R.id.reel_view);

        // Set layout manager with smooth scrolling disabled for better snap effect
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                getContext(),
                LinearLayoutManager.VERTICAL,
                false
        );
        reelView.setLayoutManager(layoutManager);

        // Add snap helper for Instagram-like snapping
        SnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(reelView);

        // Disable nested scrolling for smoother snapping
        reelView.setNestedScrollingEnabled(false);

        // Create sample data - Replace with your actual data
        createSampleData();

        reelAdapter = new ReelAdapter(requireContext(), reelItems, reelView);
        reelView.setAdapter(reelAdapter);

        return root;
    }


    private void createSampleData() {
        ArrayList<ArrayList<String>> dataList = new ArrayList<>();
        dataList.add(new ArrayList<>(Arrays.asList(
                " https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                "Beautiful Sunset",
                "15K",
                "Enjoying the sunset at the beach",
                "user_123",
                "SECRET_1"
        )));
        dataList.add(new ArrayList<>(Arrays.asList(
                "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev/demoDev/Minecraft%20/videoplayback.mp4",
                "Mountain Hike",
                "22K",
                "Hiking adventure in the Alps",
                "user_456",
                "SECRET_2"
        )));

        for (ArrayList<String> data : dataList) {
            reelItems.add(new ReelItem(data));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (reelAdapter != null) {
            reelAdapter.pausePlayers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (reelAdapter != null) {
            reelAdapter.resumePlayers();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reelAdapter != null) {
            reelAdapter.releaseAllPlayers();
        }
        binding = null;
    }


}