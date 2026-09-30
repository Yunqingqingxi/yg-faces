package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 变脸（yunxigames faces 包）的独立配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/yg-faces.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会补回默认值（见基类 mergeMissingFields），升级后旧配置文件依然可用。
 *
 * <p><b>默认值原则「爽但不劝退」</b>：友好生物的攻击伤害默认 2.0（1 颗心）——
 * 被一群牛围殴会掉血、会紧张，但空手被鸡啄两下不至于直接送命；武器恐惧距离默认 12 格，
 * 让「亮出剑」真正成为全场驱散道具而不是贴脸才生效。
 */
public final class FacesConfig extends YgConfig {
	public static final String FILE_NAME = "yg-faces.json";

	// ---------- 变脸玩法 ----------

	/** 总开关：关闭后不向任何生物注入变脸 Goal（已在场上的生物重启世界后恢复原版行为）。 */
	public boolean facesEnabled = true;

	/**
	 * 友好生物攻击玩家的伤害（点数，2.0 = 1 颗心）。
	 * 为什么不依赖原版攻击力属性：牛羊等生物的属性表里根本没有 ATTACK_DAMAGE，
	 * 运行期补属性要动 DefaultAttributes（大 mixin），不如自己出伤害值还可配置。
	 */
	public double attackDamage = 2.0;

	/** 武器恐惧距离（格）：玩家主手拿战斗用品时，该距离内的生物掉头就跑。 */
	public double weaponFleeDistance = 12.0;

	/** 恐惧逃跑速度倍率（1.0 = 原版行走速度；生物基础速度各不相同，这是乘上去的倍率）。 */
	public double fleeSpeed = 1.4;

	/** 美食诱惑跟随速度倍率（拿着某生物的美食时，该生物跟着玩家走的速度）。 */
	public double temptSpeed = 1.1;

	/**
	 * 战斗用品判定：原版<b>物品标签</b>列表。默认剑 / 斧 / 矛三族（矛 = 26.2 新增的长矛，
	 * {@code #minecraft:spears} 一个标签覆盖木→下界合金六种材质）。第三方 mod 往这些
	 * 标签里加的武器自动生效，无需改本配置。
	 */
	public List<String> weaponTags = new ArrayList<>(List.of(
			"minecraft:swords", "minecraft:axes", "minecraft:spears"));

	/**
	 * 战斗用品判定补充：散件<b>物品 id</b> 列表（没有家族标签的原版武器）。
	 * 三叉戟 / 重锤 / 弓 / 弩默认在此。
	 */
	public List<String> weaponItems = new ArrayList<>(List.of(
			"minecraft:trident", "minecraft:mace", "minecraft:bow", "minecraft:crossbow"));

	/**
	 * 豁免名单：这些生物永远不变脸（不注入任何 Goal）。默认只豁免 Boss
	 * （凋灵 / 监守者；末影龙不是 Mob 体系天然不参与）。支持精确 id 与
	 * {@code 命名空间:*} 通配 —— 第三方 mod 的 Boss 也可以在这里挡掉。
	 * 注意：按本包约定，驯服的宠物<b>不</b>豁免——狼也会翻脸咬主人。
	 */
	public List<String> excludedMobs = new ArrayList<>(List.of(
			"minecraft:wither", "minecraft:warden"));

	private transient YgConfig.IdFilter excludedFilter = YgConfig.IdFilter.EMPTY;

	public boolean isMobExcluded(net.minecraft.resources.Identifier id) {
		return excludedFilter.matches(id);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("yg-faces.json");
	private static volatile FacesConfig instance;

	FacesConfig() {  // 包内可见：单元测试与 YgConfig 缺项补回需要 new 默认实例
	}

	/** 自检专用：造一份全新默认配置（绕开单例，不落盘、不影响运行中的 instance）。 */
	static FacesConfig blankForTest() {
		return new FacesConfig();
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static FacesConfig get() {
		FacesConfig local = instance;
		if (local == null) {
			synchronized (FacesConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized FacesConfig load() {
		Path path = configPath(FILE_NAME);
		FacesConfig loaded = null;
		com.google.gson.JsonObject raw = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				// 先解析成 JsonObject 留底：merge 时用它区分「json 里没写这一项」和「明确写了 false」
				raw = GSON.fromJson(reader, com.google.gson.JsonObject.class);
				loaded = GSON.fromJson(raw, FacesConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[yg-faces.json] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new FacesConfig();
		} else {
			mergeMissingFields(loaded, raw, new FacesConfig());
		}

		loaded.validate();
		instance = loaded;
		loaded.save();
		return loaded;
	}

	/** 把当前配置写回磁盘。 */
	public synchronized void save() {
		Path path = configPath(FILE_NAME);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("[yg-faces.json] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/** 修正越界 / 缺失的值，并解析过滤器（NaN 一并治：!(x>=lo && x<=hi) 对 NaN 恒真）。 */
	void validate() {
		if (weaponTags == null || weaponTags.isEmpty()) {
			weaponTags = new ArrayList<>(List.of(
					"minecraft:swords", "minecraft:axes", "minecraft:spears"));
		}
		if (weaponItems == null || weaponItems.isEmpty()) {
			weaponItems = new ArrayList<>(List.of(
					"minecraft:trident", "minecraft:mace", "minecraft:bow", "minecraft:crossbow"));
		}
		if (excludedMobs == null || excludedMobs.isEmpty()) {
			excludedMobs = new ArrayList<>(List.of(
					"minecraft:wither", "minecraft:warden"));
		}
		excludedFilter = parseFilter(excludedMobs, "excludedMobs");

		if (!(attackDamage >= 0.5 && attackDamage <= 20.0)) attackDamage = 2.0;
		if (!(weaponFleeDistance >= 4.0 && weaponFleeDistance <= 64.0)) weaponFleeDistance = 12.0;
		if (!(fleeSpeed >= 0.5 && fleeSpeed <= 3.0)) fleeSpeed = 1.4;
		if (!(temptSpeed >= 0.5 && temptSpeed <= 3.0)) temptSpeed = 1.1;
	}
}
