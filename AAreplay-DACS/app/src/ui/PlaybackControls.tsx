interface PlaybackControlsProps {
  isPlaying: boolean
  /** False when there are no recorded rounds. */
  canPlay?: boolean
  onPlay: () => void
  onPause: () => void
  onStepForward: () => void
  onStepBackward: () => void
  onReset: () => void
}

export function PlaybackControls({
  isPlaying,
  canPlay = true,
  onPlay,
  onPause,
  onStepForward,
  onStepBackward,
  onReset,
}: PlaybackControlsProps) {
  return (
    <div className="ui-controls">
      <button
        type="button"
        className="ui-btn"
        onClick={onReset}
        title="Reset to round 0"
      >
        <span className="ui-reset-label">0</span>
      </button>

      <button
        type="button"
        className="ui-btn"
        onClick={onStepBackward}
        disabled={isPlaying || !canPlay}
        title="Step backward"
      >
        <div className="icon-step-back"><div className="bar"/><div className="arrow"/></div>
      </button>

      {isPlaying ? (
        <button
          type="button"
          className="ui-btn"
          onClick={onPause}
          title="Pause"
        >
          <div className="icon-pause" />
        </button>
      ) : (
        <button
          type="button"
          className="ui-btn"
          onClick={onPlay}
          disabled={!canPlay}
          title="Play"
        >
          <div className="icon-play" />
        </button>
      )}

      <button
        type="button"
        className="ui-btn"
        onClick={onStepForward}
        disabled={isPlaying || !canPlay}
        title="Step forward"
      >
        <div className="icon-step-fwd"><div className="arrow"/><div className="bar"/></div>
      </button>
    </div>
  )
}
