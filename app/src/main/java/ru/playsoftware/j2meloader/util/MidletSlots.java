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

import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Only the main process writes the slot table (a small text file), every process may read it.
 */
public final class MidletSlots {
	private static final String FILE_NAME = "midlet_slots.txt";
	private static final Class<?>[] SLOTS = {MicroActivity.class, MicroActivity1.class, MicroActivity2.class};
	private static final String[] PROCESS_SUFFIX = {":midlet", ":midlet1", ":midlet2"};

	/** A game currently running in a slot. */
	public static final class Info {
		public final int slot;
		public final String name;
		public final String path;

		Info(int slot, String name, String path) {
			this.slot = slot;
			this.name = name;
			this.path = path;
		}
	}

	/** Human readable result of the last slot choice, shown as a toast in debug builds. */
	public static volatile String lastDebug = "";

	private MidletSlots() {
	}

	public static int count() {
		return SLOTS.length;
	}

	/** Slot number of the given activity class, or -1 if it is not a game slot. */
	public static int slotOf(Class<?> activityClass) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i] == activityClass) {
				return i;
			}
		}
		return -1;
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

	private static File pidFile(Context context, int slot) {
		return new File(context.getFilesDir(), "midlet_slot" + slot + ".pid");
	}

	/** Called by a game process on startup so other processes can tell that it is alive. */
	public static void registerProcess(Context context, int slot) {
		if (slot < 0 || slot >= SLOTS.length) {
			return;
		}
		try (Writer w = new OutputStreamWriter(new FileOutputStream(pidFile(context, slot)), StandardCharsets.UTF_8)) {
			w.write(String.valueOf(android.os.Process.myPid()));
		} catch (IOException ignored) {
			// the ActivityManager check below still works
		}
	}

	private static boolean pidAlive(Context context, int slot) {
		File f = pidFile(context, slot);
		if (!f.isFile()) {
			return false;
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
			String pid = r.readLine();
			if (pid == null) {
				return false;
			}
			File cmd = new File("/proc/" + pid.trim() + "/cmdline");
			if (!cmd.isFile()) {
				return false;
			}
			try (BufferedReader c = new BufferedReader(new InputStreamReader(new FileInputStream(cmd), StandardCharsets.UTF_8))) {
				String name = c.readLine();
				// the cmdline is NUL terminated; compare with the expected process name
				return name != null && name.startsWith(context.getPackageName() + PROCESS_SUFFIX[slot]);
			}
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Which slots have a live game process right now. */
	private static boolean[] aliveSlots(Context context) {
		Set<String> byManager = runningProcesses(context);
		String pkg = context.getPackageName();
		boolean[] alive = new boolean[SLOTS.length];
		for (int i = 0; i < alive.length; i++) {
			alive[i] = byManager.contains(pkg + PROCESS_SUFFIX[i]) || pidAlive(context, i);
		}
		return alive;
	}

	private static File tableFile(Context context) {
		return new File(context.getFilesDir(), FILE_NAME);
	}

	/** Reads the slot table: one entry (or null) per slot. */
	private static Info[] readTable(Context context) {
		Info[] table = new Info[SLOTS.length];
		File f = tableFile(context);
		if (!f.isFile()) {
			return table;
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				String[] p = line.split("\t", 3);
				if (p.length < 3) {
					continue;
				}
				int slot = Integer.parseInt(p[0]);
				if (slot >= 0 && slot < table.length) {
					table[slot] = new Info(slot, p[1], p[2]);
				}
			}
		} catch (IOException | NumberFormatException ignored) {
			// a broken table is treated as empty
		}
		return table;
	}

	private static void writeTable(Context context, Info[] table) {
		File f = tableFile(context);
		File tmp = new File(f.getParentFile(), FILE_NAME + ".tmp");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
			for (Info i : table) {
				if (i != null) {
					w.write(i.slot + "\t" + i.name + "\t" + i.path + "\n");
				}
			}
		} catch (IOException e) {
			return;
		}
		//noinspection ResultOfMethodCallIgnored
		tmp.renameTo(f);
	}

	private static String cleanName(@Nullable String name, String path) {
		String n = name;
		if (n == null || n.trim().isEmpty()) {
			n = Uri.parse(path).getLastPathSegment();
		}
		if (n == null) {
			n = "?";
		}
		return n.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
	}

	/** Games that are running right now, ordered by slot. Safe to call from any process. */
	@NonNull
	public static List<Info> running(Context context) {
		Info[] table = readTable(context);
		boolean[] alive = aliveSlots(context);
		List<Info> result = new ArrayList<>();
		for (int i = 0; i < table.length; i++) {
			if (table[i] != null && alive[i]) {
				result.add(table[i]);
			}
		}
		return result;
	}

	/** True if at least one slot is not occupied. */
	public static boolean hasFreeSlot(Context context) {
		return running(context).size() < SLOTS.length;
	}

	/**
	 * Builds the intent that starts (or brings back) the MIDlet at {@code data}.
	 * Call from the main process only.
	 *
	 * @param action may be null
	 * @param name   display name of the game, shown on the tab
	 * @return the intent, or null when every slot is occupied by another game
	 */
	@Nullable
	public static Intent createIntent(Context context, @Nullable String action, Uri data, @Nullable String name) {
		String path = data.toString();
		Info[] table = readTable(context);
		boolean[] alive = aliveSlots(context);
		int free = -1;
		int resume = -1;
		for (int i = 0; i < SLOTS.length; i++) {
			boolean isAlive = alive[i];
			if (isAlive) {
				if (table[i] != null && path.equals(table[i].path)) {
					resume = i;
					break;
				}
			} else if (free < 0) {
				free = i;
			}
		}
		int slot = resume >= 0 ? resume : free;
		lastDebug = "slot " + slot + (resume >= 0 ? " (resume)" : "") + ", running: "
				+ java.util.Arrays.toString(alive);
		if (slot < 0) {
			return null;
		}
		if (resume < 0) {
			table[slot] = new Info(slot, cleanName(name, path), path);
			writeTable(context, table);
		}
		Intent intent = action == null ? new Intent(context, SLOTS[slot])
				: new Intent(action, data, context, SLOTS[slot]);
		intent.setData(data);
		// every slot lives in its own task (see taskAffinity in the manifest), so the game list
		// stays reachable while a game is running and the user can start another one
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if (resume >= 0) {
			// the game is already running: just bring it back to the front
			intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		}
		return intent;
	}

	/** Intent that brings an already running game to the front (used by the tab bar). */
	public static Intent switchIntent(Context context, Info info) {
		Intent intent = new Intent(context, SLOTS[info.slot]);
		intent.setData(Uri.parse(info.path));
		intent.putExtra(KEY_MIDLET_NAME, info.name);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return intent;
	}

	/** Intent that opens the game list so another game can be started. */
	@Nullable
	public static Intent appListIntent(Context context) {
		Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
		if (intent != null) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		return intent;
	}
}
