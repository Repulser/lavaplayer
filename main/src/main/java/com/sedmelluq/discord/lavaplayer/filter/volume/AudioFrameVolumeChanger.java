package com.sedmelluq.discord.lavaplayer.filter.volume;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkDecoder;
import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkEncoder;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameRebuilder;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext;
import com.sedmelluq.discord.lavaplayer.track.playback.ImmutableAudioFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * A frame rebuilder to apply a specific volume level to the frames.
 */
public class AudioFrameVolumeChanger implements AudioFrameRebuilder {
  private final AudioConfiguration configuration;
  private final AudioDataFormat format;
  private final int newVolume;
  private final ShortBuffer sampleBuffer;
  private final PcmVolumeProcessor volumeProcessor;

  private AudioChunkEncoder encoder;
  private AudioChunkDecoder decoder;
  private int frameIndex;

  private AudioFrameVolumeChanger(AudioConfiguration configuration, AudioDataFormat format, int newVolume) {
    this.configuration = configuration;
    this.format = format;
    this.newVolume = newVolume;

    this.sampleBuffer = ByteBuffer
        .allocateDirect(format.totalSampleCount() * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer();
    this.volumeProcessor = new PcmVolumeProcessor(100);
  }

  @Override
  public AudioFrame rebuild(AudioFrame frame) {
    if (frame.getVolume() == newVolume) {
      return frame;
    }

    try {
      decoder.decode(frame.getData(), sampleBuffer);

      int targetVolume = newVolume;

      if (++frameIndex < 50) {
        targetVolume = (int) ((newVolume - frame.getVolume()) * (frameIndex / 50.0) + frame.getVolume());
      }

      // Volume 0 is stored in the frame with volume 100 buffer
      if (targetVolume != 0) {
        volumeProcessor.applyVolume(frame.getVolume(), targetVolume, sampleBuffer);
      }

      byte[] bytes = encoder.encode(sampleBuffer);

      // One frame per 20ms is consumed. To not spike the CPU usage, reencode only once per 5ms. By the time the buffer is
      // fully rebuilt, it is probably near to 3/4 its maximum size.
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        // Keep it interrupted, it will trip on the next interruptible operation
        Thread.currentThread().interrupt();
        // Rethrow to ensure proper cleanup in apply() method
        throw new RuntimeException("Volume change interrupted", e);
      }

      return new ImmutableAudioFrame(frame.getTimecode(), bytes, targetVolume, format, frame.getFlags());
    } catch (RuntimeException e) {
      // Propagate runtime exceptions to ensure cleanup happens
      throw e;
    } catch (Exception e) {
      // Wrap checked exceptions to ensure cleanup happens
      throw new RuntimeException("Failed to rebuild audio frame", e);
    }
  }

  private void setupLibraries() {
    encoder = format.createEncoder(configuration);
    decoder = format.createDecoder();
  }

  private void clearLibraries() {
    if (encoder != null) {
      encoder.close();
    }

    if (decoder != null) {
      decoder.close();
    }
  }

  /**
   * Applies a volume level to the buffered frames of a frame consumer
   * @param context Configuration and output information for processing
   */
  public static void apply(AudioProcessingContext context) {
    AudioFrameVolumeChanger volumeChanger = new AudioFrameVolumeChanger(context.configuration, context.outputFormat,
        context.playerOptions.volumeLevel.get());

    boolean librariesSetup = false;
    try {
      volumeChanger.setupLibraries();
      librariesSetup = true;
      context.frameBuffer.rebuild(volumeChanger);
    } catch (Exception e) {
      // Log the exception but ensure cleanup happens
      if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new RuntimeException("Failed to apply volume change", e);
    } finally {
      // Always attempt cleanup if libraries were setup
      if (librariesSetup) {
        try {
          volumeChanger.clearLibraries();
        } catch (Exception cleanupEx) {
          // Log cleanup failure but don't throw to avoid masking original exception
          // The finalizer will eventually clean up the native resources
          cleanupEx.printStackTrace();
        }
      }
    }
  }
}
