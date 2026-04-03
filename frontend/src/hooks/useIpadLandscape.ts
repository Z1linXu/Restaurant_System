import { useEffect, useState } from 'react'

function detectIpadLandscape() {
  if (typeof window === 'undefined') {
    return false
  }

  const width = window.innerWidth
  const height = window.innerHeight
  return width >= 1024 && width <= 1400 && width > height
}

export function useIpadLandscape() {
  const [isIpadLandscape, setIsIpadLandscape] = useState(detectIpadLandscape)

  useEffect(() => {
    const handleResize = () => {
      setIsIpadLandscape(detectIpadLandscape())
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return isIpadLandscape
}
