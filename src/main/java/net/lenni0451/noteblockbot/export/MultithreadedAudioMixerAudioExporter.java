package net.lenni0451.noteblockbot.export;

import net.lenni0451.commons.Sneaky;
import net.raphimc.audiomixer.AudioMixer;
import net.raphimc.audiomixer.pcmsource.impl.MonoIntPcmSource;
import net.raphimc.audiomixer.pcmsource.impl.StereoIntPcmSource;
import net.raphimc.audiomixer.sound.impl.pcm.OptimizedMonoSound;
import net.raphimc.audiomixer.sound.impl.pcm.StereoSound;
import net.raphimc.noteblocklib.model.Song;
import net.raphimc.noteblocktool.audio.export.impl.AudioMixerAudioExporter;

import javax.sound.sampled.AudioFormat;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

public class MultithreadedAudioMixerAudioExporter extends AudioMixerAudioExporter {

    private static final int MAX_SOUNDS = 8192;

    private final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 4));
    private final AudioMixer[] audioMixers = new AudioMixer[this.threadPool.getCorePoolSize()];
    private final CyclicBarrier startBarrier = new CyclicBarrier(this.threadPool.getCorePoolSize() + 1);
    private final CyclicBarrier stopBarrier = new CyclicBarrier(this.threadPool.getCorePoolSize() + 1);
    private final int[][] threadSamples = new int[this.audioMixers.length][];
    private int currentMixer = 0;
    private int samplesPerTick;

    public MultithreadedAudioMixerAudioExporter(final Song song, final AudioFormat format, final float masterVolume, final Consumer<Float> progressConsumer) {
        super(song, format, masterVolume, false, progressConsumer);

        for (int i = 0; i < this.audioMixers.length; i++) {
            this.audioMixers[i] = new AudioMixer(this.audioMixer.getAudioFormat());
            this.audioMixers[i].getMasterMixSound().setMaxSounds(MAX_SOUNDS / this.audioMixers.length);
        }
        for (int i = 0; i < this.threadPool.getCorePoolSize(); i++) {
            final int mixerIndex = i;
            this.threadPool.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        this.startBarrier.await();
                        this.threadSamples[mixerIndex] = this.audioMixers[mixerIndex].mix(this.samplesPerTick * 2);
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

        this.audioMixers[this.currentMixer].playSound(new OptimizedMonoSound(new MonoIntPcmSource(this.sounds.get(sound)), pitch, volume, panning));
        this.currentMixer = (this.currentMixer + 1) % this.audioMixers.length;
    }

    @Override
    protected void mix(int samplesPerTick) {
        this.samplesPerTick = samplesPerTick;
        try {
            this.startBarrier.await();
            this.stopBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            Sneaky.sneak(e);
        }
        for (int[] threadSamples : this.threadSamples) {
            this.audioMixer.playSound(new StereoSound(new StereoIntPcmSource(threadSamples)));
        }
        super.mix(samplesPerTick);
        if (this.audioMixer.getMasterMixSound().getActiveSounds() != 0) {
            throw new IllegalStateException("Mixer still has active sounds after mixing");
        }
    }

    @Override
    public void render() throws InterruptedException {
        super.render();
        this.threadPool.shutdownNow();
    }

}
