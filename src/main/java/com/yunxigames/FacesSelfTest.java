package com.yunxigames;

import com.yunxigames.MobAttitude.Attitude;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;

import static com.yunxigames.SelfTest.check;

/**
 * 变脸包的开服自检（①~⑤）。
 *
 * <p>自检只查「链路能不能用」：配置钳制、武器索引展开、态度纯函数、Goal 有没有挂上、
 * 豁免名单生效。真实游戏里「牛追着玩家顶」「僵尸见剑就跑」这类行为要进游戏目视确认 ——
 * 自检服务器上没有玩家，Goal 的 canUse 不会被触发，这部分覆盖不到（与 mobs 外观同理）。
 *
 * <p>所有测试实体只 {@code create}（构造）不 {@code addFreshEntity}（生成进世界）：
 * 态度判定与 Goal 注入都在构造器里完成，构造完即可断言、随取随弃，不污染世界。
 */
final class FacesSelfTest {
	private FacesSelfTest() {
	}

	// ------------------------------------------------------------ ① 配置钳制

	static void checkConfigClamps(SelfTest.Context ctx) {
		FacesConfig config = FacesConfig.blankForTest();

		// NaN / 越界 / 离谱值全部落回默认（!(x>=lo && x<=hi) 对 NaN 恒真，顺带治 NaN）
		config.attackDamage = Double.NaN;
		config.weaponFleeDistance = 999.0;
		config.fleeSpeed = -1.0;
		config.temptSpeed = 100.0;
		config.validate();

		check("配置钳制·NaN 伤害落默认", config.attackDamage == 2.0, "attackDamage=" + SelfTest.trim(config.attackDamage));
		check("配置钳制·恐惧距离越界落默认", config.weaponFleeDistance == 12.0,
				"weaponFleeDistance=" + SelfTest.trim(config.weaponFleeDistance));
		check("配置钳制·速度越界落默认", config.fleeSpeed == 1.4 && config.temptSpeed == 1.1,
				"fleeSpeed=" + SelfTest.trim(config.fleeSpeed) + " temptSpeed=" + SelfTest.trim(config.temptSpeed));

		// 边界值必须原样保留
		config.attackDamage = 20.0;
		config.weaponFleeDistance = 4.0;
		config.validate();
		check("配置钳制·边界值保留", config.attackDamage == 20.0 && config.weaponFleeDistance == 4.0, "上下界不吞合法值");
	}

	// ------------------------------------------------------------ ② 武器判定

	static void checkWeaponDetection(SelfTest.Context ctx) {
		FacesConfig config = FacesConfig.get();
		MobAttitude.reindex(config); // 自检前强制重建索引，与生产同一条路径

		check("武器判定·剑", MobAttitude.isWeapon(new ItemStack(Items.IRON_SWORD)), "iron_sword → 恐惧");
		check("武器判定·斧", MobAttitude.isWeapon(new ItemStack(Items.NETHERITE_AXE)), "netherite_axe → 恐惧");
		check("武器判定·矛（标签族覆盖六材质）", MobAttitude.isWeapon(new ItemStack(Items.IRON_SPEAR))
				&& MobAttitude.isWeapon(new ItemStack(Items.WOODEN_SPEAR)), "iron_spear / wooden_spear → 恐惧");
		check("武器判定·三叉戟", MobAttitude.isWeapon(new ItemStack(Items.TRIDENT)), "trident → 恐惧");
		check("武器判定·重锤", MobAttitude.isWeapon(new ItemStack(Items.MACE)), "mace → 恐惧");
		check("武器判定·弓弩", MobAttitude.isWeapon(new ItemStack(Items.BOW))
				&& MobAttitude.isWeapon(new ItemStack(Items.CROSSBOW)), "bow / crossbow → 恐惧");
		check("武器判定·非武器不误伤", !MobAttitude.isWeapon(new ItemStack(Items.WHEAT))
				&& !MobAttitude.isWeapon(new ItemStack(Items.STONE))
				&& !MobAttitude.isWeapon(ItemStack.EMPTY), "小麦 / 石头 / 空手 → 不恐惧");
	}

	// ------------------------------------------------------------ ③ 态度判定（纯函数，不需玩家）

	static void checkAttitudes(SelfTest.Context ctx) {
		Level level = ctx.level;
		Mob cow = spawn(level, "minecraft:cow");
		Mob pig = spawn(level, "minecraft:pig");
		Mob zombie = spawn(level, "minecraft:zombie");

		if (cow == null || pig == null || zombie == null) {
			check("态度判定·测试实体构造", false, "cow/pig/zombie 构造失败（注册表缺项？）");
			return;
		}

		try {
			check("态度·空手=敌意", MobAttitude.attitudeOf(cow, ItemStack.EMPTY) == Attitude.HOSTILE, "牛也敢顶空手玩家");
			check("态度·武器=恐惧压过一切", MobAttitude.attitudeOf(cow, new ItemStack(Items.IRON_SWORD)) == Attitude.FEAR,
					"牛见了剑掉头跑");
			check("态度·本物种美食=诱惑", MobAttitude.attitudeOf(cow, new ItemStack(Items.WHEAT)) == Attitude.TEMPT,
					"牛之于小麦");
			check("态度·别人的美食=照样敌意", MobAttitude.attitudeOf(pig, new ItemStack(Items.WHEAT)) == Attitude.HOSTILE
					&& MobAttitude.attitudeOf(pig, new ItemStack(Items.CARROT)) == Attitude.TEMPT,
					"小麦对猪是空手，胡萝卜才是猪的美食");
			check("态度·敌对生物无美食豁免", MobAttitude.attitudeOf(zombie, new ItemStack(Items.WHEAT)) == Attitude.HOSTILE,
					"僵尸不是 Animal，拿小麦喂不停它");
		} finally {
			cow.discard();
			pig.discard();
			zombie.discard();
		}
	}

	// ------------------------------------------------------------ ④ Goal 注入

	static void checkGoalInjection(SelfTest.Context ctx) {
		Level level = ctx.level;
		Mob cow = spawn(level, "minecraft:cow");
		if (cow == null) {
			check("Goal 注入·测试实体构造", false, "cow 构造失败");
			return;
		}

		try {
			boolean hasFear = false;
			boolean hasMelee = false;
			boolean hasTempt = false;

			for (var wrapped : cow.getGoalSelector().getAvailableGoals()) {
				Goal goal = wrapped.getGoal();
				if (goal instanceof AvoidEntityGoal<?>) {
					hasFear = true;
				}
				if (goal instanceof MeleeAttackGoal) {
					hasMelee = true;
				}
				if (goal instanceof TemptGoal) {
					hasTempt = true;
				}
			}

			check("Goal 注入·恐惧逃跑挂上", hasFear, "AvoidEntityGoal 在 goalSelector");
			check("Goal 注入·变脸近战挂上", hasMelee, "MeleeAttackGoal 子类在 goalSelector");
			check("Goal 注入·美食诱惑挂上", hasTempt, "TemptGoal 在 goalSelector");

			// targetSelector 没有 public getter，反射取一次（自检专用，链路查证性质）
			Boolean hasTargetGoal = null;
			try {
				Field field = Mob.class.getDeclaredField("targetSelector");
				field.setAccessible(true);
				GoalSelector targetSelector = (GoalSelector) field.get(cow);
				hasTargetGoal = targetSelector.getAvailableGoals().stream()
						.anyMatch(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals("AttitudeTargetGoal"));
			} catch (ReflectiveOperationException e) {
				check("Goal 注入·索敌挂上（反射）", false, "反射失败：" + e);
			}
			if (hasTargetGoal != null) {
				check("Goal 注入·变脸索敌挂上", hasTargetGoal, "AttitudeTargetGoal 在 targetSelector");
			}
		} finally {
			cow.discard();
		}
	}

	// ------------------------------------------------------------ ⑤ Boss 豁免

	static void checkExclusions(SelfTest.Context ctx) {
		FacesConfig config = FacesConfig.get();

		check("豁免·名单匹配", config.isMobExcluded(Identifier.parse("minecraft:wither"))
				&& config.isMobExcluded(Identifier.parse("minecraft:warden")), "凋灵 / 监守者默认豁免");
		check("豁免·名单外不误伤", !config.isMobExcluded(Identifier.parse("minecraft:cow")), "牛不豁免");
		check("豁免·通配命名空间不误命中", !config.isMobExcluded(Identifier.parse("somemod:wither_boss"))
				&& !config.isMobExcluded(Identifier.parse("somemod:whatever")), "默认名单没有 somemod:*，别把 mod 生物一起豁免");

		Mob wither = spawn(ctx.level, "minecraft:wither");
		if (wither == null) {
			check("豁免·shouldInject", false, "wither 构造失败");
			return;
		}
		try {
			check("豁免·Boss 不注入 Goal", !MobAttitude.shouldInject(wither), "凋灵构造出来也不会被挂变脸 Goal");
		} finally {
			wither.discard();
		}
	}

	// ------------------------------------------------------------ ⑥ 非动物防崩（tempt_range 属性守卫）

	/**
	 * v1.0.0 崩服事故的回归自检：26.2 的 {@code TemptGoal.canUse} 逐刻读
	 * {@code minecraft:tempt_range} 属性，鱿鱼 / 发光鱿鱼这类非动物 PathfinderMob 没有它，
	 * 构造期挂上 TemptGoal 后第一个 AI 刻就崩服。守卫后：非动物不再注册诱惑 Goal，
	 * 但恐惧 / 敌意照常参与。
	 */
	static void checkTemptGuard(SelfTest.Context ctx) {
		Level level = ctx.level;
		Mob glowSquid = spawn(level, "minecraft:glow_squid");
		if (glowSquid == null) {
			check("防崩·测试实体构造", false, "glow_squid 构造失败");
			return;
		}

		try {
			boolean hasTempt = false;
			for (var wrapped : glowSquid.getGoalSelector().getAvailableGoals()) {
				if (wrapped.getGoal() instanceof TemptGoal) {
					hasTempt = true;
				}
			}

			check("防崩·发光鱿鱼不挂诱惑 Goal", !hasTempt,
					"没有 tempt_range 属性的生物绝不注册 TemptGoal（v1.0.0 崩服回归项）");
			check("防崩·非动物仍参与变脸", MobAttitude.shouldInject(glowSquid),
					"鱿鱼不在豁免名单，恐惧 / 敌意照常生效");
		} finally {
			glowSquid.discard();
		}
	}

	// ------------------------------------------------------------ ⑦ 末影人特殊（拿武器强制冷静）

	/**
	 * 末影人特殊规则自检：能测的是「链路在不在」——末影人参与变脸（不在豁免名单、
	 * 是 PathfinderMob）、特殊开关默认开着。真实行为（凝视愤怒被压住 / 掏剑脱战）要进游戏
	 * 目视确认 —— setTarget 拦截发生在运行期有玩家的场景，自检服务器上没有玩家。
	 */
	static void checkEndermanSpecial(SelfTest.Context ctx) {
		FacesConfig config = FacesConfig.get();

		check("末影人·特殊开关默认开", config.endermanWeaponCalm, "endermanWeaponCalm 缺项应补回 true");

		Mob enderman = spawn(ctx.level, "minecraft:enderman");
		if (enderman == null) {
			check("末影人·测试实体构造", false, "enderman 构造失败");
			return;
		}

		try {
			check("末影人·参与变脸", MobAttitude.shouldInject(enderman),
					"末影人不在豁免名单，敌意 / 恐惧 Goal 照常注入（没看眼睛也愤怒由它覆盖）");
			check("末影人·是 PathfinderMob", enderman instanceof net.minecraft.world.entity.PathfinderMob,
					"末影人走普通 Goal 体系（Boss 级不走体系的才天然排除）");
		} finally {
			enderman.discard();
		}
	}

	// ------------------------------------------------------------ 工具

	/** 构造一只测试生物（只 create 不进世界；构造器里变脸注入已经跑完）。 */
	private static Mob spawn(Level level, String id) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
		if (type == null) {
			return null;
		}
		return (Mob) type.create(level, EntitySpawnReason.COMMAND);
	}
}
