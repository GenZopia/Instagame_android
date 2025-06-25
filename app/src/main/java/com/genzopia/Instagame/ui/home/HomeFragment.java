package com.genzopia.Instagame.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.genzopia.Instagame.webgl_gameloading.Game_mode;
import com.genzopia.Instagame.databinding.FragmentHomeBinding;

import org.mozilla.geckoview.GeckoSession;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private GeckoSession geckoSession;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        binding.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(getContext(), Game_mode.class);
                startActivity(intent);

            }
        });



        return root;
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}