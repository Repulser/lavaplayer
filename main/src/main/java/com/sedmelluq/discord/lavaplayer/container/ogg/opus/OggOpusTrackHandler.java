package com.sedmelluq.discord.lavaplayer.container.ogg.opus;

import com.sedmelluq.discord.lavaplayer.container.common.OpusPacketRouter;
import com.sedmelluq.discord.lavaplayer.container.ogg.OggPacketInputStream;
import com.sedmelluq.discord.lavaplayer.container.ogg.OggTrackHandler;
import com.sedmelluq.discord.lavaplayer.tools.io.DirectBufferStreamBroker;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * OGG stream handler for Opus codec.
 */
public class OggOpusTrackHandler implements OggTrackHandler {
  private final OggPacketInputStream packetInputStream;
  private final DirectBufferStreamBroker broker;
  private final int channelCount;
  private final int sampleRate;
  private final Object stateLock = new Object();
  private volatile OpusPacketRouter opusPacketRouter;
  private Long pendingSeekTimecode;

  /**
   * @param packetInputStream OGG packet input stream
   * @param broker Broker for loading stream data into direct byte buffer.
   * @param channelCount Number of channels in the track.
   * @param sampleRate Sample rate of the track.
   */
  public OggOpusTrackHandler(OggPacketInputStream packetInputStream, DirectBufferStreamBroker broker, int channelCount,
                             int sampleRate) {

    this.packetInputStream = packetInputStream;
    this.broker = broker;
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
  }

  @Override
  public void initialise(AudioProcessingContext context, long timecode, long desiredTimecode) {
    synchronized (stateLock) {
      if (opusPacketRouter != null) {
        return;
      }
      
      OpusPacketRouter newRouter = new OpusPacketRouter(context, sampleRate, channelCount);
      
      try {
        // If there was a pending seek before initialization, use that position
        if (pendingSeekTimecode != null) {
          long actualPosition = packetInputStream.seek(pendingSeekTimecode);
          newRouter.seekPerformed(pendingSeekTimecode, actualPosition);
          // Only clear pending seek after successful application
          pendingSeekTimecode = null;
        } else {
          newRouter.seekPerformed(desiredTimecode, timecode);
        }
        
        // Commit the fully initialized router
        opusPacketRouter = newRouter;
      } catch (Exception e) {
        // Clean up the router on failure to prevent leaks
        newRouter.close();
        throw new RuntimeException("Failed to initialize opus router", e);
      }
    }
  }

  @Override
  public void provideFrames() throws InterruptedException {
    try {
      while (packetInputStream.startNewPacket()) {
        broker.consumeNext(packetInputStream, Integer.MAX_VALUE, Integer.MAX_VALUE);

        ByteBuffer buffer = broker.getBuffer();

        if (buffer.remaining() > 0) {
          opusPacketRouter.process(buffer);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void seekToTimecode(long timecode) {
    OpusPacketRouter router;
    synchronized (stateLock) {
      router = this.opusPacketRouter;
      if (router == null) {
        // Not initialized yet - just store the pending seek
        // The actual seek will be performed during initialization
        pendingSeekTimecode = timecode;
        return;
      }
    }
    
    // Perform the seek outside the lock to avoid blocking initialization
    try {
      long actualPosition = packetInputStream.seek(timecode);
      router.seekPerformed(timecode, actualPosition);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    synchronized (stateLock) {
      if (opusPacketRouter != null) {
        opusPacketRouter.close();
        opusPacketRouter = null;
      }
    }
  }
}
