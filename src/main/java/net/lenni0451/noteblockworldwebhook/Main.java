package net.lenni0451.noteblockworldwebhook;

import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.ContentTypes;
import net.lenni0451.commons.httpclient.constants.Headers;
import net.lenni0451.commons.httpclient.content.HttpContent;
import net.lenni0451.commons.httpclient.content.impl.MultiPartFormContent;
import net.lenni0451.commons.httpclient.content.impl.StringContent;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.nbs.NbsSong;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String API_URL = "https://api.noteblock.world/api/v1/song-browser/recent?page={PAGE}&limit=10&order=false";
    private static final HttpClient CLIENT = new HttpClient();
    private static String WEBHOOK_URL;
    private static Instant lastUpdate = Instant.now();

    static {
        try {
            File file = new File("webhook.txt");
            if (!file.exists()) {
                file.createNewFile();
                System.out.println("Webhook URL file created, please enter the webhook URL and restart the application");
                System.exit(-1);
            }
            WEBHOOK_URL = Files.readString(file.toPath()).trim();
            if (WEBHOOK_URL.isEmpty()) {
                System.out.println("Webhook URL is empty");
                System.exit(-1);
            }
        } catch (Throwable t) {
            System.out.println("Failed to read webhook.txt file");
            System.exit(-1);
        }
        try {
            File file = new File("lastUpdate.txt");
            if (file.exists()) {
                lastUpdate = Instant.ofEpochMilli(Long.parseLong(Files.readString(file.toPath()).trim()));
            } else {
                file.createNewFile();
            }
        } catch (Throwable t) {
            System.out.println("Failed to load last update time");
            System.out.println("Setting last update time to now");
        }
    }

    public static void main(String[] args) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Checking for new songs...");
                List<JSONObject> newSongs = fetchNewSongs();
                if (!newSongs.isEmpty()) {
                    for (JSONObject song : newSongs) {
                        //It is expected that download a song, converting it to mp3 and uploading it is enough delay to not hit the rate limit
                        sendWebhook(song);
                    }
                    lastUpdate = Instant.now();
                    Files.writeString(new File("lastUpdate.txt").toPath(), String.valueOf(lastUpdate.toEpochMilli()));
                }
                System.out.println("Done!");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    private static List<JSONObject> fetchNewSongs() throws IOException {
        List<JSONObject> newSongs = new ArrayList<>();
        int page = 1;
        PAGE_LOOP:
        while (true) {
            HttpResponse response = CLIENT.get(API_URL.replace("{PAGE}", String.valueOf(page))).execute();
            JSONArray songs = new JSONArray(response.getContentAsString());
            for (int i = 0; i < songs.length(); i++) {
                JSONObject song = songs.getJSONObject(i);
                Instant updatedAt = ZonedDateTime.parse(song.getString("updatedAt"), DateTimeFormatter.ISO_DATE_TIME).toInstant();
                if (updatedAt.isAfter(lastUpdate)) {
                    newSongs.add(song);
                } else {
                    break PAGE_LOOP;
                }
            }
            page++;
        }
        newSongs.sort(Comparator.comparing(a -> ZonedDateTime.parse(a.getString("updatedAt"), DateTimeFormatter.ISO_DATE_TIME)));
        return newSongs;
    }

    private static void sendWebhook(final JSONObject song) throws Exception {
        String songName = song.getString("title");
        System.out.println("Found " + songName + " by " + song.getJSONObject("uploader").getString("username"));
        String downloadUrl = "https://api.noteblock.world/api/v1/song/" + song.getString("publicId") + "/download?src=downloadButton";
        File nbsFile = File.createTempFile("song", ".nbs");
        System.out.println("Downloading nbs file...");
        Files.write(nbsFile.toPath(), CLIENT.get(downloadUrl).execute().getContent());
        NbsSong nbsSong = (NbsSong) NoteBlockLib.readSong(nbsFile);
        String description = SongUtils.getDescription(song, nbsSong);
        System.out.println("Encoding mp3 file...");
        File mp3File = SongUtils.encode(nbsSong);

        MultiPartFormContent content = new MultiPartFormContent();
        content.addPart("payload_json", new StringContent(ContentTypes.APPLICATION_JSON, new JSONObject()
                .put("username", song.getJSONObject("uploader").getString("username"))
                .put("avatar_url", song.getJSONObject("uploader").getString("profileImage"))
                .put("embeds", new JSONArray()
                        .put(new JSONObject()
                                .put("title", songName)
                                .put("description", description)
                                .put("image", new JSONObject().put("url", song.getString("thumbnailUrl")))
                                .put("url", "https://noteblock.world/song/" + song.getString("publicId")))
                )
                .put("attachments", new JSONArray()
                        .put(new JSONObject()
                                .put("id", 0)
                                .put("filename", songName + ".nbs")
                                .put("url", downloadUrl))
                        .put(new JSONObject()
                                .put("id", 1)
                                .put("filename", songName + ".mp3"))
                )
                .toString()));
        content.addPart(new MultiPartFormContent.FormPart("files[0]", HttpContent.file(nbsFile), songName + ".nbs")
                .setHeader(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_OCTET_STREAM.getMimeType())
        );
        content.addPart(new MultiPartFormContent.FormPart("files[1]", HttpContent.file(mp3File), songName + ".mp3")
                .setHeader(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_OCTET_STREAM.getMimeType())
        );
        System.out.println("Sending webhook...");
        HttpResponse response = CLIENT.post(WEBHOOK_URL).setContent(content).execute();
        nbsFile.delete();
        mp3File.delete();
        if (response.getStatusCode() / 100 != 2) {
            throw new IOException("Failed to send webhook (" + response.getStatusCode() + "): " + response.getContentAsString());
        } else {
            System.out.println("Webhook sent successfully!");
        }
    }

}
