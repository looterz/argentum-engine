import { useMemo } from 'react'
import { useGameStore } from '@/store/gameStore.ts'
import { selectGameState, useCardLegalActions } from '@/store/selectors.ts'
import { getCardImageUrl } from '@/utils/cardImages.ts'
import { useResponsiveContext, handleImageError, getCounterStatModifier, hasStatCounters, getTokenFrameGradient, getTokenFrameTextColor, getPTColor } from '../board/shared'
import { styles } from '../board/styles'
import { counterManaClass } from '@/assets/icons/keywords'
import { HoverCardPreview } from '../../ui/HoverCardPreview'
import { ManaCost } from '../../ui/ManaSymbols'

/**
 * Game board card preview — wraps the shared HoverCardPreview with
 * game-specific extras: token frames, stat breakdowns, keywords, revealed labels.
 */
export function CardPreview() {
  const hoveredCardId = useGameStore((state) => state.hoveredCardId)
  const hoverPosition = useGameStore((state) => state.hoverPosition)
  const gameState = useGameStore(selectGameState)
  const responsive = useResponsiveContext()

  // All hooks must be called before any early return
  const cardActions = useCardLegalActions(hoveredCardId)
  const card = hoveredCardId && gameState ? gameState.cards[hoveredCardId] ?? null : null
  const manaCostInfo = useMemo(() => {
    if (!card?.manaCost) return null
    const castAction = cardActions.find((a) =>
      a.action.type === 'CastSpell' && a.actionType !== 'CastFaceDown' && a.actionType !== 'CastWithKicker' && a.actionType !== 'CastSpellMode'
    )
    const effectiveCost = castAction?.manaCostString
    // No cast action or cost unchanged — show base cost without modification indicator
    if (effectiveCost == null || effectiveCost === card.manaCost) {
      return { baseCost: card.manaCost, effectiveCost: null, isReduced: false, isIncreased: false }
    }
    const countSymbols = (cost: string) => {
      const symbols = cost.match(/\{([^}]+)\}/g) ?? []
      return symbols.reduce((total, s) => {
        const inner = s.slice(1, -1)
        const num = parseInt(inner, 10)
        return total + (isNaN(num) ? 1 : num)
      }, 0)
    }
    const baseMV = countSymbols(card.manaCost)
    const effectiveMV = countSymbols(effectiveCost)
    return {
      baseCost: card.manaCost,
      effectiveCost: effectiveCost === '' ? '{0}' : effectiveCost,
      isReduced: effectiveMV < baseMV,
      isIncreased: effectiveMV > baseMV,
    }
  }, [cardActions, card?.manaCost])

  if (!card) return null

  // On mobile, show the fullscreen overlay (game-specific behaviour)
  if (responsive.isMobile) {
    return <MobileCardPreview card={card} />
  }

  const isRevealedFaceDown = card.isFaceDown && !!card.revealedName
  const displayName = isRevealedFaceDown ? card.revealedName! : card.name
  const displayImageUri = isRevealedFaceDown ? (card.revealedImageUri ?? undefined) : card.imageUri

  // Determine if stats are modified
  const isPowerBuffed = card.power !== null && card.basePower !== null && card.power > card.basePower
  const isPowerDebuffed = card.power !== null && card.basePower !== null && card.power < card.basePower
  const isToughnessBuffed = card.toughness !== null && card.baseToughness !== null && card.toughness > card.baseToughness
  const isToughnessDebuffed = card.toughness !== null && card.baseToughness !== null && card.toughness < card.baseToughness
  const hasStatModifications = isPowerBuffed || isPowerDebuffed || isToughnessBuffed || isToughnessDebuffed

  const counterModifier = getCounterStatModifier(card)
  const hasCounters = hasStatCounters(card)
  const effectPowerMod = card.power !== null && card.basePower !== null
    ? (card.power - card.basePower) - counterModifier : 0
  const effectToughnessMod = card.toughness !== null && card.baseToughness !== null
    ? (card.toughness - card.baseToughness) - counterModifier : 0
  const hasEffects = effectPowerMod !== 0 || effectToughnessMod !== 0

  // Estimate extra height for positioning
  let extraHeight = 0
  const GAP = 8
  if (manaCostInfo) extraHeight += 36 + GAP
  if (hasStatModifications) extraHeight += 80 + GAP
  if (card.keywords.length > 0 || (card.abilityFlags && card.abilityFlags.length > 0)) extraHeight += 40 + GAP

  return (
    <HoverCardPreview
      name={displayName}
      imageUri={displayImageUri ?? null}
      pos={hoverPosition}
      rulings={card.rulings}
      extraHeight={extraHeight}
    >
      {/* Mana cost panel — always shown, with modification indicator when cost changes */}
      {manaCostInfo && (
        <div style={{
          ...styles.cardPreviewStatsBox,
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          gap: 8,
          padding: '6px 12px',
        }}>
          {manaCostInfo.effectiveCost ? (
            <>
              <span style={{
                color: '#888',
                fontSize: 12,
                textDecoration: 'line-through',
                display: 'flex',
                alignItems: 'center',
              }}>
                <ManaCost cost={manaCostInfo.baseCost} size={14} gap={1} />
              </span>
              <span style={{ color: '#888', fontSize: 14 }}>&rarr;</span>
              <span style={{
                display: 'flex',
                alignItems: 'center',
                filter: manaCostInfo.isReduced
                  ? 'drop-shadow(0 0 3px rgba(0, 200, 80, 0.5))'
                  : manaCostInfo.isIncreased
                    ? 'drop-shadow(0 0 3px rgba(255, 68, 68, 0.5))'
                    : 'none',
              }}>
                <ManaCost cost={manaCostInfo.effectiveCost} size={16} gap={1} />
              </span>
            </>
          ) : (
            <span style={{ display: 'flex', alignItems: 'center' }}>
              <ManaCost cost={manaCostInfo.baseCost} size={16} gap={1} />
            </span>
          )}
        </div>
      )}

      {/* Stats box (for creatures with modifications) */}
      {card.power !== null && card.toughness !== null && hasStatModifications && (
        <div style={styles.cardPreviewStatsBox}>
          <div style={styles.cardPreviewStatsMain}>
            <span style={{
              color: isPowerBuffed ? '#00ff00' : isPowerDebuffed ? '#ff4444' : '#ffffff',
              fontWeight: 700, fontSize: 26,
            }}>
              {card.power}
            </span>
            <span style={{ color: '#ffffff', fontSize: 26 }}>/</span>
            <span style={{
              color: isToughnessBuffed ? '#00ff00' : isToughnessDebuffed ? '#ff4444' : '#ffffff',
              fontWeight: 700, fontSize: 26,
            }}>
              {card.toughness}
            </span>
          </div>
          <div style={styles.cardPreviewStatsBreakdown}>
            {card.basePower !== null && card.baseToughness !== null && (
              <div style={styles.cardPreviewStatsRow}>
                <span style={styles.cardPreviewStatsLabel}>Base</span>
                <span style={styles.cardPreviewStatsValue}>
                  {card.basePower}/{card.baseToughness}
                </span>
              </div>
            )}
            {hasCounters && (
              <div style={styles.cardPreviewStatsRow}>
                <span style={{...styles.cardPreviewStatsLabel, color: '#66ccff'}}>
                  <i className={`ms ms-${counterModifier >= 0 ? counterManaClass.PLUS_ONE_PLUS_ONE : counterManaClass.MINUS_ONE_MINUS_ONE}`} style={{marginRight: 4, fontSize: 10}} />Counters
                </span>
                <span style={{...styles.cardPreviewStatsValue, color: counterModifier >= 0 ? '#66ccff' : '#ff6666'}}>
                  {counterModifier >= 0 ? '+' : ''}{counterModifier}/{counterModifier >= 0 ? '+' : ''}{counterModifier}
                </span>
              </div>
            )}
            {hasEffects && (
              <div style={styles.cardPreviewStatsRow}>
                <span style={{...styles.cardPreviewStatsLabel, color: '#ffcc66'}}>Effects</span>
                <span style={{...styles.cardPreviewStatsValue, color: '#ffcc66'}}>
                  {effectPowerMod >= 0 ? '+' : ''}{effectPowerMod}/{effectToughnessMod >= 0 ? '+' : ''}{effectToughnessMod}
                </span>
              </div>
            )}
            {card.damage != null && card.damage > 0 && (
              <div style={styles.cardPreviewDamageRow}>
                <span>Damage</span>
                <span style={{ fontWeight: 600, fontFamily: 'monospace' }}>
                  {card.damage}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Keywords/abilities info panel */}
      {(card.keywords.length > 0 || (card.abilityFlags && card.abilityFlags.length > 0)) && (
        <div style={styles.cardPreviewKeywords}>
          {card.keywords.map((keyword) => (
            <div key={keyword} style={styles.cardPreviewKeyword}>
              <span style={styles.cardPreviewKeywordName}>{keyword}</span>
            </div>
          ))}
          {card.abilityFlags?.map((flag) => (
            <div key={flag} style={styles.cardPreviewKeyword}>
              <span style={styles.cardPreviewKeywordName}>{flag}</span>
            </div>
          ))}
        </div>
      )}
    </HoverCardPreview>
  )
}

/**
 * Mobile fullscreen card preview overlay (game-specific).
 */
function MobileCardPreview({ card }: { card: import('@/types').ClientCard }) {
  const isRevealedFaceDown = card.isFaceDown && !!card.revealedName
  const cardImageUrl = isRevealedFaceDown
    ? getCardImageUrl(card.revealedName!, card.revealedImageUri ?? undefined, 'large')
    : getCardImageUrl(card.name, card.imageUri, 'large')

  const previewWidth = 200
  const previewHeight = Math.round(previewWidth * 1.4)

  return (
    <div style={{
      ...styles.cardPreviewOverlay,
      top: 0, left: 0, right: 0, bottom: 0,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      backgroundColor: 'rgba(0, 0, 0, 0.6)',
    }}>
      <div style={{ ...styles.cardPreviewContainer, width: previewWidth }}>
        <div style={{
          ...styles.cardPreviewCard,
          position: 'relative',
          width: previewWidth,
          height: previewHeight,
        }}>
          {card.isToken && card.imageUri ? (
            <div style={{
              ...styles.tokenFrame,
              background: getTokenFrameGradient(card.colors),
              borderRadius: 12,
            }}>
              <div style={{
                ...styles.tokenNameBar,
                color: getTokenFrameTextColor(card.colors),
                fontSize: 14, padding: '5px 10px',
                borderRadius: '8px 8px 0 0',
              }}>
                {card.name}
              </div>
              <div style={styles.tokenArtBox}>
                <img src={cardImageUrl} alt={card.name} style={styles.tokenArtImage} />
              </div>
              <div style={{
                ...styles.tokenTypeBar,
                color: getTokenFrameTextColor(card.colors),
                fontSize: 11, padding: '4px 10px',
                borderRadius: '0 0 8px 8px',
              }}>
                {card.typeLine}
              </div>
              {card.power !== null && card.toughness !== null && (
                <div style={{
                  ...styles.tokenPreviewPT,
                  color: getPTColor(card.power, card.toughness, card.basePower, card.baseToughness),
                }}>
                  {card.power}/{card.toughness}
                </div>
              )}
            </div>
          ) : (
            <img
              src={cardImageUrl}
              alt={isRevealedFaceDown ? card.revealedName! : card.name}
              style={styles.cardPreviewImage}
              onError={(e) => handleImageError(e, isRevealedFaceDown ? card.revealedName! : card.name, 'large')}
            />
          )}
          {isRevealedFaceDown && (
            <div style={{
              position: 'absolute', top: 8, left: '50%', transform: 'translateX(-50%)',
              backgroundColor: 'rgba(0, 0, 0, 0.75)', color: '#66ccff',
              fontSize: 12, fontWeight: 600, padding: '2px 10px', borderRadius: 4,
              border: '1px solid rgba(102, 204, 255, 0.5)', pointerEvents: 'none', whiteSpace: 'nowrap',
            }}>
              Revealed
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
