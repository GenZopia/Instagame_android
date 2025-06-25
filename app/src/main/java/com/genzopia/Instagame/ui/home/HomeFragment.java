package com.genzopia.Instagame.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.genzopia.Instagame.databinding.FragmentHomeBinding;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private GeckoSession geckoSession;
    private GeckoRuntime geckoRuntime;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        GeckoView geckoView = binding.geckoView;  // Make sure geckoView is in fragment_home.xml
        geckoSession = new GeckoSession();
        geckoRuntime = GeckoRuntime.create(requireContext());

        geckoSession.open(geckoRuntime);
        geckoView.setSession(geckoSession);

        // Load any website
        geckoSession.loadUri("https://www.google.com");

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
