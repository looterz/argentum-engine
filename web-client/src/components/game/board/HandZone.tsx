import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useZoneCards, useZone } from '../../../store/selectors'
import { useGameStore } from '../../../store/gameStore'
import type { ZoneId, ClientCard, EntityId } from '../../../types'
import { calculateFittingCardWidth } from '../../../hooks/useResponsive'
import { useResponsiveContext } from './shared'
import { styles } from './styles'
import { GameCard } from '../card'
import { CARD_BACK_IMAGE_URL } from '../../../utils/cardImages'

/**
 * Row of cards (hand or other horizontal zone).
 * Cards in hand are NOT grouped - each card is shown individually.
 */
export function CardRow({
  zoneId,
  faceDown = false,
  interactive = false,
  small = false,
  inverted = false,
  ghostCards = [],
}: {
  zoneId: ZoneId
  faceDown?: boolean
  interactive?: boolean
  small?: boolean
  inverted?: boolean
  ghostCards?: readonly ClientCard[]
}) {
  const cards = useZoneCards(zoneId)
  const zone = useZone(zoneId)
  const responsive = useResponsiveContext()

  // For hidden zones (like opponent's hand), use zone size to show face-down placeholders
  // If some cards are revealed, show them face-up plus placeholders for unrevealed cards
  const zoneSize = zone?.size ?? 0
  const unrevealedCount = faceDown ? Math.max(0, zoneSize - cards.length) : 0
  const showPlaceholders = faceDown && cards.length === 0 && zoneSize > 0

  // Show empty message only if no cards at all (no revealed, no placeholders, no ghost cards)
  if (cards.length === 0 && !showPlaceholders && unrevealedCount === 0 && ghostCards.length === 0) {
    return <div style={{ ...styles.emptyZone, fontSize: responsive.fontSize.small }}>No cards</div>
  }

  // Calculate available width for the hand (viewport - padding - zone piles on sides)
  const sideZoneWidth = responsive.pileWidth + 20 // pile + margin
  const availableWidth = responsive.viewportWidth - (responsive.containerPadding * 2) - (sideZoneWidth * 2)

  // Calculate card width that fits all cards (revealed + unrevealed + ghost)
  const totalCardCount = (faceDown ? zoneSize : cards.length) + ghostCards.length
  const cardCount = showPlaceholders ? zoneSize : totalCardCount
  const baseWidth = small ? responsive.smallCardWidth : responsive.cardWidth
  const minWidth = small ? 30 : 45
  const fittingWidth = calculateFittingCardWidth(
    cardCount,
    availableWidth,
    responsive.cardGap,
    baseWidth,
    minWidth
  )

  // For hands (player or opponent), create a fan effect
  // - Player's own hand: interactive, face-up
  // - Opponent's hand: face-down, inverted (top of screen)
  // - Spectator bottom hand: face-down, not inverted (bottom of screen)
  const isPlayerHand = interactive && !faceDown
  const isOpponentHand = faceDown && inverted
  const isSpectatorBottomHand = faceDown && !inverted && !interactive
  const cardHeight = Math.round(fittingWidth * 1.4)

  // For opponent's hand: show revealed cards face-up, plus placeholders for unrevealed cards
  const hasRevealedCards = faceDown && cards.length > 0
  const shouldShowFan = isPlayerHand || isOpponentHand || isSpectatorBottomHand

  if (shouldShowFan && (cards.length > 0 || showPlaceholders || unrevealedCount > 0 || ghostCards.length > 0)) {
    return (
      <HandFan
        cards={cards}
        placeholderCount={showPlaceholders ? zoneSize : unrevealedCount}
        fittingWidth={fittingWidth}
        cardHeight={cardHeight}
        cardGap={responsive.cardGap}
        faceDown={faceDown && !hasRevealedCards}
        revealedCards={hasRevealedCards}
        interactive={interactive}
        small={small}
        inverted={inverted}
        ghostCards={ghostCards}
      />
    )
  }

  // Render face-down placeholders for hidden zones (non-fan layout)
  if (showPlaceholders) {
    const cardRatio = 1.4
    const height = Math.round(fittingWidth * cardRatio)
    return (
      <div style={{ ...styles.cardRow, gap: responsive.cardGap, padding: responsive.cardGap }}>
        {Array.from({ length: zoneSize }).map((_, index) => (
          <div
            key={`placeholder-${index}`}
            style={{
              ...styles.card,
              width: fittingWidth,
              height,
              borderRadius: responsive.isMobile ? 4 : 8,
              border: '2px solid #333',
              boxShadow: '0 2px 8px rgba(0,0,0,0.5)',
            }}
          >
            <img
              src={CARD_BACK_IMAGE_URL}
              alt="Card back"
              style={styles.cardImage}
            />
          </div>
        ))}
      </div>
    )
  }

  // Render each card individually (no grouping for hand)
  return (
    <div style={{ ...styles.cardRow, gap: responsive.cardGap, padding: responsive.cardGap }}>
      {cards.map((card) => (
        <GameCard
          key={card.id}
          card={card}
          count={1}
          faceDown={faceDown}
          interactive={interactive}
          small={small}
          overrideWidth={fittingWidth}
          inHand={interactive && !faceDown}
        />
      ))}
    </div>
  )
}

/**
 * Apply custom hand card ordering. Cards in handCardOrder are placed first (in that order),
 * followed by any new cards not yet in the order (appended at end).
 */
function applyHandOrder(cards: readonly ClientCard[], handCardOrder: readonly EntityId[]): readonly ClientCard[] {
  if (handCardOrder.length === 0) return cards

  const cardMap = new Map(cards.map(c => [c.id, c]))
  const ordered: ClientCard[] = []

  // Add cards in custom order (skip any that left the hand)
  for (const id of handCardOrder) {
    const card = cardMap.get(id)
    if (card) {
      ordered.push(card)
      cardMap.delete(id)
    }
  }

  // Append any new cards not in the custom order
  for (const card of cardMap.values()) {
    ordered.push(card)
  }

  return ordered
}

/**
 * Hand display with fan/arc effect - cards slightly overlap and rotate like held cards.
 * Supports drag-to-reorder for the player's own hand.
 */
export function HandFan({
  cards,
  placeholderCount = 0,
  fittingWidth,
  cardHeight,
  faceDown,
  revealedCards = false,
  interactive,
  small,
  inverted = false,
  ghostCards = [],
}: {
  cards: readonly ClientCard[]
  placeholderCount?: number
  fittingWidth: number
  cardHeight: number
  cardGap: number
  faceDown: boolean
  revealedCards?: boolean
  interactive: boolean
  small: boolean
  inverted?: boolean
  ghostCards?: readonly ClientCard[]
}) {
  const [, setHoveredIndex] = useState<number | null>(null)
  const handCardOrder = useGameStore((s) => s.handCardOrder)
  const setHandCardOrder = useGameStore((s) => s.setHandCardOrder)
  const stopDraggingCard = useGameStore((s) => s.stopDraggingCard)

  // Whether this is the player's own interactive hand (supports reorder)
  const isPlayerHand = interactive && !faceDown && !inverted

  // Apply custom ordering to cards (only for player's own hand)
  const orderedCards = useMemo(
    () => isPlayerHand ? applyHandOrder(cards, handCardOrder) : cards,
    [cards, handCardOrder, isPlayerHand],
  )

  // Reorder drag state (local to this component)
  const reorderRef = useRef<{
    cardId: EntityId
    startX: number
    startIndex: number
    currentOrder: EntityId[]
    activated: boolean
  } | null>(null)
  const [reorderDragCardId, setReorderDragCardId] = useState<EntityId | null>(null)
  const [reorderCurrentX, setReorderCurrentX] = useState(0)
  // Tracks whether pointer is down for potential reorder (to attach window listeners)
  const [pointerTracking, setPointerTracking] = useState(false)

  // When we have revealed cards in opponent's hand, show both revealed cards AND placeholders
  const baseCardCount = revealedCards
    ? orderedCards.length + placeholderCount
    : (placeholderCount > 0 ? placeholderCount : orderedCards.length)
  const cardCount = baseCardCount + ghostCards.length

  // Scale fan parameters based on card count
  // Fewer cards = more spread, more cards = tighter fan
  const maxRotation = Math.min(12, 40 / Math.max(cardCount, 1)) // Max rotation at edges (degrees)
  const maxVerticalOffset = Math.min(15, 45 / Math.max(cardCount, 1)) // Max rise at center (pixels)

  // Calculate overlap - more overlap with more cards, but keep it readable
  const overlapFactor = Math.max(0.5, 0.85 - (cardCount * 0.025))
  const cardSpacing = fittingWidth * overlapFactor

  // Total width of the hand fan
  const totalWidth = cardSpacing * (cardCount - 1) + fittingWidth

  // Allow cards to extend slightly beyond the visible area to save vertical space
  const edgeMargin = -15

  // For inverted fan, flip the arc and rotation direction
  const rotationMultiplier = inverted ? -1 : 1

  // Create array of items to render
  // - If revealedCards: show revealed cards face-up + placeholders for unrevealed
  // - If placeholderCount > 0 and no revealed cards: all placeholders
  // - Otherwise: show cards normally
  const baseItems = revealedCards
    ? [
        ...orderedCards.map((card, index) => ({ type: 'card' as const, card, index, showFaceUp: true, isGhost: false })),
        ...Array.from({ length: placeholderCount }, (_, i) => ({ type: 'placeholder' as const, index: orderedCards.length + i })),
      ]
    : placeholderCount > 0
      ? Array.from({ length: placeholderCount }, (_, i) => ({ type: 'placeholder' as const, index: i }))
      : orderedCards.map((card, index) => ({ type: 'card' as const, card, index, showFaceUp: false, isGhost: false }))

  // Append ghost cards (graveyard cards with legal activated abilities)
  const ghostItems = ghostCards.map((card, i) => ({
    type: 'card' as const,
    card,
    index: baseItems.length + i,
    showFaceUp: true,
    isGhost: true,
  }))
  const items = [...baseItems, ...ghostItems]

  // Handle reorder pointer down on a card wrapper
  const handleReorderPointerDown = useCallback((e: React.PointerEvent, cardId: EntityId, index: number) => {
    if (!isPlayerHand) return
    // Only primary button (left click / touch)
    if (e.button !== 0) return

    const currentOrder = orderedCards.map(c => c.id)
    reorderRef.current = {
      cardId,
      startX: e.clientX,
      startIndex: index,
      currentOrder,
      activated: false,
    }
    setPointerTracking(true)
  }, [isPlayerHand, orderedCards])

  // Global pointer move/up for reorder
  useEffect(() => {
    if (!pointerTracking) return

    const HORIZONTAL_THRESHOLD = 15

    const handlePointerMove = (e: PointerEvent) => {
      const ref = reorderRef.current
      if (!ref) return

      const deltaX = e.clientX - ref.startX
      const absDeltaX = Math.abs(deltaX)

      // Enter reorder mode once horizontal threshold is met
      if (!ref.activated && absDeltaX >= HORIZONTAL_THRESHOLD) {
        ref.activated = true
        setReorderDragCardId(ref.cardId)
        // Cancel any in-progress play-drag to hide the DraggedCardOverlay
        stopDraggingCard()
      }

      if (ref.activated) {
        setReorderCurrentX(deltaX)

        // Calculate which position the dragged card should be in based on horizontal offset
        const newIndex = Math.round(ref.startIndex + deltaX / cardSpacing)
        const clampedIndex = Math.max(0, Math.min(ref.currentOrder.length - 1, newIndex))

        if (clampedIndex !== ref.startIndex) {
          // Reorder: move the card from startIndex to clampedIndex
          const newOrder = [...ref.currentOrder]
          const [moved] = newOrder.splice(ref.startIndex, 1)
          if (moved !== undefined) {
            newOrder.splice(clampedIndex, 0, moved)
            ref.currentOrder = newOrder
            ref.startIndex = clampedIndex
            ref.startX = e.clientX - (deltaX % cardSpacing) // Reset start to prevent jitter
            setReorderCurrentX(0)
          }
        }
      }
    }

    const handlePointerUp = () => {
      const ref = reorderRef.current
      if (ref?.activated) {
        // Commit the reorder
        setHandCardOrder(ref.currentOrder)
      }
      reorderRef.current = null
      setReorderDragCardId(null)
      setReorderCurrentX(0)
      setPointerTracking(false)
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [pointerTracking, cardSpacing, setHandCardOrder, stopDraggingCard])

  return (
    <div
      style={{
        position: 'relative',
        width: totalWidth,
        height: cardHeight + maxVerticalOffset + 40, // Extra space for hover lift
        marginBottom: inverted ? 0 : edgeMargin,
        marginTop: inverted ? edgeMargin : 0,
      }}
    >
      {items.map((item, index) => {
        // Calculate position from center (-1 to 1)
        const centerOffset = cardCount > 1
          ? (index - (cardCount - 1) / 2) / ((cardCount - 1) / 2)
          : 0

        // Calculate rotation (edges rotate away from center)
        const rotation = centerOffset * maxRotation * rotationMultiplier

        // Calculate vertical offset (arc shape - center cards are higher/lower)
        const verticalOffset = (1 - Math.abs(centerOffset) ** 1.5) * maxVerticalOffset

        // Calculate horizontal position
        const left = index * cardSpacing

        // Z-index: center cards on top, but dragged card always on top
        const isDragging = reorderDragCardId !== null && item.type === 'card' && reorderDragCardId === item.card.id
        const zIndex = isDragging ? 200 : 50 - Math.abs(index - Math.floor(cardCount / 2))

        const key = item.type === 'card' ? item.card.id : `placeholder-${item.index}`

        // Apply drag offset to the card being dragged
        const dragTranslateX = isDragging ? reorderCurrentX : 0

        return (
          <div
            key={key}
            onPointerDown={
              item.type === 'card' && !item.isGhost
                ? (e) => handleReorderPointerDown(e, item.card.id, index)
                : undefined
            }
            style={{
              position: 'absolute',
              left,
              ...(inverted
                ? { top: edgeMargin, transform: `translateX(${dragTranslateX}px) translateY(${verticalOffset}px) rotate(${rotation}deg)` }
                : { bottom: edgeMargin, transform: `translateX(${dragTranslateX}px) translateY(${-verticalOffset}px) rotate(${rotation}deg)` }
              ),
              transformOrigin: inverted ? 'top center' : 'bottom center',
              zIndex,
              transition: isDragging ? 'none' : 'transform 0.12s ease-out, left 0.12s ease-out',
              cursor: interactive ? 'pointer' : 'default',
              opacity: isDragging ? 0.85 : 1,
            }}
            onMouseEnter={() => !inverted && setHoveredIndex(index)}
            onMouseLeave={() => !inverted && setHoveredIndex(null)}
          >
            {item.type === 'card' ? (
              <GameCard
                card={item.card}
                count={1}
                faceDown={faceDown && !item.showFaceUp}
                interactive={interactive}
                small={small}
                overrideWidth={fittingWidth}
                inHand={interactive && !faceDown}
                isGhost={item.isGhost}
              />
            ) : (
              <div
                style={{
                  width: fittingWidth,
                  height: cardHeight,
                  borderRadius: 6,
                  border: '2px solid #333',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.5)',
                  overflow: 'hidden',
                }}
              >
                <img
                  src={CARD_BACK_IMAGE_URL}
                  alt="Card back"
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
