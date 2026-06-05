interface ErrorDisplayProps {
  error: string | null
  category?: 'parse' | 'structural' | 'semantic'
}

export function ErrorDisplay({ error, category }: ErrorDisplayProps) {
  if (!error) return null

  const label = category === 'semantic'
    ? 'Semantic error'
    : category === 'structural'
    ? 'Structural error'
    : 'Parse error'

  return (
    <div className="ui-error">
      <div className="ui-error-cat">{label}</div>
      <div className="ui-error-msg">{error}</div>
    </div>
  )
}
