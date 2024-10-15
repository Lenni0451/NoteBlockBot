package net.lenni0451.noteblockbot.utils;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.raphimc.noteblocklib.format.nbs.NbsSong;
import net.raphimc.noteblocklib.util.SongUtil;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SongInfo {

    public static String fromApi(final JSONObject apiObject, final NbsSong song) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Description", apiObject.optString("description"));
        metadata.put("Original author", apiObject.optString("originalAuthor"));
        metadata.put("Notes", String.valueOf(SongUtil.getNoteCount(song.getView())));
        int vanillaInstruments = SongUtil.getUsedVanillaInstruments(song.getView()).size();
        int customInstruments = SongUtil.getUsedCustomInstruments(song.getView()).size();
        if (vanillaInstruments == 0) {
            metadata.put("Instruments", customInstruments + " *(custom)*");
        } else if (customInstruments == 0) {
            metadata.put("Instruments", vanillaInstruments + " *(vanilla)*");
        } else {
            metadata.put("Instruments", (vanillaInstruments + customInstruments) + " *(" + vanillaInstruments + " vanilla, " + customInstruments + " custom)*");
        }
        metadata.put("Speed", String.format("%.2f t/s", song.getView().getSpeed()));
        int length = (int) Math.ceil(song.getView().getLength() / song.getView().getSpeed());
        metadata.put("Length", String.format("%02d:%02d:%02d", length / 3600, (length / 60) % 60, length % 60));
        return format(metadata);
    }

    public static String fromSong(final NbsSong song) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Title", sanitize(song.getHeader().getTitle()));
        metadata.put("Description", sanitize(song.getHeader().getDescription()));
        metadata.put("Author", sanitize(song.getHeader().getAuthor()));
        metadata.put("Original author", sanitize(song.getHeader().getOriginalAuthor()));
        metadata.put("Notes", String.valueOf(SongUtil.getNoteCount(song.getView())));
        int vanillaInstruments = SongUtil.getUsedVanillaInstruments(song.getView()).size();
        int customInstruments = SongUtil.getUsedCustomInstruments(song.getView()).size();
        if (vanillaInstruments == 0) {
            metadata.put("Instruments", customInstruments + " *(custom)*");
        } else if (customInstruments == 0) {
            metadata.put("Instruments", vanillaInstruments + " *(vanilla)*");
        } else {
            metadata.put("Instruments", (vanillaInstruments + customInstruments) + " *(" + vanillaInstruments + " vanilla, " + customInstruments + " custom)*");
        }
        metadata.put("Speed", String.format("%.2f t/s", song.getView().getSpeed()));
        int length = (int) Math.ceil(song.getView().getLength() / song.getView().getSpeed());
        metadata.put("Length", String.format("%02d:%02d:%02d", length / 3600, (length / 60) % 60, length % 60));
        return format(metadata);
    }

    private static String format(final Map<String, String> metadata) {
        return metadata.entrySet().stream().filter(e -> !e.getValue().isBlank()).map(e -> "**" + e.getKey() + ":** " + e.getValue()).collect(Collectors.joining("\n"));
    }

    private static String sanitize(final String s) {
        return MarkdownSanitizer.sanitize(s, MarkdownSanitizer.SanitizationStrategy.ESCAPE);
    }

}
