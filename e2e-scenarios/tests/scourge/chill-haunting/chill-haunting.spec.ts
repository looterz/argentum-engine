import { test } from '../../../fixtures/scenarioFixture'

/**
 * E2E test: Chill Haunting exile-from-graveyard additional cost + creature targeting.
 *
 * Setup:
 * - P1 has Chill Haunting in hand, 2 creature cards in graveyard, 2 Swamps
 * - P2 has a Hill Giant (3/3) on battlefield
 *
 * Flow:
 * 1. P1 casts Chill Haunting, selecting both creature cards from graveyard (exile 2)
 * 2. Then targets P2's Hill Giant
 * 3. Spell resolves: Hill Giant gets -2/-2, becoming 1/1
 */
test('exile 2 creatures from graveyard gives target -2/-2', async ({ createGame }) => {
  const { player1, player2 } = await createGame({
    player1Name: 'Caster',
    player2Name: 'Opponent',
    player1: {
      hand: ['Chill Haunting'],
      graveyard: ['Grizzly Bears', 'Glory Seeker'],
      battlefield: [
        { name: 'Swamp', tapped: false },
        { name: 'Swamp', tapped: false },
      ],
      library: ['Swamp'],
    },
    player2: {
      battlefield: [{ name: 'Hill Giant', tapped: false, summoningSickness: false }],
      library: ['Mountain'],
    },
    phase: 'PRECOMBAT_MAIN',
    activePlayer: 1,
  })

  const p1 = player1.gamePage
  const p2 = player2.gamePage

  // Cast Chill Haunting
  await p1.clickCard('Chill Haunting')
  await p1.selectAction('Cast')

  // Should see graveyard selection overlay — select both creatures
  await p1.selectCardInZoneOverlay('Grizzly Bears')
  await p1.selectCardInZoneOverlay('Glory Seeker')
  await p1.confirmSelection()

  // Now should enter creature targeting mode — target Hill Giant
  await p1.selectTarget('Hill Giant')
  await p1.confirmTargets()

  // Opponent resolves
  await p2.resolveStack('Chill Haunting')

  // Hill Giant (3/3) should now be 1/1
  await p1.expectStats('Hill Giant', '1/1')
})
