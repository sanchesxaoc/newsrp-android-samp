package com.xyron.game.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.xyron.game.R;

public class IntroActivity extends AppCompatActivity {

    private static final long FADE_IN_MS  = 1000;
    private static final long HOLD_MS     = 1500;
    private static final long FADE_OUT_MS = 900;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_intro);

        ImageView logo = findViewById(R.id.intro_logo);
        playIntro(logo);
    }

    private void playIntro(ImageView logo) {
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f);
        fadeIn.setDuration(FADE_IN_MS);

        fadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                logo.postDelayed(() -> {
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(logo, View.ALPHA, 1f, 0f);
                    fadeOut.setDuration(FADE_OUT_MS);
                    fadeOut.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            goNext();
                        }
                    });
                    fadeOut.start();
                }, HOLD_MS);
            }
        });

        fadeIn.start();
    }

    private void goNext() {
        startActivity(new Intent(this, EntryActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }
}
