package com.yunxigames;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * 变脸玩法的三个 Goal 与注入逻辑。
 *
 * <p><b>为什么用三个普通 Goal 而不是自建状态机</b>：原版 Goal 选择器本来就支持「条件成立就上、
 * 不成立就让位」，把三种态度各做成一个 Goal、在 canUse / canContinueToUse 里逐刻读玩家主手，
 * 态度切换就由原版 Goal 选择器自动完成 —— 零自定义状态、零网络包、生物重启后行为自动复原。
 *
 * <p>优先级设计（数字越小越优先，全部压过原版同类）：
 * <ol>
 *   <li>{@code 0} 恐惧逃跑（只占 MOVE 旗标，与原版 FloatGoal 的 JUMP 旗标不冲突，
 *       且能压过苦力怕的点燃（1）与僵尸的近战（2）—— 见你拿剑，点燃都点不起来）；</li>
 *   <li>{@code 3} 变脸近战（与原版生物自身目标并列，只在「敌意」态度时追打）；</li>
 *   <li>{@code 4} 美食诱惑（最低，敌意/恐惧在场时永远让位）。</li>
 * </ol>
 */
public final class AttitudeGoals {
	private AttitudeGoals() {
	}

	/** 向一只生物注入变脸三件套（调用方已用 {@link MobAttitude#shouldInject} 过滤）。 */
	public static void inject(Mob mob, GoalSelector goalSelector, GoalSelector targetSelector) {
		if (!(mob instanceof PathfinderMob pathfinder)) {
			return;
		}

		FacesConfig config = FacesConfig.get();

		// ① 恐惧：附近（weaponFleeDistance 内）任何主手拿战斗用品的玩家都触发逃跑。
		//    谓词经原版 AvoidEntityGoal 构造器接进 TargetingConditions，只对「拿武器的人」生效。
		goalSelector.addGoal(0, new AvoidEntityGoal<>(
				pathfinder, Player.class,
				player -> MobAttitude.attitudeOf(mob, player.getMainHandItem()) == MobAttitude.Attitude.FEAR,
				(float) config.weaponFleeDistance,
				config.fleeSpeed, config.fleeSpeed,
				player -> true));

		// ② 敌意：把「拿武器 / 拿本生物美食」的玩家从索敌候选里剔掉（选择器在 TargetGoal 层生效，
		//    剔除后该 Goal 自然找不到目标，攻击与恐惧/诱惑互不打架）。
		targetSelector.addGoal(2, new AttitudeTargetGoal(mob));

		// ③ 变脸近战：不依赖原版 ATTACK_DAMAGE 属性（牛羊的属性表里根本没有它），伤害走配置。
		goalSelector.addGoal(3, new AttitudeAttackGoal(pathfinder, config.attackDamage));

		// ④ 诱惑：拿着本生物的美食时被牵着走（canScare=false：玩家疾跑也不吓跑它）。
		//    26.2 的 TemptGoal.canUse 逐刻读 minecraft:tempt_range 属性，而该属性只有带
		//    诱惑 AI 的动物（牛羊猪鸡……）才有——鱿鱼 / 蝙蝠 / 铁傀儡这类非动物 PathfinderMob
		//    的属性表里没有，直接挂上去就会 IllegalArgumentException 崩服（v1.0.0 实际事故）。
		//    美食判定本来只对 Animal 生效，这里再挡一道「没有属性就不注册」双保险。
		if (pathfinder.getAttribute(Attributes.TEMPT_RANGE) != null) {
			goalSelector.addGoal(4, new TemptGoal(pathfinder, config.temptSpeed,
					stack -> MobAttitude.isFavoriteFood(mob, stack), false));
		}
	}

	/**
	 * 变脸索敌：目标候选必须「对该生物是敌意态度」（没拿武器、也没拿本生物的美食）。
	 * canContinueToUse 里再验一次态度 —— 玩家战斗中把剑切出去/切回来，仇恨当刻解除/重建。
	 */
	private static final class AttitudeTargetGoal extends NearestAttackableTargetGoal<Player> {
		private final Mob mob;

		AttitudeTargetGoal(Mob mob) {
			super(mob, Player.class, true,
					(candidate, level) -> MobAttitude.attitudeOf(mob, candidate.getMainHandItem())
							== MobAttitude.Attitude.HOSTILE);
			this.mob = mob;
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.mob.getTarget();
			if (target != null
					&& MobAttitude.attitudeOf(this.mob, target.getMainHandItem()) != MobAttitude.Attitude.HOSTILE) {
				return false;
			}
			return super.canContinueToUse();
		}
	}

	/**
	 * 变脸近战：追上玩家后按配置伤害直接结算。
	 * 为什么覆写 {@code checkAndPerformAttack} 而不直接用原版：原版走 {@code doHurtTarget}
	 * 读 ATTACK_DAMAGE 属性，友好生物没有这个属性（硬补要动 DefaultAttributes 全局属性表）；
	 * 自己结算伤害可控、可配、零副作用。攻击距离公式照抄原版 MeleeAttackGoal 的
	 * {@code getAttackReachSqr}（26.2 里已内联，这里按同式复刻）。
	 */
	private static final class AttitudeAttackGoal extends MeleeAttackGoal {
		private final double damage;

		AttitudeAttackGoal(PathfinderMob mob, double damage) {
			super(mob, 1.2, true);
			this.damage = damage;
		}

		@Override
		protected void checkAndPerformAttack(LivingEntity target) {
			float reach = this.mob.getBbWidth() * 2.0F;
			double attackReachSqr = (double) (reach * reach) + target.getBbWidth();
			if (this.isTimeToAttack() && this.mob.distanceToSqr(target) <= attackReachSqr) {
				this.resetAttackCooldown();
				if (this.mob.level() instanceof ServerLevel level) {
					target.hurtServer(level, this.mob.damageSources().mobAttack(this.mob), (float) this.damage);
				}
			}
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.mob.getTarget();
			if (target != null
					&& MobAttitude.attitudeOf(this.mob, target.getMainHandItem()) != MobAttitude.Attitude.HOSTILE) {
				return false;
			}
			return super.canContinueToUse();
		}
	}
}
