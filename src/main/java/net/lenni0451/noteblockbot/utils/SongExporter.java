package net.lenni0451.noteblockbot.utils;

import net.dv8tion.jda.api.utils.FileUpload;
import net.lenni0451.noteblockbot.export.Mp3Encoder;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;

import java.io.File;
import java.util.regex.Pattern;

public class SongExporter {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*");

    public static ProcessedSong process(final NbsSong song, final String fileName, final boolean spoiler) throws Exception {
        byte[] mp3Data = Mp3Encoder.encode(song, new File("Sounds"));
        String info = SongInfo.fromSong(song);
        info = URL_PATTERN.matcher(info).replaceAll("<$0>");
        String songName = fileName;
        if (songName.toLowerCase().endsWith(".nbs")) {
            songName = songName.substring(0, songName.length() - 4);
        }
        if (!song.getTitleOr("").isBlank()) {
            songName = song.getTitle();
        }
        FileUpload upload = FileUpload.fromData(mp3Data, songName + ".mp3");
        if (spoiler) {
            upload = upload.asSpoiler();
            info = "||" + info + "||";
        }
        return new ProcessedSong(upload, info);
    }


    public record ProcessedSong(FileUpload upload, String info) {
    }

}
