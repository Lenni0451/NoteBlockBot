package net.lenni0451.noteblockbot.export;

import com.sun.jna.Pointer;
import net.raphimc.audiomixer.util.PcmFloatAudioFormat;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
import net.raphimc.noteblocktool.audio.SoundMap;
import net.raphimc.noteblocktool.audio.library.LameLibrary;
import net.raphimc.noteblocktool.audio.player.impl.SongRenderer;
import net.raphimc.noteblocktool.audio.system.impl.AudioMixerAudioSystem;

import java.io.ByteArrayOutputStream;
import java.io.File;

public class Mp3Encoder {

    private static final int MAX_SOUNDS = 16384;
    private static final PcmFloatAudioFormat FORMAT = new PcmFloatAudioFormat(48000, 2);
    private static final int QUALITY = 50;

    public static byte[] encode(final NbsSong song, final File soundsFolder) throws Exception {
        SoundMap.reload(soundsFolder); //Load all custom instruments
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            float[] samples = sample(song);

            Pointer lame = LameLibrary.INSTANCE.lame_init();
            if (lame == null) throw new IllegalStateException("Failed to initialize LAME encoder");
            int result = LameLibrary.INSTANCE.lame_set_in_samplerate(lame, (int) FORMAT.getSampleRate());
            if (result < 0) throw new IllegalStateException("Failed to set sample rate: " + result);
            result = LameLibrary.INSTANCE.lame_set_num_channels(lame, FORMAT.getChannels());
            if (result < 0) throw new IllegalStateException("Failed to set channels: " + result);
            result = LameLibrary.INSTANCE.lame_set_VBR(lame, LameLibrary.vbr_default);
            if (result < 0) throw new IllegalStateException("Failed to set VBR mode: " + result);
            result = LameLibrary.INSTANCE.lame_set_VBR_quality(lame, (1F - (QUALITY / 100F)) * 9F);
            if (result < 0) throw new IllegalStateException("Failed to set VBR quality: " + result);
            result = LameLibrary.INSTANCE.lame_init_params(lame);
            if (result < 0) throw new IllegalStateException("Failed to initialize LAME parameters: " + result);

            int frameCount = samples.length / FORMAT.getChannels();
            byte[] dataBuffer = new byte[(int) (1.25F * frameCount + 7200)];
            int dataLength = LameLibrary.INSTANCE.lame_encode_buffer_interleaved_ieee_float(lame, samples, frameCount, dataBuffer, dataBuffer.length);
            if (dataLength < 0) throw new IllegalStateException("Failed to encode buffer: " + dataLength);
            byte[] trailerBuffer = new byte[7200];
            int trailerLength = LameLibrary.INSTANCE.lame_encode_flush(lame, trailerBuffer, trailerBuffer.length);
            if (trailerLength < 0) throw new IllegalStateException("Failed to flush encoder: " + trailerLength);
            byte[] headerBuffer = new byte[LameLibrary.INSTANCE.lame_get_lametag_frame(lame, null, 0)];
            int headerLength = LameLibrary.INSTANCE.lame_get_lametag_frame(lame, headerBuffer, headerBuffer.length);
            if (headerLength < 0) throw new IllegalStateException("Failed to get LAME tag frame: " + headerLength);
            result = LameLibrary.INSTANCE.lame_close(lame);
            if (result < 0) throw new IllegalStateException("Failed to close encoder: " + result);

            baos.write(headerBuffer, 0, headerLength);
            baos.write(dataBuffer, 0, dataLength);
            baos.write(trailerBuffer, 0, trailerLength);
            return baos.toByteArray();
        }
    }

    private static float[] sample(final NbsSong song) throws Exception {
        try (SongRenderer renderer = new SongRenderer(song, stringMap -> new AudioMixerAudioSystem(stringMap, MAX_SOUNDS, true, true, FORMAT))) {
            renderer.setTimingJitter(true);
            return renderer.renderSong();
        }
    }

}
