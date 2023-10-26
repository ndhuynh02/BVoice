package com.example.bvoice.fragment;

import android.media.MediaPlayer;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.VideoView;

import com.example.bvoice.PracticeActivity;
import com.example.bvoice.R;

import java.lang.reflect.Field;

public class LearnFragment extends Fragment {
    private VideoView videoView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_learn, container, false);

        videoView = view.findViewById(R.id.videoView);
        videoView.setVideoPath("android.resource://" + getActivity().getPackageName() + "/" + getRawResId(PracticeActivity.word));

//        MediaController mediaController = new MediaController(getActivity());
//        mediaController.setAnchorView(videoView);
//        videoView.setMediaController(mediaController);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setLooping(true);
            }
        });

        videoView.start();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        videoView.start();
    }

    private static int getRawResId(String resName) {
        resName = resName.toLowerCase().replace(" ", "_");
        Class<?> c = R.raw.class;
        try {
            Field idField = c.getDeclaredField(resName);
            return idField.getInt(idField);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}