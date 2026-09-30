package com.yunxigames.mixin;

import com.yunxigames.AttitudeGoals;
import com.yunxigames.MobAttitude;
import net.minecraft.world.entity.EntityType;
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
 * 变脸注入点：在 {@link Mob} 构造器尾部给每只生物挂上变脸三件套。
 *
 * <p><b>为什么挂在构造器而不是 {@code registerGoals}</b>：僵尸等大量生物覆写了
 * {@code registerGoals} 且不调 {@code super}（基类方法是空的、被虚调用短路），
 * 注入基类方法会整类漏掉；构造器只有一个、必然执行、且此时原版目标已全部注册完，
 * 在 TAIL 追加不会和任何子类抢时序。
 *
 * <p><b>为什么客户端也会执行</b>：本 mixin 不区分环境 —— 客户端构造的生物实例同样
 * 挂 Goal，但 AI 只在服务端 tick，多挂的 Goal 永远不会跑，与系列「只在服务端做判定」
 * 的底线不冲突（不需要为此区分环境源集）。
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
}
