package com.yunxigames;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-faces 回归测试：钉死 Gson 缺项补回与「显式 false 不可被偷改」的历史坑。
 * 本包从诞生起就用 JsonObject 存在性检查（raw.has），这里保证它不退化。
 */
class FacesRegressionTest {

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

	@Test
	void missingBooleanFieldsFallBackToCodeDefaultTrue() throws Exception {
		Files.writeString(configDir.resolve(FacesConfig.FILE_NAME),
				"{\"facesEnabled\": true}");
		FacesConfig cfg = FacesConfig.load();
		assertTrue(cfg.facesEnabled, "缺项布尔必须补回代码默认 true");
	}

	@Test
	void explicitFalseInJsonMustNotBeOverwritten() throws Exception {
		Files.writeString(configDir.resolve(FacesConfig.FILE_NAME),
				"{\"facesEnabled\": false}");
		FacesConfig cfg = FacesConfig.load();
		assertFalse(cfg.facesEnabled, "玩家明确写 false 必须保持 false（关掉变脸是合法选择）");
	}

	@Test
	void endermanWeaponCalmMissingRecoversTrueExplicitFalseStaysFalse() throws Exception {
		Files.writeString(configDir.resolve(FacesConfig.FILE_NAME), "{}");
		assertTrue(FacesConfig.load().endermanWeaponCalm, "缺项 endermanWeaponCalm 必须补回 true");

		Files.writeString(configDir.resolve(FacesConfig.FILE_NAME),
				"{\"endermanWeaponCalm\": false}");
		assertFalse(FacesConfig.load().endermanWeaponCalm, "显式 false 不许被偷改（关末影人特殊规则是合法选择）");
	}

	@Test
	void corruptedConfigFallsBackToDefaults() throws Exception {
		Files.writeString(configDir.resolve(FacesConfig.FILE_NAME), "{{{不是json");
		FacesConfig cfg = FacesConfig.load();
		assertTrue(cfg.facesEnabled, "损坏文件应回退到默认配置而不是崩溃");
		assertFalse(cfg.excludedMobs.isEmpty(), "默认豁免名单应就位");
		assertEquals(0, cfg.selfTestRolls, "自检掷骰默认必须为 0（别提交带 200 的配置）");
	}

	@Test
	void legacyConfigWithUnknownFieldsStillLoads() throws Exception {
		// 未来删字段时旧配置要能读 —— Gson 对未知字段天然忽略，现在就钉死这个行为
		Files.writeString(configDir.resolve(FacesConfig.FILE_NAME),
				"{\"facesEnabled\": true, \"someRemovedField\": 42}");
		FacesConfig cfg = FacesConfig.load();
		assertTrue(cfg.facesEnabled, "含未知字段的旧配置必须能读");
	}

	@Test
	void missingListFieldsTakeDefaults() throws Exception {
		// 只写了武器散件、没写标签族与豁免名单 —— 缺项列表必须补默认而不是 null
		Files.writeString(configDir.resolve(FacesConfig.FILE_NAME),
				"{\"facesEnabled\": true, \"weaponItems\": [\"minecraft:trident\"]}");
		FacesConfig cfg = FacesConfig.load();
		assertFalse(cfg.weaponTags.isEmpty(), "缺项 weaponTags 补默认（剑/斧/矛标签族）");
		assertEquals(1, cfg.weaponItems.size(), "显式写的 weaponItems 原样保留");
		assertTrue(cfg.weaponItems.contains("minecraft:trident"));
		assertFalse(cfg.excludedMobs.isEmpty(), "缺项 excludedMobs 补默认（凋灵/监守者）");
		assertTrue(cfg.isMobExcluded(net.minecraft.resources.Identifier.parse("minecraft:wither")),
				"补默认之后过滤器也要同步重建（validate 里解析）");
	}
}
