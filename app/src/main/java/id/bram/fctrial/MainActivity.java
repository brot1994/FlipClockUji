package id.bram.fctrial;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Aplikasi UJI COBA untuk mengetes di tablet:
 *  1. membuka YouTube / YT Music / Spotify / Chrome di sisi sebelah (split-screen) atau sebagai pop-up
 *  2. mengontrol play/pause/next aplikasi musik lain
 *  3. memutar playlist tertentu langsung (playFromUri / playFromSearch)
 *  4. membacakan teks bahasa Indonesia + bel
 */
public class MainActivity extends Activity {

    static final String PKG_YT = "com.google.android.youtube";
    static final String PKG_YTM = "com.google.android.apps.youtube.music";
    static final String PKG_SPOTIFY = "com.spotify.music";
    static final String PKG_CHROME = "com.android.chrome";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusView, mediaView, logView;
    private EditText linkInput, searchInput, ttsInput;
    private CheckBox adjacentBox, bellBox, twiceBox, popupTopBox;
    private LinearLayout sessionList;
    private String targetPkg = null; // media session yang dipilih
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private AudioManager audio;
    private AudioFocusRequest focusReq;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#121214"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(40));
        scroll.addView(root);

        root.addView(title("FC Uji Split — uji coba"));
        root.addView(note("Aplikasi ini hanya untuk mengetes. Kirim screenshot hasilnya ke Claude."));

        // ---- 1. status
        root.addView(header("1. Status"));
        statusView = note("");
        root.addView(statusView);
        root.addView(row(
                btn("Beri izin kontrol musik", v -> openNotifAccess()),
                btn("Cek ulang", v -> refreshAll())));

        // ---- 2. open in split
        root.addView(header("2. Buka link di sisi sebelah"));
        root.addView(note("Langkah awal: buka FC Uji Split dalam layar terbagi dulu (Recent apps → ikon aplikasi → 'Buka di tampilan layar terpisah'). Lalu tempel link playlist/video di bawah."));
        linkInput = input("Tempel link YouTube / YT Music / Spotify di sini");
        root.addView(linkInput);
        adjacentBox = check("Buka di sisi sebelah (split-screen)", true);
        root.addView(adjacentBox);
        root.addView(row(btn("YouTube", v -> openLink(PKG_YT)), btn("YT Music", v -> openLink(PKG_YTM))));
        root.addView(row(btn("Spotify", v -> openLink(PKG_SPOTIFY)), btn("Chrome", v -> openLink(PKG_CHROME))));
        root.addView(note("Uji pop-up (jendela mengambang). Centang = 75% bagian ATAS layar (Opsi 1), tidak dicentang = 75% bagian KIRI (Opsi 2):"));
        popupTopBox = check("Pop-up di atas (Opsi 1)", true);
        root.addView(popupTopBox);
        root.addView(row(btn("Pop-up YouTube", v -> openPopup(PKG_YT)), btn("Pop-up YT Music", v -> openPopup(PKG_YTM))));
        root.addView(row(btn("Pop-up Spotify", v -> openPopup(PKG_SPOTIFY)), btn("Pop-up Chrome", v -> openPopup(PKG_CHROME))));

        // ---- 3. media control
        root.addView(header("3. Kontrol musik / video yang sedang jalan"));
        mediaView = note("");
        root.addView(mediaView);
        sessionList = new LinearLayout(this);
        sessionList.setOrientation(LinearLayout.VERTICAL);
        root.addView(sessionList);
        root.addView(row(btn("⏮ Sebelumnya", v -> ctl("prev")), btn("▶ Putar", v -> ctl("play")),
                btn("⏸ Jeda", v -> ctl("pause")), btn("⏭ Berikutnya", v -> ctl("next"))));
        root.addView(row(btn("Volume −", v -> vol(-1)), btn("Volume +", v -> vol(1))));
        root.addView(note("Uji putar playlist langsung (pakai link di bagian 2):"));
        root.addView(row(btn("playFromUri (link)", v -> playFromUri()), btn("playFromMediaId (link)", v -> playFromMediaId())));
        searchInput = input("Kata kunci cari, mis. nama playlist / lagu");
        root.addView(searchInput);
        root.addView(row(btn("playFromSearch", v -> playFromSearch())));
        root.addView(note("Tanpa izin (tombol media global, ke aplikasi musik terakhir):"));
        root.addView(row(btn("Play/Pause global", v -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)),
                btn("Next global", v -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT))));

        // ---- 4. TTS
        root.addView(header("4. Bacakan teks"));
        ttsInput = input("Teks yang dibacakan");
        ttsInput.setText("Rapat K3 dimulai sepuluh menit lagi di ruang meeting");
        root.addView(ttsInput);
        bellBox = check("Bunyikan bel dulu", true);
        twiceBox = check("Bacakan 2 kali", false);
        root.addView(bellBox);
        root.addView(twiceBox);
        root.addView(row(btn("🔊 Bacakan", v -> speak())));

        // ---- log
        root.addView(header("Log"));
        logView = note("");
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(12);
        root.addView(logView);

        setContentView(scroll);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("id", "ID"));
                ttsReady = true;
                log("TTS siap. Bahasa Indonesia: " + (r >= 0 ? "TERSEDIA" : "TIDAK ADA (kode " + r + ")"));
            } else {
                log("TTS gagal dimulai (kode " + status + ")");
            }
        });

        refreshAll();
        ui.postDelayed(ticker, 2000);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshSessions();
            ui.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    public void onMultiWindowModeChanged(boolean inMulti, Configuration c) {
        super.onMultiWindowModeChanged(inMulti, c);
        log("Mode layar terbagi: " + (inMulti ? "AKTIF" : "tidak aktif"));
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(ticker);
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------ status
    private boolean hasNotifAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    private void refreshAll() {
        refreshStatus();
        refreshSessions();
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
                .append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Layar terbagi: ").append(isInMultiWindowMode() ? "AKTIF" : "tidak aktif").append("\n");
        sb.append("Izin kontrol musik: ").append(hasNotifAccess() ? "SUDAH" : "BELUM").append("\n");
        sb.append("Terpasang → YouTube: ").append(installed(PKG_YT) ? "ya" : "tidak")
                .append(" · YT Music: ").append(installed(PKG_YTM) ? "ya" : "tidak")
                .append(" · Spotify: ").append(installed(PKG_SPOTIFY) ? "ya" : "tidak")
                .append(" · Chrome: ").append(installed(PKG_CHROME) ? "ya" : "tidak");
        statusView.setText(sb.toString());
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    private void openNotifAccess() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            log("Tidak bisa membuka pengaturan izin: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ open links
    private Intent viewIntent(String pkg) {
        String link = linkInput.getText().toString().trim();
        if (link.isEmpty()) {
            if (PKG_YTM.equals(pkg)) link = "https://music.youtube.com/";
            else if (PKG_SPOTIFY.equals(pkg)) link = "spotify:";
            else link = "https://www.youtube.com/";
            log("Link kosong, membuka halaman utama " + pkg);
        }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        i.setPackage(pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private void openLink(String pkg) {
        Intent i = viewIntent(pkg);
        boolean adj = adjacentBox.isChecked();
        if (adj) i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        try {
            startActivity(i);
            log("Buka " + short_(pkg) + (adj ? " di sisi sebelah" : " layar penuh") + ": " + i.getData()
                    + (adj && !isInMultiWindowMode() ? "  (catatan: layar terbagi belum aktif)" : ""));
        } catch (ActivityNotFoundException e) {
            log("GAGAL: aplikasi " + short_(pkg) + " tidak bisa membuka link ini.");
        } catch (Exception e) {
            log("GAGAL: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void openPopup(String pkg) {
        Intent i = viewIntent(pkg);
        DisplayMetrics m = getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        boolean top = popupTopBox.isChecked();
        Rect r = top ? new Rect(0, 0, w, (int) (h * 0.75f)) : new Rect(0, 0, (int) (w * 0.75f), h);
        try {
            ActivityOptions opt = ActivityOptions.makeBasic();
            opt.setLaunchBounds(r);
            startActivity(i, opt.toBundle());
            log("Buka " + short_(pkg) + " sebagai pop-up " + (top ? "ATAS " : "KIRI ") + r.width() + "x" + r.height());
        } catch (Exception e) {
            log("GAGAL pop-up: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ media sessions
    private List<MediaController> sessions() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            return msm.getActiveSessions(new ComponentName(this, MediaListener.class));
        } catch (SecurityException e) {
            return null;
        }
    }

    private MediaController target() {
        List<MediaController> list = sessions();
        if (list == null || list.isEmpty()) return null;
        if (targetPkg != null) {
            for (MediaController c : list) if (targetPkg.equals(c.getPackageName())) return c;
        }
        return list.get(0);
    }

    private String stateName(PlaybackState s) {
        if (s == null) return "?";
        switch (s.getState()) {
            case PlaybackState.STATE_PLAYING: return "DIPUTAR";
            case PlaybackState.STATE_PAUSED: return "dijeda";
            case PlaybackState.STATE_STOPPED: return "berhenti";
            case PlaybackState.STATE_BUFFERING: return "memuat";
            case PlaybackState.STATE_CONNECTING: return "menyambung";
            case PlaybackState.STATE_ERROR: return "error";
            default: return "state " + s.getState();
        }
    }

    private void refreshSessions() {
        List<MediaController> list = sessions();
        sessionList.removeAllViews();
        if (list == null) {
            mediaView.setText("Izin kontrol musik BELUM diberikan. Tekan 'Beri izin kontrol musik' di bagian 1, aktifkan 'FC Uji Split'.");
            return;
        }
        if (list.isEmpty()) {
            mediaView.setText("Belum ada aplikasi yang memutar musik/video. Putar sesuatu dulu di YouTube / YT Music / Spotify.");
            return;
        }
        MediaController t = target();
        mediaView.setText("Ketuk salah satu untuk memilih yang dikontrol (dipilih: " + (t != null ? short_(t.getPackageName()) : "-") + ")");
        for (final MediaController c : list) {
            MediaMetadata md = c.getMetadata();
            String titleTxt = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
            String artist = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
            long actions = c.getPlaybackState() != null ? c.getPlaybackState().getActions() : 0;
            String txt = short_(c.getPackageName()) + " · " + stateName(c.getPlaybackState()) + "\n"
                    + (titleTxt != null ? titleTxt : "(tanpa judul)") + (artist != null ? " — " + artist : "") + "\n"
                    + "dukung: " + actionsText(actions);
            Button b = btn(txt, v -> { targetPkg = c.getPackageName(); log("Dipilih: " + short_(targetPkg)); refreshSessions(); });
            b.setAllCaps(false);
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            boolean sel = t != null && t.getPackageName().equals(c.getPackageName());
            b.setBackgroundColor(Color.parseColor(sel ? "#3A2A12" : "#1F1F23"));
            sessionList.addView(b);
        }
    }

    private String actionsText(long a) {
        List<String> s = new ArrayList<>();
        if ((a & PlaybackState.ACTION_PLAY) != 0) s.add("play");
        if ((a & PlaybackState.ACTION_PAUSE) != 0) s.add("pause");
        if ((a & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) s.add("next");
        if ((a & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) s.add("prev");
        if ((a & PlaybackState.ACTION_PLAY_FROM_URI) != 0) s.add("fromUri");
        if ((a & PlaybackState.ACTION_PLAY_FROM_MEDIA_ID) != 0) s.add("fromMediaId");
        if ((a & PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0) s.add("fromSearch");
        return s.isEmpty() ? "-" : android.text.TextUtils.join(", ", s);
    }

    private void ctl(String what) {
        MediaController c = target();
        if (c == null) { log("Tidak ada media yang bisa dikontrol (cek izin / putar sesuatu dulu)"); return; }
        MediaController.TransportControls t = c.getTransportControls();
        switch (what) {
            case "play": t.play(); break;
            case "pause": t.pause(); break;
            case "next": t.skipToNext(); break;
            case "prev": t.skipToPrevious(); break;
        }
        log("Kirim '" + what + "' ke " + short_(c.getPackageName()));
        ui.postDelayed(this::refreshSessions, 800);
    }

    private void playFromUri() {
        MediaController c = target();
        String link = linkInput.getText().toString().trim();
        if (c == null || link.isEmpty()) { log("Perlu media aktif + link di bagian 2"); return; }
        try {
            c.getTransportControls().playFromUri(Uri.parse(link), null);
            log("playFromUri → " + short_(c.getPackageName()) + ": " + link);
        } catch (Exception e) { log("playFromUri GAGAL: " + e.getMessage()); }
        ui.postDelayed(this::refreshSessions, 1500);
    }

    private void playFromMediaId() {
        MediaController c = target();
        String link = linkInput.getText().toString().trim();
        if (c == null || link.isEmpty()) { log("Perlu media aktif + link di bagian 2"); return; }
        String id = link;
        // ubah link Spotify https://open.spotify.com/playlist/XXX → spotify:playlist:XXX
        if (link.contains("open.spotify.com/")) {
            String p = Uri.parse(link).getPath();
            if (p != null) {
                String[] seg = p.replaceAll("^/+", "").split("/");
                if (seg.length >= 2) id = "spotify:" + seg[seg.length - 2] + ":" + seg[seg.length - 1];
            }
        }
        try {
            c.getTransportControls().playFromMediaId(id, null);
            log("playFromMediaId → " + short_(c.getPackageName()) + ": " + id);
        } catch (Exception e) { log("playFromMediaId GAGAL: " + e.getMessage()); }
        ui.postDelayed(this::refreshSessions, 1500);
    }

    private void playFromSearch() {
        MediaController c = target();
        String q = searchInput.getText().toString().trim();
        if (c == null || q.isEmpty()) { log("Perlu media aktif + kata kunci"); return; }
        try {
            c.getTransportControls().playFromSearch(q, new Bundle());
            log("playFromSearch → " + short_(c.getPackageName()) + ": " + q);
        } catch (Exception e) { log("playFromSearch GAGAL: " + e.getMessage()); }
        ui.postDelayed(this::refreshSessions, 1500);
    }

    private void mediaKey(int code) {
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        log("Tombol media global dikirim (" + KeyEvent.keyCodeToString(code) + ")");
    }

    private void vol(int dir) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        log("Volume " + audio.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    // ------------------------------------------------------------ TTS + bell
    private void speak() {
        if (!ttsReady) { log("TTS belum siap"); return; }
        final String text = ttsInput.getText().toString().trim();
        if (text.isEmpty()) return;
        final boolean bell = bellBox.isChecked();
        final int times = twiceBox.isChecked() ? 2 : 1;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        if (Build.VERSION.SDK_INT >= 26) {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs).build();
            int r = audio.requestAudioFocus(focusReq);
            log("Minta musik dikecilkan: " + (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "OK" : "ditolak (" + r + ")"));
        }
        tts.setAudioAttributes(attrs);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { if (id.equals("last")) releaseFocus(); }
            @Override public void onError(String id) { releaseFocus(); }
        });

        new Thread(() -> {
            if (bell) playBell();
            ui.post(() -> {
                for (int n = 1; n <= times; n++) {
                    Bundle p = new Bundle();
                    tts.speak(text, n == 1 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, p, n == times ? "last" : "u" + n);
                    if (n < times) tts.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, "gap" + n);
                }
                log("Membacakan " + times + "x" + (bell ? " (dengan bel)" : ""));
            });
        }).start();
    }

    private void releaseFocus() {
        if (Build.VERSION.SDK_INT >= 26 && focusReq != null) audio.abandonAudioFocusRequest(focusReq);
    }

    /** "Ding-dong" sederhana dari dua nada sinus. Berjalan di thread latar. */
    private void playBell() {
        int sr = 44100;
        double[][] notes = {{880, 0.55}, {659.25, 0.9}};
        int total = 0;
        for (double[] n : notes) total += (int) (sr * n[1]);
        short[] buf = new short[total];
        int pos = 0;
        for (double[] n : notes) {
            int len = (int) (sr * n[1]);
            for (int i = 0; i < len; i++) {
                double env = Math.exp(-3.2 * i / (double) len);
                double v = Math.sin(2 * Math.PI * n[0] * i / sr) * 0.7 + Math.sin(2 * Math.PI * n[0] * 2 * i / sr) * 0.15;
                buf[pos++] = (short) (v * env * 0.55 * Short.MAX_VALUE);
            }
        }
        AudioTrack at = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buf.length * 2).build();
        at.write(buf, 0, buf.length);
        at.play();
        try { Thread.sleep((long) (1000.0 * total / sr) + 150); } catch (InterruptedException ignored) { }
        at.release();
    }

    // ------------------------------------------------------------ ui helpers
    private String short_(String pkg) {
        if (pkg == null) return "?";
        switch (pkg) {
            case PKG_YT: return "YouTube";
            case PKG_YTM: return "YT Music";
            case PKG_SPOTIFY: return "Spotify";
            case PKG_CHROME: return "Chrome";
            default: return pkg;
        }
    }

    private void log(String s) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + s;
        ui.post(() -> {
            CharSequence old = logView.getText();
            String all = line + "\n" + old;
            if (all.length() > 6000) all = all.substring(0, 6000);
            logView.setText(all);
        });
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#ECEAE4"));
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#F2A33A"));
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(22), 0, dp(6));
        return t;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor("#C8C8CC"));
        t.setTextSize(14);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.parseColor("#6A6A70"));
        e.setTextColor(Color.parseColor("#ECEAE4"));
        e.setSingleLine(true);
        e.setTextSize(15);
        return e;
    }

    private CheckBox check(String s, boolean on) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(Color.parseColor("#ECEAE4"));
        c.setChecked(on);
        c.setMinHeight(dp(44));
        return c;
    }

    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#ECEAE4"));
        b.setBackgroundColor(Color.parseColor("#2A2A2E"));
        b.setMinHeight(dp(52));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            r.addView(v, lp);
        }
        return r;
    }
}
