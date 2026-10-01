package com.yunxigames.mixin;

import com.yunxigames.MobAttitude;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 史莱姆家族（史莱姆 / 岩浆怪 / 硫磺立方怪）的变脸规则：玩家拿着武器或本生物美食时
 * <b>碰上去不掉血</b>。
 *
 * <p><b>为什么要单独拦 playerTouch</b>：立方怪的伤害不走目标系统 —— 是纯粹的
 * 碰撞结算（{@code playerTouch} → {@code dealDamage}），玩家撞上去就挨打，
 * 26.2 里它们虽是 PathfinderMob、也注入了变脸 Goal，但「逃跑中蹭到玩家」这一下
 * 不经过任何 Goal，setTarget 拦截管不到。在 {@code playerTouch} HEAD 按冷静规则
 * 取消，拿剑穿过一群逃跑的岩浆怪就不再边跑边被弹。
 *
 * <p>反方向不用做：空手 / 拿别的东西时碰撞照常（{@code HOSTILE} 态不取消），
 * 与原版行为一致。26.2 这一家搬进了 {@code cubemob} 包且共父类，拦父类一处全覆盖。
 */
@Mixin(AbstractCubeMob.class)
public abstract class AbstractCubeMobMixin {

	@Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
	private void ygfaces$noTouchDamageWhenNotHostile(Player player, CallbackInfo ci) {
		if (MobAttitude.shouldDropTarget((AbstractCubeMob) (Object) this, player)) {
			ci.cancel();
		}
	}
}
