package io.horizonvm.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Process;
import android.provider.OpenableColumns;
import android.system.Os;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Vulkan 드라이버 선택/설치 UI. MainActivity smali 에서 attach / onResult / afterEnv 3곳만 호출한다. */
public final class DriverManager {
    static final int REQ = 101;
    static final String KEY = "vk_driver"; // "" = 시스템 기본, "bundled" = APK 내장, 그 외 = files/drivers/<이름>
    static final int C_TXT = -1643792, C_DIM = -7694685, C_ACC = -9459492, C_CARD = -14998997;
    static final long MAX_BYTES = 256L << 20;

    static Activity act;
    static SharedPreferences prefs;
    static Spinner sp;
    static TextView status;
    static final List<String[]> items = new ArrayList<>(); // {value, label}
    static String appliedDriver = null; // 이 프로세스에서 VM 이 처음 시작될 때의 선택 (Vulkan 은 프로세스당 1회 로드)
    static String lastSet = null;
    static boolean setBcn = false;
    static boolean suppress = false;

    static int dp(float v) { return Math.round(v * act.getResources().getDisplayMetrics().density); }

    static File root() { File f = new File(act.getFilesDir(), "drivers"); f.mkdirs(); return f; }

    static boolean hasBundled() { return new File(act.getApplicationInfo().nativeLibraryDir, "libvulkan_freedreno.so").exists(); }

    static String metaName(File dir) {
        try {
            String m = new String(readAll(new java.io.FileInputStream(new File(dir, "meta.json")), 1 << 16), "UTF-8");
            String n = jsonStr(m, "name");
            String v = jsonStr(m, "driverVersion");
            if (n != null) return v != null ? n + " (" + v + ")" : n;
        } catch (Exception ignored) { }
        return dir.getName();
    }

    static String jsonStr(String j, String key) {
        int k = j.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int c = j.indexOf(':', k);
        if (c < 0) return null;
        int a = j.indexOf('"', c);
        if (a < 0) return null;
        int b = j.indexOf('"', a + 1);
        return b < 0 ? null : j.substring(a + 1, b);
    }

    static byte[] readAll(InputStream in, int max) throws IOException {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) { o.write(b, 0, n); if (o.size() > max) throw new IOException("too large"); }
            return o.toByteArray();
        } finally { in.close(); }
    }

    static String sanitize(String s) {
        String r = s.replaceAll("[^A-Za-z0-9._-]", "_");
        while (r.startsWith(".")) r = r.substring(1);
        if (r.length() > 64) r = r.substring(0, 64);
        return r.isEmpty() ? "driver" : r;
    }

    static void reload() {
        items.clear();
        items.add(new String[]{"", "기본 (시스템 드라이버)"});
        if (hasBundled()) items.add(new String[]{"bundled", "내장: Turnip (APK 포함)"});
        File[] ds = root().listFiles();
        if (ds != null) {
            Arrays.sort(ds);
            for (File d : ds) if (d.isDirectory() && !d.getName().startsWith(".")) items.add(new String[]{d.getName(), "설치됨: " + metaName(d)});
        }
    }

    static void fillSpinner(String select) {
        reload();
        String[] labels = new String[items.size()];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) { labels[i] = items.get(i)[1]; if (items.get(i)[0].equals(select)) sel = i; }
        suppress = true;
        sp.setAdapter((SpinnerAdapter) new ArrayAdapter<String>(act, android.R.layout.simple_spinner_dropdown_item, labels));
        sp.setSelection(sel, false);
        suppress = false;
        updateStatus();
    }

    static String selected() { return prefs.getString(KEY, ""); }

    static void updateStatus() {
        String v = selected();
        String s = v.isEmpty() ? "시스템 Vulkan 드라이버 사용" : ("사용자 드라이버: " + v);
        if (appliedDriver != null && !appliedDriver.equals(v)) s += "\n※ 변경 사항은 앱을 다시 시작하면 적용됩니다";
        else s += "  ·  다음 시작부터 적용";
        status.setText(s);
    }

    static Button btn(String t, final Runnable r) {
        Button b = new Button(act);
        b.setText(t); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(C_TXT); b.setGravity(8388627);
        GradientDrawable g = new GradientDrawable(); g.setColor(C_CARD); g.setCornerRadius(dp(10));
        b.setBackground(g); b.setPadding(dp(16), dp(10), dp(16), dp(10)); b.setStateListAnimator(null);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return b;
    }

    /** buildSetup() 직후 호출: 왼쪽 패널(실행 섹션 위)에 드라이버 섹션 삽입. */
    public static void attach(Activity a) {
        try {
            act = a;
            prefs = a.getSharedPreferences("hvm", 0);
            java.lang.reflect.Field f = a.getClass().getDeclaredField("setup");
            f.setAccessible(true);
            ViewGroup setup = (ViewGroup) f.get(a);
            ViewGroup scroll = (ViewGroup) setup.getChildAt(0);
            LinearLayout left = (LinearLayout) scroll.getChildAt(0);

            TextView head = new TextView(a);
            head.setText("Vulkan 드라이버"); head.setTextSize(13); head.setTextColor(C_ACC); head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setPadding(0, dp(14), 0, dp(4));
            sp = new Spinner(a);
            status = new TextView(a); status.setTextSize(12); status.setTextColor(C_DIM);
            LinearLayout row = new LinearLayout(a); row.setOrientation(0);
            Button install = btn("드라이버 파일 선택 · 설치", new Runnable() { public void run() { pick(); } });
            Button del = btn("삭제", new Runnable() { public void run() { deleteSelected(); } });
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1f);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2); lp2.leftMargin = dp(6);
            row.addView(install, lp1); row.addView(del, lp2);
            LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-1, -2); m.topMargin = dp(6);

            int idx = Math.min(2, left.getChildCount());
            left.addView(head, idx++);
            left.addView(sp, idx++, m);
            left.addView(row, idx++, m);
            left.addView(status, idx, m);

            fillSpinner(selected());
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (suppress) return;
                    prefs.edit().putString(KEY, items.get(pos)[0]).apply();
                    updateStatus();
                    if (appliedDriver != null && !appliedDriver.equals(items.get(pos)[0])) askRestart();
                }
                public void onNothingSelected(AdapterView<?> p) { }
            });
        } catch (Throwable t) {
            android.util.Log.w("HVMVK", "driver UI attach failed", t);
        }
    }

    static void askRestart() {
        new AlertDialog.Builder(act).setTitle("드라이버 변경")
            .setMessage("Vulkan 드라이버는 프로세스당 한 번만 로드됩니다. 앱을 종료하면 다음 실행부터 새 드라이버가 적용됩니다.")
            .setPositiveButton("앱 종료", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { act.finishAndRemoveTask(); Process.killProcess(Process.myPid()); }
            }).setNegativeButton("나중에", null).show();
    }

    static void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        act.startActivityForResult(i, REQ);
    }

    static void deleteSelected() {
        int pos = sp.getSelectedItemPosition();
        if (pos < 0 || pos >= items.size()) return;
        final String v = items.get(pos)[0];
        if (v.isEmpty() || v.equals("bundled")) { Toast.makeText(act, "기본/내장 드라이버는 삭제할 수 없습니다", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(act).setTitle("삭제").setMessage(items.get(pos)[1] + " 를 삭제할까요?")
            .setPositiveButton("삭제", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    rmTree(new File(root(), v));
                    prefs.edit().putString(KEY, "").apply();
                    fillSpinner("");
                    if (appliedDriver != null && !appliedDriver.isEmpty()) askRestart();
                }
            }).setNegativeButton("취소", null).show();
    }

    static void rmTree(File f) {
        File[] c = f.listFiles();
        if (c != null) for (File x : c) rmTree(x);
        f.delete();
    }

    /** MainActivity.onActivityResult 에서 호출. 처리했으면 true. */
    public static boolean onResult(final Activity a, int req, int res, final Intent data) {
        if (req != REQ) return false;
        if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
            act = a;
            final Uri uri = data.getData();
            Toast.makeText(a, "드라이버 설치 중…", Toast.LENGTH_SHORT).show();
            new Thread(new Runnable() { public void run() {
                String msg, name = null;
                try { name = install(uri); msg = "설치 완료: " + name; }
                catch (Throwable t) { msg = "설치 실패: " + t.getMessage(); }
                final String fm = msg, fn = name;
                a.runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(a, fm, Toast.LENGTH_LONG).show();
                    if (fn != null) {
                        prefs.edit().putString(KEY, fn).apply();
                        fillSpinner(fn);
                        if (appliedDriver != null && !appliedDriver.equals(fn)) askRestart();
                    }
                } });
            } }).start();
        }
        return true;
    }

    static String displayName(Uri u) {
        try (Cursor c = act.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        String s = u.getLastPathSegment();
        return s != null ? s : "driver.so";
    }

    /** zip(meta.json + .so) 또는 단일 .so 설치. 반환: files/drivers 아래 폴더 이름. */
    static String install(Uri uri) throws Exception {
        BufferedInputStream in = new BufferedInputStream(act.getContentResolver().openInputStream(uri), 1 << 16);
        in.mark(8);
        byte[] h = new byte[4];
        int n = in.read(h);
        in.reset();
        File tmp = new File(root(), ".tmp_" + System.nanoTime());
        tmp.mkdirs();
        try {
            if (n == 4 && h[0] == 'P' && h[1] == 'K') {
                ZipInputStream z = new ZipInputStream(in);
                ZipEntry e; long total = 0;
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String base = e.getName();
                    base = base.substring(base.lastIndexOf('/') + 1);   // 경로 이탈(zip slip) 방지: 파일명만 사용
                    if (!(base.equals("meta.json") || base.endsWith(".so"))) continue;
                    total += copy(z, new File(tmp, base), MAX_BYTES - total);
                }
            } else if (n == 4 && h[0] == 0x7f && h[1] == 'E' && h[2] == 'L' && h[3] == 'F') {
                String dn = displayName(uri);
                if (!dn.endsWith(".so")) dn += ".so";
                copy(in, new File(tmp, sanitize(dn)), MAX_BYTES);
            } else {
                throw new IOException("zip 또는 .so 파일이 아닙니다");
            }
            // 라이브러리 이름 결정
            String lib = null, name = null;
            File meta = new File(tmp, "meta.json");
            if (meta.exists()) {
                String m = new String(readAll(new java.io.FileInputStream(meta), 1 << 16), "UTF-8");
                lib = jsonStr(m, "libraryName"); name = jsonStr(m, "name");
            }
            if (lib == null) {
                File[] so = tmp.listFiles();
                if (so != null) for (File f : so) if (f.getName().endsWith(".so")) { lib = f.getName(); break; }
                if (lib == null) throw new IOException(".so 가 없습니다");
                if (name == null) name = lib.replace(".so", "");
                FileOutputStream mo = new FileOutputStream(meta);
                mo.write(("{\"name\":\"" + name.replace("\"", "'") + "\",\"libraryName\":\"" + lib + "\"}").getBytes("UTF-8"));
                mo.close();
            }
            File lf = new File(tmp, lib);
            if (!lf.exists()) throw new IOException("libraryName(" + lib + ") 파일이 없습니다");
            byte[] mg = new byte[4];
            java.io.FileInputStream fi = new java.io.FileInputStream(lf);
            fi.read(mg); fi.close();
            if (mg[0] != 0x7f || mg[1] != 'E') throw new IOException("ELF 가 아닙니다");
            if (name == null) name = lib;
            String dirName = sanitize(name);
            File dst = new File(root(), dirName);
            if (dst.exists()) rmTree(dst);
            if (!tmp.renameTo(dst)) throw new IOException("폴더 이동 실패");
            return dirName;
        } finally {
            if (tmp.exists()) rmTree(tmp);
        }
    }

    static long copy(InputStream in, File out, long limit) throws IOException {
        OutputStream o = new FileOutputStream(out);
        try {
            byte[] b = new byte[1 << 16]; long t = 0; int n;
            while ((n = in.read(b)) > 0) { t += n; if (t > limit) throw new IOException("파일이 너무 큽니다"); o.write(b, 0, n); }
            return t;
        } finally { o.close(); }
    }

    /** MainActivity.launchVm 에서 applyEnv() 직후 호출: 선택에 따라 HVM_VK_DRIVER 설정. 사용자가 '추가 환경변수'로 직접 지정했으면 존중. */
    public static void afterEnv(Activity a) {
        try {
            act = a;
            if (prefs == null) prefs = a.getSharedPreferences("hvm", 0);
            String v = prefs.getString(KEY, "");
            String cur = Os.getenv("HVM_VK_DRIVER");
            if (cur != null && !cur.equals(lastSet)) { appliedDriver = cur; return; } // 사용자 직접 지정
            if (!v.isEmpty() && !v.equals("bundled") && !new File(new File(a.getFilesDir(), "drivers"), v).isDirectory()) {
                Toast.makeText(a, "선택한 드라이버를 찾을 수 없어 기본 드라이버를 사용합니다", Toast.LENGTH_LONG).show();
                v = "";
            }
            if (v.isEmpty()) {
                if (lastSet != null) Os.unsetenv("HVM_VK_DRIVER");
                if (setBcn) { Os.unsetenv("HVM_EDEN_NO_BCN_PATCH"); setBcn = false; }
                lastSet = null;
            } else {
                Os.setenv("HVM_VK_DRIVER", v, true);
                lastSet = v;
                if (Os.getenv("HVM_EDEN_NO_BCN_PATCH") == null) { Os.setenv("HVM_EDEN_NO_BCN_PATCH", "1", true); setBcn = true; }
            }
            if (appliedDriver == null) appliedDriver = v;
            if (status != null) updateStatus();
        } catch (Throwable t) {
            android.util.Log.w("HVMVK", "afterEnv failed", t);
        }
    }
}
