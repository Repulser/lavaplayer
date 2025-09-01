package com.sedmelluq.discord.lavaplayer.track;

import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

/**
 * Audio track which delegates its processing to another track. The delegate does not have to be known when the
 * track is created, but is passed when processDelegate() is called.
 */
public abstract class DelegatedAudioTrack extends BaseAudioTrack {
  private volatile InternalAudioTrack delegate;

  /**
   * @param trackInfo Track info
   */
  public DelegatedAudioTrack(AudioTrackInfo trackInfo) {
    super(trackInfo);
  }

  protected synchronized void processDelegate(InternalAudioTrack delegate, LocalAudioTrackExecutor localExecutor)
      throws Exception {

    this.delegate = delegate;

    delegate.assignExecutor(localExecutor, false);
    delegate.process(localExecutor);
  }

  @Override
  public void setPosition(long position) {
    InternalAudioTrack currentDelegate = delegate;
    if (currentDelegate != null) {
      currentDelegate.setPosition(position);
    } else {
      super.setPosition(position);
    }
  }

  @Override
  public long getDuration() {
    InternalAudioTrack currentDelegate = delegate;
    if (currentDelegate != null) {
      return currentDelegate.getDuration();
    } else {
      return super.getDuration();
    }
  }

  @Override
  public long getPosition() {
    InternalAudioTrack currentDelegate = delegate;
    if (currentDelegate != null) {
      return currentDelegate.getPosition();
    } else {
      return super.getPosition();
    }
  }
}
