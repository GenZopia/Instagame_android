package com.genzopia.Instagame.ui.dashboard;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import com.genzopia.Instagame.reelview.ReelRepository;
import com.genzopia.Instagame.vertical_recylerview_custom.TempStorage;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private ReelAdapter reelAdapter;
    private List<ReelItem> reelItems = new ArrayList<>();
    private RecyclerView reelView;
    private ReelRepository reelRepository;
    private boolean isLoadingMore = false;
    private boolean hasMore = true;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        reelView = root.findViewById(R.id.reel_view);

        LinearLayoutManager layoutManager = new LinearLayoutManager(
                getContext(),
                LinearLayoutManager.VERTICAL,
                false
        );
        if (TempStorage.videoId != null) {
            Toast.makeText(requireContext(), TempStorage.videoId, Toast.LENGTH_SHORT).show();
            TempStorage.videoId = null; // Clear after use
        }
        reelView.setLayoutManager(layoutManager);

        SnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(reelView);

        reelView.setNestedScrollingEnabled(false);

        // Initialize adapter with empty list
        reelAdapter = new ReelAdapter(requireContext(), reelItems, reelView);
        reelView.setAdapter(reelAdapter);

        // Add scroll listener for smooth video transitions
        reelView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (reelAdapter != null) {
                    reelAdapter.handleScrollStateChange(newState);
                }
            }
        });

        reelRepository = new ReelRepository();
        loadMoreReels();

        // Preload follow states for better performance
        if (reelAdapter != null) {
            reelAdapter.preloadFollowStates();
        }

        // Add scroll listener for lazy loading
        reelView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && hasMore && !isLoadingMore) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 2 && firstVisibleItemPosition >= 0) {
                        loadMoreReels();
                    }
                }
            }
        });

        return root;
    }

    private void loadMoreReels() {
        isLoadingMore = true;
        reelRepository.fetchReelsPage(new ReelRepository.ReelDataCallback() {
            @Override
            public void onReelsLoaded(List<ReelItem> reels) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    int oldSize = reelItems.size();
                    reelItems.addAll(reels);
                    if (reelAdapter != null) {
                        reelAdapter.notifyItemRangeInserted(oldSize, reels.size());
                        // Preload follow states for new reels
                        reelAdapter.preloadFollowStates();
                        // If this is the initial load, play the first video
                        if (oldSize == 0 && reels.size() > 0) {
                            reelView.post(() -> reelAdapter.ensureOnlyCurrentVideoPlays());
                        }
                    }
                    isLoadingMore = false;
                    hasMore = reelRepository.hasMore();
                });
            }
            @Override
            public void onError(String errorMessage) {
                isLoadingMore = false;
                if (getContext() != null && isAdded()) {
                    Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
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
            // Add a small delay to ensure the view is properly attached
            reelView.postDelayed(() -> {
                reelAdapter.ensureOnlyCurrentVideoPlays();
            }, 100); // 100ms delay
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (reelAdapter != null) {
            reelAdapter.pausePlayers();
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
