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

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Per-game snapshots of the save data directory (RMS and the game's file system).
 * A snapshot is a plain zip file, one per save, so the user can keep several.
 */
public final class DataSnapshots {
	private static final String EXT = ".zip";

	private DataSnapshots() {
	}

	/**
	 * Zips the content of {@code dataDir} into a new file in {@code snapshotsDir}.
	 *
	 * @param prefix optional name prefix, e.g. "before-restore"; may be null
	 */
	@NonNull
	public static File create(File dataDir, File snapshotsDir, String prefix) throws IOException {
		if (!snapshotsDir.isDirectory() && !snapshotsDir.mkdirs()) {
			throw new IOException("Can't create " + snapshotsDir);
		}
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
		File out = new File(snapshotsDir, (prefix == null ? "" : prefix + "-") + stamp + EXT);
		try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
			addDir(zos, dataDir, "");
		} catch (IOException | RuntimeException e) {
			//noinspection ResultOfMethodCallIgnored
			out.delete();
			throw e;
		}
		return out;
	}

	private static void addDir(ZipOutputStream zos, File dir, String path) throws IOException {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		byte[] buf = new byte[8192];
		for (File f : files) {
			String name = path + f.getName();
			if (f.isDirectory()) {
				addDir(zos, f, name + "/");
			} else {
				zos.putNextEntry(new ZipEntry(name));
				try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
					int n;
					while ((n = in.read(buf)) > 0) {
						zos.write(buf, 0, n);
					}
				}
				zos.closeEntry();
			}
		}
	}

	/** Snapshots of one game, newest first. Never null. */
	@NonNull
	public static File[] list(File snapshotsDir) {
		File[] files = snapshotsDir.listFiles((f) -> f.isFile() && f.getName().endsWith(EXT));
		if (files == null) {
			return new File[0];
		}
		Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
		return files;
	}

	/** Replaces the content of {@code dataDir} with the content of the snapshot. */
	public static void restore(File snapshot, File dataDir) throws IOException {
		String root = dataDir.getCanonicalPath() + File.separator;
		FileUtils.clearDirectory(dataDir);
		byte[] buf = new byte[8192];
		try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(snapshot)))) {
			ZipEntry e;
			while ((e = zis.getNextEntry()) != null) {
				File target = new File(dataDir, e.getName());
				// protect against "../" paths inside the zip
				if (!target.getCanonicalPath().startsWith(root)) {
					throw new IOException("Bad entry in snapshot: " + e.getName());
				}
				if (e.isDirectory()) {
					//noinspection ResultOfMethodCallIgnored
					target.mkdirs();
					continue;
				}
				File parent = target.getParentFile();
				if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
					throw new IOException("Can't create " + parent);
				}
				try (OutputStream os = new BufferedOutputStream(new FileOutputStream(target))) {
					int n;
					while ((n = zis.read(buf)) > 0) {
						os.write(buf, 0, n);
					}
				}
			}
		}
	}
}
