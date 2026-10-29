package net.lenni0451.noteblockbot.export;

import lombok.extern.slf4j.Slf4j;
import net.raphimc.audiomixer.io.mp3.Mp3AudioOutputStream;
import net.raphimc.audiomixer.util.AudioFormat;
import net.raphimc.audiomixer.util.io.seekable.SeekableByteArrayOutputStream;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
import net.raphimc.noteblocktool.audio.renderer.SongRenderer;
import net.raphimc.noteblocktool.audio.renderer.impl.ProgressSongRenderer;
import net.raphimc.noteblocktool.audio.util.SoundMap;

import java.io.File;

@Slf4j
public class Mp3Encoder {

    private static final int MAX_SOUNDS = 16384;
    private static final AudioFormat FORMAT = new AudioFormat(48000, 2);
    private static final float QUALITY = 0.5F;

    public static byte[] encode(final NbsSong song, final File soundsFolder) throws Exception {
        SoundMap.reload(soundsFolder); //Load all custom instruments

        Mp3AudioOutputStream.Id3Tags id3Tags = new Mp3AudioOutputStream.Id3Tags()
                .withTitle(song.getTitle())
                .withArtist(song.getAuthor())
                .withComment(song.getDescription())
                .withCustomField("Renderer", "NoteBlockBot");
        SeekableByteArrayOutputStream baos = new SeekableByteArrayOutputStream();
        try (Mp3AudioOutputStream mp3AudioOutputStream = new Mp3AudioOutputStream(baos, FORMAT, Mp3AudioOutputStream.Encoding.vbr(QUALITY), id3Tags)) {
            mp3AudioOutputStream.write(sample(song));
        }
        return baos.toByteArray();
    }

    private static float[] sample(final NbsSong song) throws Exception {
        try (SongRenderer renderer = new ProgressSongRenderer(song, MAX_SOUNDS, true, true, FORMAT, progress -> {})) {
            renderer.setTimingJitter(true);
            return renderer.renderSong().samples();
        }
    }

}
