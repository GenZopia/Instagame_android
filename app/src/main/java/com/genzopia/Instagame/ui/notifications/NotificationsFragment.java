package com.genzopia.Instagame.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.genzopia.Instagame.MainActivity;
import com.genzopia.Instagame.Post.Post_mainactivity;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.databinding.FragmentNotificationsBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;


public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        BottomNavigationView navView = ((MainActivity) getContext() ).findViewById(R.id.nav_view);
        navView.setSelectedItemId(R.id.navigation_home);
        Intent intent=new Intent(getContext(), Post_mainactivity.class);
        startActivity(intent);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}