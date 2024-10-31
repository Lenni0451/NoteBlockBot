package net.lenni0451.noteblockbot.api;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.noteblockbot.Main;
import net.lenni0451.noteblockbot.data.SQLiteDB;
import net.lenni0451.noteblockbot.export.Mp3Encoder;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
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
            log.warn("Failed to load last update time");
            log.warn("Setting last update time to now");
        }
    }

    public static void run() {
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            try {
                log.debug("Checking for new songs...");
                List<JSONObject> newSongs = fetchNewSongs();
                if (!newSongs.isEmpty()) {
                    for (JSONObject apiObject : newSongs) {
                        sendEmbed(apiObject);
                    }
                    lastUpdate = Instant.now();
                    Files.writeString(new File("lastUpdate.txt").toPath(), String.valueOf(lastUpdate.toEpochMilli()));
                }
                log.debug("Done!");
            } catch (Throwable t) {
                log.error("Failed to fetch api", t);
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    private static List<JSONObject> fetchNewSongs() throws IOException, InterruptedException {
        List<JSONObject> newSongs = new ArrayList<>();
        PAGE_LOOP:
        for (int page = 1; newSongs.size() % 10 == 0; page++) {
            log.debug("Fetching page {}...", page);
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
        log.info("Found {} by {}", songName, song.getJSONObject("uploader").getString("username"));
        String downloadUrl = "https://api.noteblock.world/api/v1/song/" + song.getString("publicId") + "/download?src=downloadButton";
        log.info("Downloading nbs file...");
        byte[] nbsData = NetUtils.get(downloadUrl).getContent();
        NbsSong nbsSong = (NbsSong) NoteBlockLib.readSong(nbsData, SongFormat.NBS);
        String description = SongInfo.fromApi(song, nbsSong);
        log.info("Encoding mp3 file...");
        byte[] mp3Data = Mp3Encoder.encode(nbsSong);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(songName);
        embed.setDescription(description);
        embed.setImage(song.getString("thumbnailUrl"));
        embed.setUrl("https://noteblock.world/song/" + song.getString("publicId"));
        embed.setAuthor(song.getJSONObject("uploader").getString("username"), null, song.getJSONObject("uploader").getString("profileImage"));

        int count = 0;
        Set<Long> toRemove = new HashSet<>();
        try (PreparedStatement statement = Main.getDb().prepare("SELECT * FROM " + SQLiteDB.UPLOAD_NOTIFICATION)) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long guildId = result.getLong("GuildId");
                    long channelId = result.getLong("ChannelId");

                    Guild guild = Main.getJda().getGuildById(guildId);
                    if (guild == null) {
                        log.warn("Guild with id {} not found. Removing it from list.", guildId);
                        toRemove.add(guildId);
                        continue;
                    }
                    TextChannel textChannel = guild.getTextChannelById(channelId);
                    if (textChannel == null) {
                        log.warn("TextChannel with id {} not found in guild {}. Removing it from list.", channelId, guild.getName());
                        toRemove.add(guildId);
                        continue;
                    }
                    textChannel.sendMessageEmbeds(embed.build()).setFiles(FileUpload.fromData(nbsData, songName + ".nbs"), FileUpload.fromData(mp3Data, songName + ".mp3")).queue();
                    count++;
                }
            }
        }
        if (!toRemove.isEmpty()) {
            try (PreparedStatement statement = Main.getDb().prepare("DELETE FROM " + SQLiteDB.UPLOAD_NOTIFICATION + " WHERE GuildId = ?")) {
                for (long guildId : toRemove) {
                    statement.setLong(1, guildId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
        log.info("Sent {} notifications", count);
    }

}
