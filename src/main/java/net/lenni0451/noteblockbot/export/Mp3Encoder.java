package net.lenni0451.noteblockbot.export;

import com.sun.jna.Pointer;
import net.raphimc.audiomixer.util.PcmFloatAudioFormat;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
import net.raphimc.noteblocktool.audio.SoundMap;
import net.raphimc.noteblocktool.audio.export.AudioExporter;
import net.raphimc.noteblocktool.audio.export.LameLibrary;
import net.raphimc.noteblocktool.audio.export.impl.AudioMixerAudioExporter;

import java.io.ByteArrayOutputStream;
import java.io.File;

public class Mp3Encoder {

    private static final PcmFloatAudioFormat FORMAT = new PcmFloatAudioFormat(48000, 2);

    public static byte[] encode(final NbsSong song, final File soundsFolder) throws Exception {
        SoundMap.reload(soundsFolder); //Load all custom instruments
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            float[] samples = sample(song);
            final int numSamples = samples.length / FORMAT.getChannels();
            final byte[] mp3Buffer = new byte[(int) (1.25 * numSamples + 7200)];
            final Pointer lame = LameLibrary.INSTANCE.lame_init();
            if (lame == null) throw new IllegalStateException("Failed to initialize LAME encoder");
            int result = LameLibrary.INSTANCE.lame_set_in_samplerate(lame, (int) FORMAT.getSampleRate());
            if (result < 0) throw new IllegalStateException("Failed to set sample rate: " + result);
            result = LameLibrary.INSTANCE.lame_set_num_channels(lame, FORMAT.getChannels());
            if (result < 0) throw new IllegalStateException("Failed to set channels: " + result);
            result = LameLibrary.INSTANCE.lame_init_params(lame);
            if (result < 0) throw new IllegalStateException("Failed to initialize LAME parameters: " + result);
            result = LameLibrary.INSTANCE.lame_encode_buffer_interleaved_ieee_float(lame, samples, numSamples, mp3Buffer, mp3Buffer.length);
            if (result < 0) throw new IllegalStateException("Failed to encode buffer: " + result);
            baos.write(mp3Buffer, 0, result);
            result = LameLibrary.INSTANCE.lame_encode_flush(lame, mp3Buffer, mp3Buffer.length);
            if (result < 0) throw new IllegalStateException("Failed to flush encoder: " + result);
            baos.write(mp3Buffer, 0, result);
            LameLibrary.INSTANCE.lame_close(lame);
            return baos.toByteArray();
        }
    }

    private static float[] sample(final NbsSong song) throws Exception {
        AudioExporter exporter = new AudioMixerAudioExporter(song, FORMAT, 1F, false, true, f -> {});
        exporter.render();
        return exporter.getSamples();
    }

}
