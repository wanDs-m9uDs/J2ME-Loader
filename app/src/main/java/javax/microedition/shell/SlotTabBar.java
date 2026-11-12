/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.shell;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import ru.playsoftware.j2meloader.util.MidletSlots;

/**
 * Small floating "tab bar" drawn over a running game. It is shown only while at least two
 * games are running, lists them as tabs and switches between them when a tab is tapped.
 * The first chip collapses the bar, the last "+" chip opens the game list to start another game.
 */
class SlotTabBar {
	private static final long REFRESH_MS = 1500;

	private final Activity activity;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final LinearLayout bar;
	private final int currentSlot;
	private boolean collapsed;
	private String lastKey = "";

	private final Runnable tick = new Runnable() {
		@Override
		public void run() {
			refresh();
			handler.postDelayed(this, REFRESH_MS);
		}
	};

	SlotTabBar(Activity activity) {
		this.activity = activity;
		this.currentSlot = MidletSlots.slotOf(activity.getClass());
		this.bar = new LinearLayout(activity);
		this.bar.setOrientation(LinearLayout.HORIZONTAL);
		this.bar.setVisibility(View.GONE);
	}

	private int dp(int v) {
		return Math.round(v * activity.getResources().getDisplayMetrics().density);
	}

	void attach() {
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
				Gravity.TOP | Gravity.CENTER_HORIZONTAL);
		int statusBar = 0;
		int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (id > 0) {
			statusBar = activity.getResources().getDimensionPixelSize(id);
		}
		lp.topMargin = statusBar + dp(4);
		activity.addContentView(bar, lp);
	}

	void start() {
		handler.removeCallbacks(tick);
		lastKey = "";
		tick.run();
	}

	void stop() {
		handler.removeCallbacks(tick);
	}

	private TextView chip(String text, boolean active, View.OnClickListener click) {
		TextView t = new TextView(activity);
		t.setText(text);
		t.setTextColor(Color.WHITE);
		t.setTextSize(12);
		t.setSingleLine(true);
		t.setEllipsize(TextUtils.TruncateAt.END);
		t.setMaxWidth(dp(120));
		t.setGravity(Gravity.CENTER);
		t.setPadding(dp(10), dp(5), dp(10), dp(5));
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(dp(14));
		bg.setColor(active ? 0xDD378ADD : 0x99000000);
		t.setBackground(bg);
		t.setOnClickListener(click);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = dp(4);
		t.setLayoutParams(lp);
		return t;
	}

	private void refresh() {
		List<MidletSlots.Info> games = MidletSlots.running(activity);
		if (games.size() < 2) {
			bar.setVisibility(View.GONE);
			lastKey = "";
			return;
		}
		boolean hasFree = games.size() < MidletSlots.count();
		StringBuilder key = new StringBuilder();
		key.append(collapsed).append(hasFree);
		for (MidletSlots.Info g : games) {
			key.append('|').append(g.slot).append(':').append(g.name);
		}
		if (key.toString().equals(lastKey)) {
			return;
		}
		lastKey = key.toString();
		bar.removeAllViews();
		bar.addView(chip(collapsed ? "›" : "‹", false, v -> {
			collapsed = !collapsed;
			refresh();
		}));
		if (!collapsed) {
			for (MidletSlots.Info g : games) {
				boolean active = g.slot == currentSlot;
				bar.addView(chip(g.name, active, v -> {
					if (!active) {
						activity.startActivity(MidletSlots.switchIntent(activity, g));
					}
				}));
			}
			if (hasFree) {
				bar.addView(chip("+", false, v -> {
					Intent list = MidletSlots.appListIntent(activity);
					if (list != null) {
						activity.startActivity(list);
					}
				}));
			}
		}
		bar.setVisibility(View.VISIBLE);
	}
}
