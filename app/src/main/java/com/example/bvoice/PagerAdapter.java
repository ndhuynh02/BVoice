package com.example.bvoice;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.bvoice.fragment.LearnFragment;
import com.example.bvoice.fragment.PracticeFragment;

public class PagerAdapter extends FragmentStateAdapter {
    public PagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new LearnFragment();
            case 1:
                return new PracticeFragment();
            default:
                return new LearnFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
