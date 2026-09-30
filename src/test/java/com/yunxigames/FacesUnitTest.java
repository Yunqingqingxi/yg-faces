package com.yunxigames;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-faces 单元测试：validate() 钳制（含 NaN 挡板）、豁免名单过滤器（精确 id + 命名空间通配）。
 */
class FacesUnitTest {

	@TempDir
	Path configDir;

	@BeforeEach
	void injectConfigDir() {
		YgConfig.configDirOverride = configDir;
	}

	@AfterEach
	void resetConfigDir() {
		YgConfig.configDirOverride = null;
	}

	private static FacesConfig fresh() {
		return FacesConfig.blankForTest();
	}

	@Test
	void nanClampsFallBackToDefaults() {
		FacesConfig cfg = fresh();
		cfg.attackDamage = Double.NaN;
		cfg.weaponFleeDistance = Double.NaN;
		cfg.fleeSpeed = Double.NaN;
		cfg.temptSpeed = Double.NaN;
		cfg.validate();

		assertEquals(2.0D, cfg.attackDamage, "NaN 伤害落默认（!(x>=lo && x<=hi) 对 NaN 恒真）");
		assertEquals(12.0D, cfg.weaponFleeDistance);
		assertEquals(1.4D, cfg.fleeSpeed);
		assertEquals(1.1D, cfg.temptSpeed);
	}

	@Test
	void outOfRangeClampsFallBackToDefaults() {
		FacesConfig cfg = fresh();
		cfg.attackDamage = 100.0D;
		cfg.weaponFleeDistance = 0.5D;
		cfg.fleeSpeed = 50.0D;
		cfg.temptSpeed = -3.0D;
		cfg.validate();

		assertEquals(2.0D, cfg.attackDamage);
		assertEquals(12.0D, cfg.weaponFleeDistance);
		assertEquals(1.4D, cfg.fleeSpeed);
		assertEquals(1.1D, cfg.temptSpeed);
	}

	@Test
	void boundaryValuesAreKept() {
		FacesConfig cfg = fresh();
		cfg.attackDamage = 0.5D;
		cfg.weaponFleeDistance = 64.0D;
		cfg.fleeSpeed = 3.0D;
		cfg.temptSpeed = 0.5D;
		cfg.validate();

		assertEquals(0.5D, cfg.attackDamage, "下界 0.5 不吞");
		assertEquals(64.0D, cfg.weaponFleeDistance, "上界 64 不吞");
		assertEquals(3.0D, cfg.fleeSpeed);
		assertEquals(0.5D, cfg.temptSpeed);
	}

	@Test
	void exclusionFilterSupportsExactAndNamespaceWildcard() {
		FacesConfig cfg = fresh();
		cfg.excludedMobs = new ArrayList<>(List.of(
				"minecraft:wither", "SomeBossMod:*"));
		cfg.validate();

		assertTrue(cfg.isMobExcluded(net.minecraft.resources.Identifier.parse("minecraft:wither")), "精确 id 命中");
		assertFalse(cfg.isMobExcluded(net.minecraft.resources.Identifier.parse("minecraft:cow")), "名单外不命中");
		assertTrue(cfg.isMobExcluded(net.minecraft.resources.Identifier.parse("somebossmod:dragon_lord")),
				"通配大小写规范化后命中整个命名空间");
		assertTrue(cfg.isMobExcluded(net.minecraft.resources.Identifier.parse("somebossmod:anything")));
	}

	@Test
	void nullListsAreRecoveredByValidate() {
		FacesConfig cfg = fresh();
		cfg.weaponTags = null;
		cfg.weaponItems = null;
		cfg.excludedMobs = null;
		cfg.validate();

		assertFalse(cfg.weaponTags.isEmpty(), "null 列表必须回默认，validate 后不得再是 null");
		assertFalse(cfg.weaponItems.isEmpty());
		assertFalse(cfg.excludedMobs.isEmpty());
	}
}
