package com.yunxigames.mixin;

import com.yunxigames.AttitudeGoals;
import com.yunxigames.MobAttitude;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 变脸在 {@link Mob} 基类上的两件事：<b>Goal 注入</b>与<b>通用仇恨切断</b>。
 *
 * <h3>① Goal 注入（构造器 TAIL）</h3>
 *
 * <p><b>为什么挂在构造器而不是 {@code registerGoals}</b>：僵尸等大量生物覆写了
 * {@code registerGoals} 且不调 {@code super}（基类方法是空的、被虚调用短路），
 * 注入基类方法会整类漏掉；构造器只有一个、必然执行、且此时原版目标已全部注册完，
 * 在 TAIL 追加不会和任何子类抢时序。
 *
 * <p><b>为什么客户端也会执行</b>：本 mixin 不区分环境 —— 客户端构造的生物实例同样
 * 挂 Goal，但 AI 只在服务端 tick，多挂的 Goal 永远不会跑，与系列「只在服务端做判定」
 * 的底线不冲突（不需要为此区分环境源集）。
 *
 * <h3>② 通用仇恨切断（v1.2.1 修复「拿武器仍被攻击」）</h3>
 *
 * <p><b>为什么逃跑 Goal 压不住攻击</b>：原版敌对生物自带的索敌 Goal 不认识变脸态度，
 * 每刻照样把拿剑玩家设为目标；AvoidEntityGoal 只占 MOVE 旗标，攻击途径另有其路
 * （索敌/记仇 anger 系统、距离边缘抖动、恶魂喷火球根本不占 MOVE）。而原版所有愤怒
 * 来源最终都汇聚到 {@code setTarget(LivingEntity)} —— 在这个汇聚点 HEAD 拦
 * 「目标是非敌意态度玩家」的调用，普通索敌 / HurtByTarget 记仇 / anger 系统 /
 * 末影人凝视一次全断，恶魂、幻翼这类不注入 Goal 的非 PathfinderMob 也一并覆盖。
 * （原先的 EnderManMixin 专属拦截被本规则取代而删除。）
 *
 * <p><b>掏剑当刻脱战</b>：战斗中已有目标不会自己消失（索敌 Goal 不会因为「目标拿剑」
 * 再调一次 setTarget），{@code customServerAiStep} TAIL 补一刀把非敌意态度的玩家
 * 目标立即清掉 —— 所有生物（不只末影人）「亮剑即脱战」。{@code setTarget(null)}
 * 不会被 HEAD 拦截（目标是 null 不是玩家），清仇恨不会递归。
 *
 * <p>豁免 Boss（凋灵 / 监守者）与总开关判断都在 {@link MobAttitude#shouldDropTarget}。
 */
@Mixin(Mob.class)
public abstract class MobAttitudeMixin {
	@Shadow
	@Final
	protected GoalSelector goalSelector;

	@Shadow
	@Final
	protected GoalSelector targetSelector;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void ygfaces$injectAttitudeGoals(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (MobAttitude.shouldInject(self)) {
			AttitudeGoals.inject(self, this.goalSelector, this.targetSelector);
		}
	}

	/**
	 * 拦截「把仇恨设到非敌意态度玩家」的全部路径：原版索敌 / 记仇 / anger 系统 /
	 * 末影人凝视在这里汇聚，一刀全断。
	 */
	@Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
	private void ygfaces$calmWhenNotHostile(LivingEntity target, CallbackInfo ci) {
		if (MobAttitude.shouldDropTarget((Mob) (Object) this, target)) {
			ci.cancel();
		}
	}

	/**
	 * 战斗中玩家掏出武器（或掏出这只生物的美食）：当刻脱战。
	 * 美食也断仇 —— 玩家拿小麦靠近刚打过的牛，牛不该记着仇顶着角撞拿小麦的人。
	 */
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void ygfaces$dropCalmTarget(ServerLevel level, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (self.getTarget() != null && MobAttitude.shouldDropTarget(self, self.getTarget())) {
			self.setTarget(null);
		}
	}
}
