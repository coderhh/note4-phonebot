package com.hack.note4.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Minimal dark home-screen launcher built from source for a rooted Galaxy Note 4.
 *  Black background, a thin clock + date at the top, a scrolling grid of apps below. */
public class LauncherActivity extends Activity {

    private TextView clock;
    private TextView date;
    private AppGridAdapter adapter;
    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(tick, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Explicitly opaque so the TV/last-app picture never bleeds through on odd firmware.
        getWindow().setBackgroundDrawableResource(android.R.color.black);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FF050608"));
        root.setPadding(dp(28), dp(40), dp(28), dp(20));

        // Clock block, top-left.
        clock = new TextView(this);
        clock.setTextColor(0xFFFFFFFF);
        clock.setTextSize(86);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setLineSpacing(0, 0.9f);

        date = new TextView(this);
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(20);
        date.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        date.setPadding(0, dp(2), 0, 0);

        LinearLayout clockBlock = new LinearLayout(this);
        clockBlock.setOrientation(LinearLayout.VERTICAL);
        clockBlock.addView(clock);
        clockBlock.addView(date);

        // App grid.
        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setColumnWidth(dp(96));
        grid.setVerticalSpacing(dp(28));
        grid.setHorizontalSpacing(dp(12));
        grid.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);
        grid.setGravity(Gravity.CENTER);
        adapter = new AppGridAdapter(this, loadApps());
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppEntry a = adapter.getItem(position);
                if (a == null) return;
                Intent it = getPackageManager().getLaunchIntentForPackage(a.pkg);
                if (it != null) {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                }
            }
        });

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridLp.topMargin = dp(24);
        root.addView(clockBlock);
        root.addView(grid, gridLp);

        setContentView(root);
    }

    private List<AppEntry> loadApps() {
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = getPackageManager().queryIntentActivities(main, 0);
        List<AppEntry> out = new ArrayList<AppEntry>();
        for (ResolveInfo ri : ris) {
            // Don't list ourselves on the home grid.
            if (ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName)) continue;
            AppEntry e = new AppEntry();
            e.label = ri.loadLabel(getPackageManager()).toString();
            e.pkg = ri.activityInfo.packageName;
            e.icon = ri.loadIcon(getPackageManager());
            out.add(e);
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private void updateClock() {
        Date now = new Date();
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        clock.setText(tf.format(now));
        date.setText(df.format(now));
    }

    @Override protected void onResume() {
        super.onResume();
        updateClock();
        handler.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class AppEntry {
        String label;
        String pkg;
        Drawable icon;
    }

    private static class AppGridAdapter extends BaseAdapter {
        private final Context ctx;
        private final List<AppEntry> apps;
        AppGridAdapter(Context c, List<AppEntry> a) { ctx = c; apps = a; }
        @Override public int getCount() { return apps.size(); }
        @Override public AppEntry getItem(int p) { return p < apps.size() ? apps.get(p) : null; }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View convert, ViewGroup parent) {
            LinearLayout cell;
            if (convert == null) {
                cell = new LinearLayout(ctx);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                int pad = (int)(8 * ctx.getResources().getDisplayMetrics().density + 0.5f);
                cell.setPadding(pad, pad, pad, pad);
                ImageView icon = new ImageView(ctx);
                icon.setId(1);
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp2(ctx, 64), dp2(ctx, 64)));
                TextView label = new TextView(ctx);
                label.setId(2);
                label.setTextColor(0xCCFFFFFF);
                label.setTextSize(12);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setPadding(0, (int)(4 * ctx.getResources().getDisplayMetrics().density + 0.5f), 0, 0);
                cell.addView(icon);
                cell.addView(label);
            } else {
                cell = (LinearLayout) convert;
            }
            AppEntry e = apps.get(p);
            ((ImageView) cell.findViewById(1)).setImageDrawable(e.icon);
            ((TextView) cell.findViewById(2)).setText(e.label);
            return cell;
        }
        private static int dp2(Context c, int v) {
            return (int)(v * c.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
