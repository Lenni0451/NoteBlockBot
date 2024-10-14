package net.lenni0451.noteblockworldwebhook;

import com.sun.jna.Pointer;
import net.raphimc.noteblocklib.format.nbs.NbsSong;
import net.raphimc.noteblocklib.format.nbs.model.NbsNote;
import net.raphimc.noteblocklib.model.SongView;
import net.raphimc.noteblocklib.util.SongResampler;
import net.raphimc.noteblocklib.util.SongUtil;
import net.raphimc.noteblocktool.audio.export.AudioExporter;
import net.raphimc.noteblocktool.audio.export.LameLibrary;
import net.raphimc.noteblocktool.audio.export.impl.JavaxAudioExporter;
import org.json.JSONObject;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SongUtils {

    private static final AudioFormat FORMAT = new AudioFormat(48000, 16, 2, true, false);

    public static String getDescription(final JSONObject song, final NbsSong nbsSong) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Description", song.optString("description"));
        metadata.put("Original author", song.optString("originalAuthor"));
        metadata.put("Notes", String.valueOf(SongUtil.getNoteCount(nbsSong.getView())));
        int vanillaInstruments = SongUtil.getUsedVanillaInstruments(nbsSong.getView()).size();
        int customInstruments = SongUtil.getUsedCustomInstruments(nbsSong.getView()).size();
        if (vanillaInstruments == 0) {
            metadata.put("Instruments", customInstruments + " (custom)");
        } else if (customInstruments == 0) {
            metadata.put("Instruments", vanillaInstruments + " (vanilla)");
        } else {
            metadata.put("Instruments", (vanillaInstruments + customInstruments) + " (" + vanillaInstruments + " vanilla, " + customInstruments + " custom)");
        }
        metadata.put("Speed", String.format("%.2f t/s", nbsSong.getView().getSpeed()));
        int length = (int) Math.ceil(nbsSong.getView().getLength() / nbsSong.getView().getSpeed());
        metadata.put("Length", String.format("%02d:%02d:%02d", length / 3600, (length / 60) % 60, length % 60));
        return metadata.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(e -> "**" + e.getKey() + ":** " + e.getValue()).collect(Collectors.joining("\n"));
    }

    public static File encode(final NbsSong song) throws Exception {
        File outFile = File.createTempFile("song", ".mp3");
        FileOutputStream fos = new FileOutputStream(outFile);
        byte[] samples = sample(song);
        int numSamples = samples.length / FORMAT.getFrameSize();
        byte[] mp3Buffer = new byte[(int) (1.25 * numSamples + 7200)];
        Pointer lame = LameLibrary.INSTANCE.lame_init();
        if (lame == null) throw new IllegalStateException("Failed to initialize LAME encoder");
        int result = LameLibrary.INSTANCE.lame_set_in_samplerate(lame, (int) FORMAT.getSampleRate());
        if (result < 0) throw new IllegalStateException("Failed to set sample rate: " + result);
        result = LameLibrary.INSTANCE.lame_set_num_channels(lame, FORMAT.getChannels());
        if (result < 0) throw new IllegalStateException("Failed to set channels: " + result);
        result = LameLibrary.INSTANCE.lame_init_params(lame);
        if (result < 0) throw new IllegalStateException("Failed to initialize LAME parameters: " + result);
        result = LameLibrary.INSTANCE.lame_encode_buffer_interleaved(lame, samples, numSamples, mp3Buffer, mp3Buffer.length);
        if (result < 0) throw new IllegalStateException("Failed to encode buffer: " + result);
        fos.write(mp3Buffer, 0, result);
        result = LameLibrary.INSTANCE.lame_encode_flush(lame, mp3Buffer, mp3Buffer.length);
        if (result < 0) throw new IllegalStateException("Failed to flush encoder: " + result);
        fos.write(mp3Buffer, 0, result);
        LameLibrary.INSTANCE.lame_close(lame);
        fos.close();
        return outFile;
    }

    private static byte[] sample(final NbsSong song) throws Exception {
        SongView<NbsNote> songView = song.getView();
        SongResampler.applyNbsTempoChangers(song, songView);

        AudioExporter exporter = new JavaxAudioExporter(songView, FORMAT, 1F, f -> {});
        exporter.render();
        return exporter.getSamples();
        //TODO: Update code for next NoteBlockLib version
//        AudioExporter exporter = new AudioMixerAudioExporter(songView, FORMAT, 1F, f -> {});
//        exporter.render();
//        return SoundIO.writeSamples(exporter.getSamples(), FORMAT);
    }

}
