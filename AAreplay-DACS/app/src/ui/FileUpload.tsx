import React, { useRef } from 'react'

interface FileUploadProps {
  onFileLoaded: (e: React.ChangeEvent<HTMLInputElement>) => void
  disabled?: boolean
  label?: string
}

export function FileUpload({ onFileLoaded, disabled, label }: FileUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleClick = () => {
    if (!disabled) {
      fileInputRef.current?.click()
    }
  }

  return (
    <div className="ui-upload-container">
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        onChange={e => {
          onFileLoaded(e)
          if (fileInputRef.current) fileInputRef.current.value = ''
        }}
        disabled={disabled}
        style={{ display: 'none' }}
      />
      <button
        className="ui-btn"
        disabled={disabled}
        onClick={handleClick}
        type="button"
      >
        {label || 'Load'}
      </button>
    </div>
  )
}
