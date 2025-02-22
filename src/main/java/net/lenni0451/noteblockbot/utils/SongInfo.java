package net.lenni0451.noteblockbot.utils;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
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
        metadata.put("Notes", String.valueOf(song.getNotes().getNoteCount()));
        int vanillaInstruments = SongUtil.getUsedVanillaInstruments(song).size();
        int customInstruments = SongUtil.getUsedNbsCustomInstruments(song).size();
        if (vanillaInstruments == 0) {
            metadata.put("Instruments", customInstruments + " *(custom)*");
        } else if (customInstruments == 0) {
            metadata.put("Instruments", vanillaInstruments + " *(vanilla)*");
        } else {
            metadata.put("Instruments", (vanillaInstruments + customInstruments) + " *(" + vanillaInstruments + " vanilla, " + customInstruments + " custom)*");
        }
        metadata.put("Speed", song.getTempoEvents().getHumanReadableTempoRange() + " t/s");
        metadata.put("Length", song.getHumanReadableLength());
        return format(metadata);
    }

    public static String fromSong(final NbsSong song) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Title", sanitize(song.getTitleOr("")));
        metadata.put("Description", sanitize(song.getDescriptionOr("")));
        metadata.put("Author", sanitize(song.getAuthorOr("")));
        metadata.put("Original author", sanitize(song.getOriginalAuthorOr("")));
        metadata.put("Notes", String.valueOf(song.getNotes().getNoteCount()));
        int vanillaInstruments = SongUtil.getUsedVanillaInstruments(song).size();
        int customInstruments = SongUtil.getUsedNbsCustomInstruments(song).size();
        if (vanillaInstruments == 0) {
            metadata.put("Instruments", customInstruments + " *(custom)*");
        } else if (customInstruments == 0) {
            metadata.put("Instruments", vanillaInstruments + " *(vanilla)*");
        } else {
            metadata.put("Instruments", (vanillaInstruments + customInstruments) + " *(" + vanillaInstruments + " vanilla, " + customInstruments + " custom)*");
        }
        metadata.put("Speed", song.getTempoEvents().getHumanReadableTempoRange() + " t/s");
        metadata.put("Length", song.getHumanReadableLength());
        return format(metadata);
    }

    private static String format(final Map<String, String> metadata) {
        return metadata.entrySet().stream().filter(e -> !e.getValue().isBlank()).map(e -> "**" + e.getKey() + ":** " + e.getValue()).collect(Collectors.joining("\n"));
    }

    private static String sanitize(final String s) {
        return MarkdownSanitizer.sanitize(s, MarkdownSanitizer.SanitizationStrategy.ESCAPE);
    }

}
