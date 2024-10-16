package net.lenni0451.noteblockbot.export;

import com.sun.jna.Pointer;
import net.raphimc.audiomixer.util.io.SoundIO;
import net.raphimc.noteblocklib.format.nbs.NbsSong;
import net.raphimc.noteblocklib.format.nbs.model.NbsNote;
import net.raphimc.noteblocklib.model.SongView;
import net.raphimc.noteblocklib.util.SongResampler;
import net.raphimc.noteblocktool.audio.export.AudioExporter;
import net.raphimc.noteblocktool.audio.export.LameLibrary;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;

public class Mp3Encoder {

    private static final AudioFormat FORMAT = new AudioFormat(48000, 16, 2, true, false);

    public static byte[] encode(final NbsSong song) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
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
            baos.write(mp3Buffer, 0, result);
            result = LameLibrary.INSTANCE.lame_encode_flush(lame, mp3Buffer, mp3Buffer.length);
            if (result < 0) throw new IllegalStateException("Failed to flush encoder: " + result);
            baos.write(mp3Buffer, 0, result);
            LameLibrary.INSTANCE.lame_close(lame);
            return baos.toByteArray();
        }
    }

    private static byte[] sample(final NbsSong song) throws Exception {
        SongView<NbsNote> songView = song.getView();
        SongResampler.applyNbsTempoChangers(song, songView);

        AudioExporter exporter = new MultithreadedAudioMixerAudioExporter(songView, FORMAT, 1F, f -> {});
        exporter.render();
        return SoundIO.writeSamples(exporter.getSamples(), FORMAT);
    }

}
