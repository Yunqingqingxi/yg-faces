package com.yunxigames;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 变脸包入口（yg_faces）：生物对玩家的态度由玩家主手实时决定。
 *
 * <p>本包自带 yg-core 基础库与自己的配置（{@code config/yg-faces.json}），可独立安装。
 * 玩法零持久化 —— 态度是「主手物品的纯函数」，没有任何存储态；唯一的全局状态是
 * 武器索引（配置加载时展开成 Set&lt;Item&gt;），配置重载时重建。
 */
public class YunxiGamesFaces implements ModInitializer {
	public static final String MOD_ID = "yg_faces";
	public static final String LOGGER_NAME = "yg-faces";

	@Override
	public void onInitialize() {
		FacesConfig.load();
		// 注意：武器索引（MobAttitude.reindex）不能在这里展开 —— 入口期物品标签还没 bind，
		// getTagOrEmpty 会抛 IllegalStateException。首次 isWeapon 时惰性展开（见 MobAttitude）。

		// 自检（编号包内局部）：
		// ① 配置钳制 ② 武器判定 ③ 态度判定（美食逐物种） ④ Goal 注入 ⑤ Boss 豁免
		// ⑥ 非动物防崩（tempt_range 属性守卫） ⑦ 通用冷静规则（setTarget 切断仇恨）
		SelfTest.registerStep("① 变脸·配置钳制", FacesSelfTest::checkConfigClamps);
		SelfTest.registerStep("② 变脸·武器判定", FacesSelfTest::checkWeaponDetection);
		SelfTest.registerStep("③ 变脸·态度判定", FacesSelfTest::checkAttitudes);
		SelfTest.registerStep("④ 变脸·Goal 注入", FacesSelfTest::checkGoalInjection);
		SelfTest.registerStep("⑤ 变脸·Boss 豁免", FacesSelfTest::checkExclusions);
		SelfTest.registerStep("⑥ 变脸·非动物防崩", FacesSelfTest::checkTemptGuard);
		SelfTest.registerStep("⑦ 变脸·通用冷静", FacesSelfTest::checkUniversalCalm);
		SelfTest.register(() -> FacesConfig.get().selfTestRolls);

		LoggerFactory.getLogger(LOGGER_NAME).info("[yg-faces] 变脸已加载：拿武器全场跑，拿美食喂动物，空手被围殴");
	}
}
