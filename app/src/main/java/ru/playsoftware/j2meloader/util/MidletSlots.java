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

package ru.playsoftware.j2meloader.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.microedition.shell.MicroActivity;
import javax.microedition.shell.MicroActivity1;
import javax.microedition.shell.MicroActivity2;

/**
 * Chooses which game slot (= MicroActivity subclass living in its own process)
 * a MIDlet should be started in, so that several games can run at once.
 *
 * Slot 0 is the original MicroActivity (process ":midlet"), slots 1 and 2 are extra.
 * Only the main process calls this class, so plain SharedPreferences are safe here.
 */
public final class MidletSlots {
	private static final String PREFS = "midlet_slots";
	private static final Class<?>[] SLOTS = {MicroActivity.class, MicroActivity1.class, MicroActivity2.class};
	private static final String[] PROCESS_SUFFIX = {":midlet", ":midlet1", ":midlet2"};

	private MidletSlots() {
	}

	public static int count() {
		return SLOTS.length;
	}

	private static Set<String> runningProcesses(Context context) {
		Set<String> result = new HashSet<>();
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningAppProcessInfo> list = am == null ? null : am.getRunningAppProcesses();
		if (list != null) {
			for (ActivityManager.RunningAppProcessInfo info : list) {
				result.add(info.processName);
			}
		}
		return result;
	}

	/**
	 * Builds the intent that starts (or brings back) the MIDlet at {@code data}.
	 *
	 * @param action may be null
	 * @return the intent, or null when every slot is occupied by another game
	 */
	@Nullable
	public static Intent createIntent(Context context, @Nullable String action, Uri data) {
		String path = data.toString();
		SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		Set<String> running = runningProcesses(context);
		String pkg = context.getPackageName();
		int free = -1;
		int resume = -1;
		for (int i = 0; i < SLOTS.length; i++) {
			boolean alive = running.contains(pkg + PROCESS_SUFFIX[i]);
			if (alive) {
				if (path.equals(sp.getString("slot" + i, null))) {
					resume = i;
					break;
				}
			} else if (free < 0) {
				free = i;
			}
		}
		int slot = resume >= 0 ? resume : free;
		if (slot < 0) {
			return null;
		}
		if (resume < 0) {
			sp.edit().putString("slot" + slot, path).apply();
		}
		Intent intent = action == null ? new Intent(context, SLOTS[slot])
				: new Intent(action, data, context, SLOTS[slot]);
		intent.setData(data);
		if (slot > 0) {
			// extra slots live in their own task (see taskAffinity in the manifest)
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		} else if (resume >= 0) {
			// the game is already running in the original slot: just bring it to the front
			intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		}
		return intent;
	}
}
