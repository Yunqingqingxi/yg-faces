package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-faces 冒烟测试：mod 描述文件与 mixin 配置合法、配置能从零生成并写回、改动能落盘再读回。
 */
@Tag("smoke")
class FacesSmokeTest {

	private static final Gson GSON = new Gson();

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
	void fabricModJsonIsValidWithCorrectModId() throws Exception {
		try (var in = getClass().getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(in, "fabric.mod.json 必须在 jar 资源里");
			JsonObject json = GSON.fromJson(
					new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			assertEquals("yg_faces", json.get("id").getAsString());
			assertEquals(1, json.get("schemaVersion").getAsInt());
			assertNotNull(json.get("entrypoints").getAsJsonObject().get("main"));
		}
	}

	@Test
	void mixinConfigListsMobAttitudeMixin() throws Exception {
		try (var in = getClass().getResourceAsStream("/yg-faces.mixins.json")) {
			assertNotNull(in, "yg-faces.mixins.json 必须在 jar 资源里");
			JsonObject json = GSON.fromJson(
					new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			assertEquals("com.yunxigames.mixin", json.get("package").getAsString(), "mixin 包路径必须正确");
			assertTrue(json.get("mixins").getAsJsonArray().toString().contains("MobAttitudeMixin"),
					"变脸注入器必须在 mixin 清单里");
		}
	}

	@Test
	void loadCreatesDefaultConfigFileOnDisk() {
		FacesConfig cfg = FacesConfig.load();
		assertTrue(Files.isRegularFile(configDir.resolve(FacesConfig.FILE_NAME)),
				"load() 后配置文件必须已写回磁盘");
		assertTrue(cfg.facesEnabled, "变脸默认开");
		assertEquals(2.0D, cfg.attackDamage, "友好生物默认伤害 1 颗心");
		assertFalse(cfg.excludedMobs.isEmpty(), "默认豁免名单（凋灵/监守者）应就位");
	}

	@Test
	void modifiedValuesSurviveSaveLoadRoundtrip() {
		FacesConfig cfg = FacesConfig.load();
		cfg.attackDamage = 4.0D;
		cfg.weaponFleeDistance = 20.0D;
		cfg.fleeSpeed = 2.0D;
		cfg.save();

		FacesConfig reloaded = FacesConfig.load();
		assertEquals(4.0D, reloaded.attackDamage);
		assertEquals(20.0D, reloaded.weaponFleeDistance);
		assertEquals(2.0D, reloaded.fleeSpeed);
	}

	private static void assertFalse(boolean value, String message) {
		org.junit.jupiter.api.Assertions.assertFalse(value, message);
	}
}
