package com.genzopia.Instagame.ui.profile;



import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.databinding.ActivityFullScreenImageBinding;


public class FullScreenImageActivity extends AppCompatActivity {

    private ActivityFullScreenImageBinding binding;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityFullScreenImageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Retrieve the image URL from the intent
        Intent intent = getIntent();
        String imageUrl = intent.getStringExtra("image");

        // Load the image using Glide
        Glide.with(this)
                .load(imageUrl)
                .error(R.drawable.profile)
                .into(binding.photoView);
    }
}

