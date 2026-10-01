package com.yunxigames.command;

import com.mojang.brigadier.CommandDispatcher;
import com.yunxigames.FacesConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /yg faces ...}：变脸玩法（生物态度重编程）的游戏内启停与状态。
 *
 * <p>各玩法包统一往 {@code /yg} 根下挂以玩法名命名的子树（Brigadier 会把各包注册的
 * 同名根节点合并成一棵命令树），与 drops 包的 {@code /yg drops} 同一布局。
 *
 * <p>off 后<b>新刷出</b>的生物不再注入变脸 Goal、伤害与仇恨判定也停；已在场上的生物
 * 是构造器时注入的 Goal，热关无法回收，重启世界后恢复原版行为（与配置文件注释一致）。
 */
public final class FacesCommand {
	/** 与 drops 包同一权限档（等价旧「权限等级 2」，OP 可用）。 */
	private static final PermissionCheck PERMISSION = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

	private FacesCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("yg")
				.requires(Commands.hasPermission(PERMISSION))
				.then(Commands.literal("faces")
						.executes(context -> status(context.getSource()))
						.then(Commands.literal("on")
								.executes(context -> toggle(context.getSource(), true)))
						.then(Commands.literal("off")
								.executes(context -> toggle(context.getSource(), false)))));
	}

	private static int toggle(CommandSourceStack source, boolean enabled) {
		FacesConfig config = FacesConfig.get();
		config.facesEnabled = enabled;
		config.save();
		source.sendSuccess(() -> Component.literal("[yg] 变脸玩法：" + (enabled ? "开启" : "关闭")
				+ (enabled ? "" : "（已在场上的生物重启世界后恢复原版行为）")), false);
		return status(source);
	}

	private static int status(CommandSourceStack source) {
		FacesConfig config = FacesConfig.get();
		source.sendSuccess(() -> Component.literal(String.format(
				"[yg] 变脸玩法=%s | 攻击伤害=%.1f 恐惧距离=%.0f格 逃跑速度×%.1f 诱惑速度×%.1f",
				config.facesEnabled ? "开" : "关",
				config.attackDamage,
				config.weaponFleeDistance,
				config.fleeSpeed,
				config.temptSpeed)), false);
		return 1;
	}
}
