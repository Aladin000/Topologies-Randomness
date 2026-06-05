import React, { useState, useRef, useCallback, useEffect } from 'react'
import type { ReplayFile } from './model'
import type { ReplayState } from './replay'
import {
  computeReplayState,
  roundDelayMs,
  pulseDurationMs,
  DEFAULT_SPEED,
} from './replay'
import { computeLayout, type LayoutResult } from './layout'
import { loadReplayFile } from './input'
import { SVGRenderer } from './rendering/SVGRenderer'

import {
  FileUpload,
  PlaybackControls,
  InfoPanel,
  ErrorDisplay,
  ConfigPanel,
  ResultPanel,
} from './ui'
import './ui/ui.css'
import './App.css'

type AppPhase = 'preload' | 'loading' | 'ready' | 'playing' | 'paused' | 'finished'

export default function App() {
  const [file, setFile] = useState<ReplayFile | null>(null)
  const [layout, setLayout] = useState<LayoutResult | null>(null)
  const [replayState, setReplayState] = useState<ReplayState | null>(null)

  const [phase, setPhase] = useState<AppPhase>('preload')
  const [currentRound, setCurrentRound] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [errorCat, setErrorCat] = useState<'parse' | 'structural' | 'semantic' | undefined>()

  const [showLabels, setShowLabels] = useState(false)
  const [hideRedundant, setHideRedundant] = useState(false)
  const [selectedNodeId, setSelectedNodeId] = useState<number | null>(null)
  const [showDetails, setShowDetails] = useState(false)

  const playIntervalRef = useRef<number | null>(null)
  const loadTokenRef = useRef(0)

  const stopPlayback = useCallback(() => {
    if (playIntervalRef.current !== null) {
      clearInterval(playIntervalRef.current)
      playIntervalRef.current = null
    }
  }, [])

  useEffect(() => {
    return () => stopPlayback()
  }, [stopPlayback])

  const totalRounds = file?.rounds.length ?? 0

  const goToRound = useCallback((roundNum: number) => {
    if (!file) return
    const safe = Number.isFinite(roundNum) ? Math.floor(roundNum) : 0
    const clamped = Math.max(0, Math.min(safe, file.rounds.length))
    const state = computeReplayState(file, clamped)
    setCurrentRound(state.currentRound)
    setReplayState(state)
  }, [file])

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0]
    if (!selectedFile) return

    // New load invalidates any pending older one.
    const token = ++loadTokenRef.current

    stopPlayback()
    setFile(null)
    setLayout(null)
    setReplayState(null)
    setError(null)
    setErrorCat(undefined)
    setPhase('loading')
    setSelectedNodeId(null)

    const result = await loadReplayFile(selectedFile)
    if (token !== loadTokenRef.current) return

    if (!result.ok) {
      setError(result.error)
      setErrorCat(result.category)
      setPhase('preload')
      return
    }

    setFile(result.file)
    const lo = computeLayout(result.file)
    setLayout(lo)

    const initialState = computeReplayState(result.file, 0)
    setReplayState(initialState)
    setCurrentRound(0)
    setPhase('ready')
  }

  const handlePlay = useCallback(() => {
    if (!file) return
    if (file.rounds.length === 0) return

    if (phase === 'finished') {
      const state = computeReplayState(file, 0)
      setCurrentRound(0)
      setReplayState(state)
    }
    setPhase('playing')

    const delay = roundDelayMs(DEFAULT_SPEED)
    playIntervalRef.current = window.setInterval(() => {
      setCurrentRound(prev => {
        const next = prev + 1
        if (next > file.rounds.length) {
          stopPlayback()
          setPhase('finished')
          return prev
        }
        const nextState = computeReplayState(file, next)
        setReplayState(nextState)
        if (next >= file.rounds.length) {
          stopPlayback()
          setPhase('finished')
        }
        return next
      })
    }, delay)
  }, [file, phase, stopPlayback])

  const handlePause = useCallback(() => {
    stopPlayback()
    setPhase('paused')
  }, [stopPlayback])

  const handleStepForward = useCallback(() => {
    if (!file) return
    stopPlayback()
    const next = Math.min(currentRound + 1, file.rounds.length)
    goToRound(next)
    if (file.rounds.length > 0 && next >= file.rounds.length) {
      setPhase('finished')
    } else {
      setPhase('paused')
    }
  }, [file, currentRound, goToRound, stopPlayback])

  const handleStepBackward = useCallback(() => {
    if (!file) return
    stopPlayback()
    const prev = Math.max(currentRound - 1, 0)
    goToRound(prev)
    setPhase(prev === 0 ? 'ready' : 'paused')
  }, [file, currentRound, goToRound, stopPlayback])

  const handleReset = useCallback(() => {
    stopPlayback()
    goToRound(0)
    setPhase('ready')
  }, [goToRound, stopPlayback])

  const handleGoHome = useCallback(() => {
    loadTokenRef.current += 1
    stopPlayback()
    setFile(null)
    setLayout(null)
    setReplayState(null)
    setSelectedNodeId(null)
    setError(null)
    setErrorCat(undefined)
    setCurrentRound(0)
    setShowDetails(false)
    setPhase('preload')
  }, [stopPlayback])

  const handleScrub = useCallback((round: number) => {
    stopPlayback()
    goToRound(round)
    if (file && file.rounds.length > 0 && round >= file.rounds.length) {
      setPhase('finished')
    } else if (round === 0) {
      setPhase('ready')
    } else {
      setPhase('paused')
    }
  }, [file, goToRound, stopPlayback])

  const handleNodeClick = useCallback((id: number) => {
    setSelectedNodeId(prev => (prev === id ? null : id))
  }, [])

  const handleScreenshot = useCallback(() => {
    const svgElem = document.querySelector('.app__svg-scene') as SVGElement | null
    if (!svgElem) return
    const serializer = new XMLSerializer()
    let source = serializer.serializeToString(svgElem)
    if (!/^<svg[^>]+xmlns="http:\/\/www\.w3\.org\/2000\/svg"/.test(source)) {
      source = source.replace(/^<svg/, '<svg xmlns="http://www.w3.org/2000/svg"')
    }
    const svgBlob = new Blob([source], { type: 'image/svg+xml;charset=utf-8' })
    const url = URL.createObjectURL(svgBlob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'aareplay_capture.svg'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, [])

  const isGraphLoaded = phase !== 'preload' && phase !== 'loading'
  const pulseMs = pulseDurationMs(DEFAULT_SPEED)

  return (
    <div className="app">

      <div className="app__header">
        <h1 className="app__title">
          <button
            type="button"
            className="app__title-btn"
            onClick={handleGoHome}
            aria-label="Return to home screen"
          >
            AAReplay
          </button>
        </h1>
        <div className="app__actions">
          {isGraphLoaded && (
            <button
              type="button"
              className="ui-btn--text"
              onClick={() => setShowDetails(s => !s)}
            >
              {showDetails ? 'Hide details' : 'Details'}
            </button>
          )}
          {isGraphLoaded && (
            <button type="button" className="ui-btn--text" onClick={handleScreenshot}>
              Export SVG
            </button>
          )}
          <FileUpload
            onFileLoaded={handleFileChange}
            label={isGraphLoaded ? 'Load new' : 'Load'}
          />
        </div>
      </div>

      {error && <ErrorDisplay error={error} category={errorCat} />}

      <div className="app__svg-container">
        <SVGRenderer
          phase={phase}
          layout={layout}
          edges={file?.network.edges ?? null}
          state={replayState}
          showLabels={showLabels}
          hideRedundantMessages={hideRedundant}
          selectedNodeId={selectedNodeId}
          onNodeClick={handleNodeClick}
          pulseDurationMs={pulseMs}
        />
      </div>

      {isGraphLoaded && file && replayState && (
        <div className="app__controls">

          <PlaybackControls
            isPlaying={phase === 'playing'}
            canPlay={file.rounds.length > 0}
            onPlay={handlePlay}
            onPause={handlePause}
            onStepForward={handleStepForward}
            onStepBackward={handleStepBackward}
            onReset={handleReset}
          />

          <input
            type="range"
            className="ui-scrubber"
            aria-label="Round scrubber"
            min={0}
            max={totalRounds}
            value={currentRound}
            onChange={e => handleScrub(Number(e.target.value))}
            disabled={phase === 'playing' || totalRounds === 0}
          />

          <div className="ui-toggles">
            <label>
              <input
                type="checkbox"
                checked={showLabels}
                onChange={e => setShowLabels(e.target.checked)}
              />
              Labels
            </label>
            <label>
              <input
                type="checkbox"
                checked={hideRedundant}
                onChange={e => setHideRedundant(e.target.checked)}
              />
              Hide redundant
            </label>
          </div>

          <InfoPanel
            currentRound={replayState.currentRound}
            totalRounds={replayState.totalRounds}
            informedCount={replayState.informedCount}
            messageCount={replayState.messageCount}
            nodeCount={file.configuration.nodeCount}
            failedCount={file.failedNodes?.length}
          />
        </div>
      )}

      {isGraphLoaded && file && showDetails && (
        <div className="app__details">
          <ConfigPanel configuration={file.configuration} />
          {file.result && <ResultPanel result={file.result} />}
        </div>
      )}

      <footer className="app__footer">
        Developed for the <strong>AAron</strong> system.
        <br />
        Part of <em>Topologies &amp; Randomness in Gossip Networks</em> (2026, DACS, Maastricht University).
      </footer>
    </div>
  )
}
