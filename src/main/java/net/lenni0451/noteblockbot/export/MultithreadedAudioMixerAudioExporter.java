package net.lenni0451.noteblockbot.export;

import net.raphimc.audiomixer.AudioMixer;
import net.raphimc.audiomixer.sound.source.MonoSound;
import net.raphimc.audiomixer.sound.source.StaticStereoSound;
import net.raphimc.noteblocklib.model.SongView;
import net.raphimc.noteblocktool.audio.export.impl.AudioMixerAudioExporter;

import javax.sound.sampled.AudioFormat;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

public class MultithreadedAudioMixerAudioExporter extends AudioMixerAudioExporter {

    private final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 4));
    private final AudioMixer[] audioMixers = new AudioMixer[this.threadPool.getCorePoolSize()];
    private final CyclicBarrier startBarrier = new CyclicBarrier(this.threadPool.getCorePoolSize() + 1);
    private final CyclicBarrier stopBarrier = new CyclicBarrier(this.threadPool.getCorePoolSize() + 1);
    private final int[][] threadSamples = new int[this.audioMixers.length][];
    private int currentMixer = 0;

    public MultithreadedAudioMixerAudioExporter(final SongView<?> songView, final AudioFormat format, final float masterVolume, final Consumer<Float> progressConsumer) {
        super(songView, format, masterVolume, progressConsumer);

        final int mixSampleCount = this.samplesPerTick * format.getChannels();
        for (int i = 0; i < this.audioMixers.length; i++) {
            this.audioMixers[i] = new AudioMixer(format, 8192 / this.audioMixers.length);
        }
        for (int i = 0; i < this.threadPool.getCorePoolSize(); i++) {
            final int mixerIndex = i;
            this.threadPool.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        this.startBarrier.await();
                        this.threadSamples[mixerIndex] = this.audioMixers[mixerIndex].mix(mixSampleCount);
                        this.stopBarrier.await();
                    }
                } catch (InterruptedException | BrokenBarrierException ignored) {
                }
            });
        }
    }

    @Override
    protected void processSound(String sound, float pitch, float volume, float panning) {
        if (!this.sounds.containsKey(sound)) return;

        this.audioMixers[this.currentMixer].playSound(new MonoSound(this.sounds.get(sound), pitch, volume, panning));
        this.currentMixer = (this.currentMixer + 1) % this.audioMixers.length;
    }

    @Override
    protected void postTick() {
        try {
            this.startBarrier.await();
            this.stopBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
        for (int[] threadSamples : this.threadSamples) {
            this.audioMixer.playSound(new StaticStereoSound(threadSamples));
        }
        super.postTick();
        if (this.audioMixer.getActiveSounds() != 0) {
            throw new IllegalStateException("Mixer still has active sounds after mixing");
        }
    }

    @Override
    protected void finish() {
        this.threadPool.shutdownNow();
        super.finish();
    }

}
