package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * One native control surface for the browser HLS engine, styled to follow
 * Pipe's player conventions. The engine owns transport; this view owns UI.
 * This class intentionally has no provider-specific logic.
 */
final class SupjavPlayerControls extends FrameLayout {
    interface Actions {
        void close();
        void playPause();
        void seek(int seconds);
        void seekToFraction(float fraction);
        void setSpeed(float speed);
        void cycleQuality();
        void toggleFullscreen();
    }

    private final TextView play;
    private final TextView clock;
    private final TextView quality;
    private final TextView speed;
    private final SeekBar timeline;
    private final LinearLayout bottom;
    private final LinearLayout top;
    private boolean scrubbing;
    private float playbackSpeed = 1f;
    private boolean playing;

    SupjavPlayerControls(final Context context, final Actions actions) {
        super(context);
        setClickable(false);
        setFocusable(false);
        top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        top.setBackgroundColor(0x8C000000);
        final TextView back = action(context, "‹", () -> actions.close());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));
        final TextView title = new TextView(context);
        title.setText("Pipe · Video");
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        addView(top, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));

        bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12), dp(12), dp(12), dp(16));
        bottom.setBackgroundColor(0xA8000000);
        timeline = new SeekBar(context);
        timeline.setMax(1000);
        timeline.setProgress(0);
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(final SeekBar bar, final int value,
                                                      final boolean fromUser) { }
            @Override public void onStartTrackingTouch(final SeekBar bar) { scrubbing = true; }
            @Override public void onStopTrackingTouch(final SeekBar bar) {
                scrubbing = false;
                actions.seekToFraction(bar.getProgress() / 1000f);
            }
        });
        bottom.addView(timeline, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(40)));
        final LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        play = action(context, "▶", () -> actions.playPause());
        row.addView(action(context, "−10", () -> actions.seek(-10)));
        row.addView(play);
        row.addView(action(context, "+10", () -> actions.seek(10)));
        clock = new TextView(context);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(12);
        clock.setText("0:00 / 0:00");
        clock.setGravity(Gravity.CENTER);
        row.addView(clock, new LinearLayout.LayoutParams(0, dp(44), 1));
        speed = action(context, "1×", () -> {
            playbackSpeed = playbackSpeed == 1f ? 1.25f
                    : playbackSpeed == 1.25f ? 1.5f
                    : playbackSpeed == 1.5f ? 2f : playbackSpeed == 2f ? .75f : 1f;
            speed.setText(String.format(Locale.ROOT, "%s×", playbackSpeed));
            actions.setSpeed(playbackSpeed);
        });
        row.addView(speed);
        quality = action(context, "Auto", () -> actions.cycleQuality());
        row.addView(quality);
        row.addView(action(context, "⛶", () -> actions.toggleFullscreen()));
        bottom.addView(row, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(48)));
        addView(bottom, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    void updatePosition(final double position, final double duration,
                        final boolean paused) {
        playing = !paused;
        play.setText(playing ? "❚❚" : "▶");
        if (Double.isFinite(duration) && duration > 0 && !scrubbing) {
            timeline.setProgress((int) Math.round(
                    Math.max(0, Math.min(1, position / duration)) * 1000));
        }
        clock.setText(formatTime(position) + " / " + formatTime(duration));
    }

    void setQualityLabel(final String label) {
        quality.setText(label);
    }

    private static String formatTime(final double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return "0:00";
        final long value = (long) seconds;
        return String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60);
    }

    private TextView action(final Context context, final String label, final Runnable onClick) {
        final TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(6), 0, dp(6), 0);
        view.setOnClickListener(v -> onClick.run());
        view.setClickable(true);
        return view;
    }

    private int dp(final int n) {
        return (int) (getResources().getDisplayMetrics().density * n + .5f);
    }
}
