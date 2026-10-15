package io.github.cwzmorro.fatecraft.client.trace;

/**
 * The vanilla player model's measures that Sword Body lays its blades on, in blocks (a pixel is 1/16 block), from the player's
 * feet with x to his left. Vanilla keeps them as pixel literals in {@code HumanoidModel}'s and {@code PlayerModel}'s part
 * builders and animation, so they are named once here.
 *
 * @param height the model's height: its frame starts this far above the feet, y pointing down
 * @param halfPixel what slim arms (Alex's) are narrower by on each side, and nearer the body
 * @param bodyMiddle the torso's middle above the feet
 * @param bodyHalfWidth the torso's half width
 * @param bodyHalfDepth the torso's half depth
 * @param limbHalf an arm's or leg's half width and depth
 * @param partHalfLength the torso's, an arm's and a leg's half length
 * @param shoulderHeight where an arm turns, above the feet
 * @param shoulderOut where an arm turns, out from the middle
 * @param armMiddleOut an arm's middle, further out than where it turns
 * @param armMiddleDown an arm's middle, below where it turns
 * @param hipHeight where a leg turns, above the feet
 * @param hipOut where a leg turns, out from the middle
 * @param limbPhase radians the limbs' walking swing turns through per block of the walk animation
 * @param sneakDrop how far the model lowers while sneaking
 */
record VanillaHumanoid(double height, double halfPixel, double bodyMiddle, double bodyHalfWidth, double bodyHalfDepth, double limbHalf,
	double partHalfLength, double shoulderHeight, double shoulderOut, double armMiddleOut, double armMiddleDown, double hipHeight, double hipOut,
	double limbPhase, double sneakDrop) {
	static final VanillaHumanoid PLAYER = new VanillaHumanoid(1.5, 0.03125, 1.125, 0.25, 0.125, 0.125, 0.375, 1.375, 0.3125, 0.0625, 0.25,
		0.75, 0.125, 0.6662, 0.2);
}
