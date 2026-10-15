package io.github.cwzmorro.fatecraft.gametest;

// Named like Ea's test, so -PclientTest=WeaponModel runs it too.
public class WeaponModelBabIluClientTest extends WeaponModelExcaliburClientTest {
	@Override
	protected String mainHand() {
		return "bab_ilu";
	}
}
