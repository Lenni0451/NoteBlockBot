package net.lenni0451.noteblockbot;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class DataStore {

    private static final File dataFile = new File("data.json");
    private static final File tempFile = new File("data.json.tmp");
    private static final Map<Long, Long> guildToChannel = new HashMap<>();

    static {
        try {
            if (!dataFile.exists() && tempFile.exists()) {
                log.warn("Found temporary data file, renaming it to data.json");
                tempFile.renameTo(dataFile);
            }
            if (!dataFile.exists()) {
                log.info("No data file found, creating a new one");
                dataFile.createNewFile();
            } else {
                log.info("Loading DataStore...");
                JSONObject data = new JSONObject(new String(Files.readAllBytes(dataFile.toPath())));
                for (Map.Entry<String, Object> entry : data.toMap().entrySet()) {
                    guildToChannel.put(Long.parseLong(entry.getKey()), Long.parseLong(entry.getValue().toString()));
                }
                log.info("Loaded {} entries", guildToChannel.size());
            }
        } catch (Throwable t) {
            log.error("Failed to load DataStore", t);
            System.exit(-1);
        }
    }

    public static Set<Long> getGuilds() {
        return guildToChannel.keySet();
    }

    public static void setChannel(final long guildId, final long channelId) {
        guildToChannel.put(guildId, channelId);
        save();
    }

    public static long getChannel(final long guildId) {
        return guildToChannel.getOrDefault(guildId, 0L);
    }

    public static void removeChannel(final long guildId) {
        guildToChannel.remove(guildId);
        save();
    }

    private static void save() {
        try {
            JSONObject data = new JSONObject();
            guildToChannel.forEach((k, v) -> data.put(String.valueOf(k), String.valueOf(v)));
            Files.writeString(tempFile.toPath(), data.toString(4));
            Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            log.error("Failed to save DataStore", t);
        }
    }

}
