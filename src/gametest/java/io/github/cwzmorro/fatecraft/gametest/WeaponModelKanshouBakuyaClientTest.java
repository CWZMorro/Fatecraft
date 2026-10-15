package io.github.cwzmorro.fatecraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WeaponModelKanshouBakuyaClientTest extends WeaponModelExcaliburClientTest {
	private boolean swapped;

	@Override
	protected String mainHand() {
		return this.swapped ? "bakuya" : "kanshou";
	}

	@Override
	protected String offHand() {
		return this.swapped ? "kanshou" : "bakuya";
	}

	@Override
	protected String screenshotPrefix() {
		return this.mainHand() + "-" + this.offHand();
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		this.swapped = false;
		super.runTest(context);
		this.swapped = true;
		super.runTest(context);
	}
}
