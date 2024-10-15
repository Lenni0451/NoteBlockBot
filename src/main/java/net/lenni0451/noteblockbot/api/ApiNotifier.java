package net.lenni0451.noteblockbot.api;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.FileUpload;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.noteblockbot.Main;
import net.lenni0451.noteblockbot.utils.Mp3Encoder;
import net.lenni0451.noteblockbot.utils.NetUtils;
import net.lenni0451.noteblockbot.utils.SongInfo;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
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
import java.util.concurrent.TimeUnit;

public class ApiNotifier {

    private static final String API_URL = "https://api.noteblock.world/api/v1/song-browser/recent?page={PAGE}&limit=10&order=false";
    private static Instant lastUpdate = Instant.now();

    static {
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

    public static void run() {
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            try {
                System.out.println("Checking for new songs...");
                List<JSONObject> newSongs = fetchNewSongs();
                if (!newSongs.isEmpty()) {
                    for (JSONObject apiObject : newSongs) {
                        sendEmbed(apiObject);
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

    private static List<JSONObject> fetchNewSongs() throws IOException, InterruptedException {
        List<JSONObject> newSongs = new ArrayList<>();
        PAGE_LOOP:
        for (int page = 1; newSongs.size() % 10 == 0; page++) {
            System.out.println("Fetching page " + page + "...");
            HttpResponse response = NetUtils.get(API_URL.replace("{PAGE}", String.valueOf(page)));
            JSONArray songs = new JSONArray(response.getContentAsString());
            for (int i = 0; i < songs.length(); i++) {
                JSONObject song = songs.getJSONObject(i);
                Instant createdAt = ZonedDateTime.parse(song.getString("createdAt"), DateTimeFormatter.ISO_DATE_TIME).toInstant();
                if (createdAt.isAfter(lastUpdate)) {
                    newSongs.add(song);
                } else {
                    break PAGE_LOOP;
                }
            }

            Thread.sleep(1000); //Sleep for 1 second to not hit the rate limit
        }
        newSongs.sort(Comparator.comparing(a -> ZonedDateTime.parse(a.getString("createdAt"), DateTimeFormatter.ISO_DATE_TIME)));
        return newSongs;
    }

    private static void sendEmbed(final JSONObject song) throws Exception {
        String songName = song.getString("title");
        System.out.println("Found " + songName + " by " + song.getJSONObject("uploader").getString("username"));
        String downloadUrl = "https://api.noteblock.world/api/v1/song/" + song.getString("publicId") + "/download?src=downloadButton";
        System.out.println("Downloading nbs file...");
        byte[] nbsData = NetUtils.get(downloadUrl).getContent();
        NbsSong nbsSong = (NbsSong) NoteBlockLib.readSong(nbsData, SongFormat.NBS);
        String description = SongInfo.fromApi(song, nbsSong);
        System.out.println("Encoding mp3 file...");
        byte[] mp3Data = Mp3Encoder.encode(nbsSong);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(songName);
        embed.setDescription(description);
        embed.setImage(song.getString("thumbnailUrl"));
        embed.setUrl("https://noteblock.world/song/" + song.getString("publicId"));
        embed.setAuthor(song.getJSONObject("uploader").getString("username"), null, song.getJSONObject("uploader").getString("profileImage"));
        Main.getJda()
                .getGuildById(851798351790997504L)
                .getTextChannelById(1085979343211208784L)
                .sendMessageEmbeds(embed.build())
                .setFiles(FileUpload.fromData(mp3Data, songName + ".mp3"))
                .queue();
    }

}
