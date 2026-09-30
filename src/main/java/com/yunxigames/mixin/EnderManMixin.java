package com.yunxigames.mixin;

import com.yunxigames.FacesConfig;
import com.yunxigames.MobAttitude;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影人特殊规则（变脸）：玩家主手拿战斗用品时<b>强制末影人冷静</b>。
 *
 * <p><b>为什么只拦 {@code setTarget} 一条路</b>：26.2 末影人的愤怒来源有三条——
 * 凝视（{@code EndermanLookForPlayerGoal} 的 {@code startAggroTargetConditions} 里
 * {@code isBeingStaredBy}）、记仇（persistent anger / {@code UniversalAngerGoal}）、
 * 以及变脸玩法的{@code AttitudeTargetGoal}——三条路最终都汇聚到 {@code setTarget(LivingEntity)}。
 * 在这个汇聚点 HEAD 拦截「目标是拿武器玩家」的调用，等于一刀切掉全部愤怒来源，不用逐条堵。
 *
 * <p><b>愤怒中掏出武器</b>：已有目标不会自己消失，{@code customServerAiStep} TAIL 再补一刀
 * 把拿武器玩家的仇恨立即清掉——「亮剑即脱战」当刻生效。
 *
 * <p><b>反方向不用做</b>：「不拿武器时没看眼睛也愤怒」由变脸通用的敌意索敌
 * （{@code AttitudeTargetGoal}，不要求凝视）天然覆盖。原版凝视愤怒在「不拿武器」时保持原样，
 * 与玩法不冲突。记仇不主动清除——玩家收起剑的瞬间末影人立刻翻脸，符合玩法气质。
 */
@Mixin(EnderMan.class)
public abstract class EnderManMixin {

	/**
	 * 拦截「把仇恨设到拿武器玩家」的全部路径：凝视愤怒 / 记仇愤怒 / 原版索敌在这里汇聚。
	 */
	@Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
	private void yg$faces$calmWhenWeaponHeld(LivingEntity target, CallbackInfo ci) {
		if (!(target instanceof Player player)) {
			return;
		}
		FacesConfig config = FacesConfig.get();
		if (!config.facesEnabled || !config.endermanWeaponCalm) {
			return;
		}
		if (MobAttitude.isWeapon(player.getMainHandItem())) {
			ci.cancel();
		}
	}

	/**
	 * 愤怒中玩家掏出武器：当刻脱战（setTarget(null) 不会被上面的 HEAD 拦截——目标是 null 不是玩家）。
	 */
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void yg$faces$dropWeaponTarget(ServerLevel level, CallbackInfo ci) {
		if (((Object) this) instanceof EnderMan self
				&& self.getTarget() instanceof Player player) {
			FacesConfig config = FacesConfig.get();
			if (config.facesEnabled && config.endermanWeaponCalm
					&& MobAttitude.isWeapon(player.getMainHandItem())) {
				self.setTarget(null);
			}
		}
	}
}
