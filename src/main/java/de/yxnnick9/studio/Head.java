package de.yxnnick9.studio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Laedt EINMALIG den Kopf-Avatar des konfigurierten Namens (Standard: yxnnick9) und speichert ihn als
 * config/yxnnick9-texturestudio/head.png. Abschaltbar ueber "fetchHead": false in der config.json.
 */
final class Head {
    private static volatile byte[] data;
    private static boolean started;
    private static Tex tex;

    private Head() {
    }

    static Tex get() {
        Config c = Config.I;
        if (!started) {
            started = true;
            if (c.fetchHead && c.headName != null && c.headName.matches("[A-Za-z0-9_]{1,16}")) {
                Thread t = new Thread(Head::load, "yxnnick9-head");
                t.setDaemon(true);
                t.start();
            }
        }
        if (tex == null && data != null) {
            try {
                tex = Textures.register("head", data);
            } catch (Exception e) {
                data = null;
            }
        }
        return tex;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static void load() {
        try {
            Path f = Config.DIR.resolve("head.png");
            if (Files.exists(f)) {
                byte[] b = Files.readAllBytes(f);
                if (isPng(b)) {
                    data = b;
                    return;
                }
            }
            HttpClient hc = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest rq = HttpRequest.newBuilder(
                            URI.create("https://mc-heads.net/avatar/" + Config.I.headName + "/64"))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<byte[]> rs = hc.send(rq, HttpResponse.BodyHandlers.ofByteArray());
            if (rs.statusCode() == 200 && rs.body().length < 200_000 && isPng(rs.body())) {
                Files.write(f, rs.body());
                data = rs.body();
            }
        } catch (Exception ignored) {
        }
    }
}
