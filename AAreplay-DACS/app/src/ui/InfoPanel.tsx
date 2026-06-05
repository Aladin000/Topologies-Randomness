interface InfoPanelProps {
  currentRound: number
  totalRounds: number
  informedCount: number
  messageCount: number
  nodeCount: number
  failedCount?: number
}

export function InfoPanel({
  currentRound,
  totalRounds,
  informedCount,
  messageCount,
  nodeCount,
  failedCount,
}: InfoPanelProps) {
  const roundLabel = currentRound === 0
    ? `0 / ${totalRounds}`
    : `${currentRound} / ${totalRounds}`

  return (
    <div className="ui-info">
      <div className="ui-info-item">
        <span className="ui-info-label">Round</span>
        <span className="ui-info-value">{roundLabel}</span>
      </div>
      <div className="ui-info-item">
        <span className="ui-info-label">Informed</span>
        <span className="ui-info-value">{informedCount} / {nodeCount}</span>
      </div>
      {failedCount != null && failedCount > 0 && (
        <div className="ui-info-item">
          <span className="ui-info-label" style={{ color: '#c62828' }}>Failed</span>
          <span className="ui-info-value" style={{ color: '#c62828' }}>{failedCount}</span>
        </div>
      )}
      <div className="ui-info-item">
        <span className="ui-info-label">Messages</span>
        <span className="ui-info-value">{messageCount}</span>
      </div>
    </div>
  )
}
