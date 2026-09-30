package com.yunxigames;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 变脸玩法的<b>态度判定核心</b>：生物对某个「玩家主手物品」的态度。
 *
 * <p>三种态度（为什么是函数而不是每只生物存一个状态字段：态度完全由主手物品决定，
 * 存状态就要处理「什么时候失效」，而纯函数逐刻求值天然零持久化、零同步、切换零延迟）：
 * <ul>
 *   <li>{@link Attitude#FEAR} —— 主手是战斗用品（剑/斧/矛标签族 + 三叉戟/重锤/弓/弩散件），
 *       生物掉头就跑；</li>
 *   <li>{@link Attitude#TEMPT} —— 主手是<b>该生物</b>的美食（判定直接复用原版
 *       {@code Animal#isFood}，mod 动物自动兼容），该生物不攻击还被诱惑跟着走；</li>
 *   <li>{@link Attitude#HOSTILE} —— 其他任何东西（包括空手），所有生物尝试攻击玩家。</li>
 * </ul>
 *
 * <p>美食豁免是<b>逐物种</b>的：拿着小麦是「对牛的护身符」，旁边的猪照咬；
 * 拿某生物不爱吃的东西等于空手。敌对生物（僵尸等）不是 {@code Animal}，永远没有美食豁免。
 */
public final class MobAttitude {
	/** 生物对「玩家主手拿某物品的玩家」的三种态度。 */
	public enum Attitude {
		/** 恐惧：掉头就跑。 */
		FEAR,
		/** 诱惑：不攻击，被美食牵着走。 */
		TEMPT,
		/** 敌意：尝试攻击玩家。 */
		HOSTILE
	}

	/**
	 * 武器索引：把配置里的标签族 + 散件 id 展开成 {@code Set<Item>}，主手判定就是一次
	 * 哈希查找。为什么启动时展开而不是逐刻查标签：Goal 的 canUse 每个 AI 刻都要跑，
	 * 逐刻遍历标签内容是白烧 CPU；索引一次，之后 O(1)。
	 */
	private static volatile Set<Item> weaponIndex = Set.of();
	private static volatile boolean indexed = false;

	private MobAttitude() {
	}

	/** 把当前配置的武器清单展开成索引（幂等；配置 load 后与开服自检前各调一次足够）。 */
	public static synchronized void reindex(FacesConfig config) {
		Set<Item> items = new HashSet<>();

		for (String tagId : config.weaponTags) {
			Identifier id = Identifier.tryParse(tagId == null ? "" : tagId.trim().toLowerCase(java.util.Locale.ROOT));
			if (id == null) {
				continue;
			}
			// getTagOrEmpty：标签不存在（如第三方包没装）时返回空集，安全
			for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
				items.add(holder.value());
			}
		}

		for (String itemId : config.weaponItems) {
			Identifier id = Identifier.tryParse(itemId == null ? "" : itemId.trim().toLowerCase(java.util.Locale.ROOT));
			if (id == null) {
				continue;
			}
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item != Items.AIR) {
				items.add(item);
			}
		}

		weaponIndex = Set.copyOf(items);
		indexed = true;
	}

	/** 主手物品是不是战斗用品（恐惧触发物）。 */
	public static boolean isWeapon(ItemStack hand) {
		if (!indexed) {
			reindex(FacesConfig.get());
		}
		return !hand.isEmpty() && weaponIndex.contains(hand.getItem());
	}

	/** 主手物品是不是「该生物的美食」（直接复用原版 Animal#isFood，mod 动物自动兼容）。 */
	public static boolean isFavoriteFood(Mob mob, ItemStack hand) {
		return mob instanceof net.minecraft.world.entity.animal.Animal animal
				&& animal.isFood(hand);
	}

	/**
	 * 生物对「主手拿着 hand 的玩家」的态度 —— 变脸玩法的唯一真相源。
	 * 顺序很重要：武器恐惧 &gt; 美食诱惑 &gt; 敌意（武器永远是最强信号）。
	 */
	public static Attitude attitudeOf(Mob mob, ItemStack hand) {
		if (isWeapon(hand)) {
			return Attitude.FEAR;
		}
		if (isFavoriteFood(mob, hand)) {
			return Attitude.TEMPT;
		}
		return Attitude.HOSTILE;
	}

	/**
	 * 这只生物要不要被注入变脸 Goal：
	 * 总开关开着、不在豁免名单（Boss）、且是 {@link PathfinderMob}（有寻路才能追人/逃跑；
	 * 幻翼、恶魂这类非寻路飞行生物不参与，与系列「自检要在真服务器跑」的约定一致）。
	 */
	public static boolean shouldInject(Mob mob) {
		FacesConfig config = FacesConfig.get();
		return config.facesEnabled
				&& mob instanceof PathfinderMob
				&& !config.isMobExcluded(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
	}
}
