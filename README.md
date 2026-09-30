# yg-faces 变脸（yunxigames faces 包）

> 生物对玩家的态度，由玩家主手实时决定 —— 拿武器全场掉头就跑，拿美食喂动物，空手被围殴。

Minecraft **26.2** / Fabric Loader **0.19.5** / Fabric API **0.159.0+26.2** / Java 25。
独立玩法包，自包含、零跨包依赖，可与系列其他包任意组合；**只在服务端做判定**，原版客户端直连。

## 玩法

每只生物逐刻观察附近玩家的主手物品，三种态度即时切换：

| 玩家主手 | 生物态度 |
| --- | --- |
| 战斗用品（剑 / 斧 / 矛 / 三叉戟 / 重锤 / 弓 / 弩） | **恐惧**：掉头就跑（全场生物，含僵尸苦力怕） |
| 该生物的美食（牛之于小麦、猪之于胡萝卜……） | **诱惑**：不攻击，被牵着走（只有对应物种安全） |
| 其他任何东西 / 空手 | **敌意**：尝试攻击玩家（**友好生物也装上了攻击能力**，默认伤害 1 颗心） |

- **美食豁免是逐物种的**：拿小麦是「对牛的护身符」，旁边的猪照咬；敌对生物（僵尸等）不是 Animal，永远没有美食豁免；
- **武器永远是最强信号**：恐惧 > 诱惑 > 敌意，战斗中切武器/切美食当刻生效；
- 多玩家各自独立判定：A 拿剑 B 空手，牛见了 A 跑、见了 B 咬；
- **驯服的宠物不豁免**——狼也会翻脸咬主人（云兮定的口径）。

## 技术实现（为什么这么做）

- 注入点挂在 `Mob` **构造器尾部**而不是 `registerGoals`：僵尸等大量生物覆写后者且不调 super，挂在方法上会整类漏掉；
- 三个普通 Goal（恐惧逃跑 priority 0 / 索敌+近战 2·3 / 诱惑 4），canUse/canContinueToUse 逐刻读主手 —— 态度是「主手物品的纯函数」，零持久化、零网络包、零存储态；
- 恐惧判定走原版 `AvoidEntityGoal` 的 TargetingConditions 谓词，只对「拿武器的人」生效；priority 0 只占 MOVE 旗标，能压过苦力怕的点燃（1）与僵尸近战（2）—— 见你拿剑，点燃都点不起来；
- 友好生物的攻击**不依赖原版 ATTACK_DAMAGE 属性**（牛羊属性表里没有它，硬补要动全局属性表），伤害由本包配置直接结算；
- 武器判定：启动时把 `#minecraft:swords` / `#minecraft:axes` / `#minecraft:spears` 标签族（矛 = 26.2 新增长矛，六种材质一个标签全覆盖）+ 三叉戟/重锤/弓/弩散件展开成 `Set<Item>` 哈希索引，第三方 mod 往这些标签加武器自动生效；
- 美食判定直接复用原版 `Animal#isFood`，mod 动物自动兼容；
- 非寻路生物（幻翼、恶魂等）不参与（有寻路才能追人/逃跑）。

## 配置 `config/yg-faces.json`

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `facesEnabled` | `true` | 总开关（关闭后新生物不再注入 Goal） |
| `attackDamage` | `2.0` | 友好生物攻击玩家的伤害（2.0 = 1 颗心；钳制 0.5~20） |
| `weaponFleeDistance` | `12.0` | 武器恐惧距离（格；钳制 4~64） |
| `fleeSpeed` | `1.4` | 逃跑速度倍率（钳制 0.5~3） |
| `temptSpeed` | `1.1` | 美食诱惑跟随速度倍率（钳制 0.5~3） |
| `weaponTags` | `swords`/`axes`/`spears` | 武器判定·物品标签族 |
| `weaponItems` | 三叉戟/重锤/弓/弩 | 武器判定·散件 id |
| `excludedMobs` | 凋灵/监守者 | 豁免名单（支持 `命名空间:*` 通配，第三方 Boss 也在这里挡） |
| `debugLog` | `false` | 调试日志 |
| `selfTestRolls` | `0` | 开服自检掷骰次数（>0 触发，跑完改回 0） |

名单字段写空数组视为「没配」，validate 会补回默认值（武器清单为空玩法就死了，不做全空）。

## 自检覆盖（①~⑥）

| 编号 | 内容 |
| --- | --- |
| ① | 配置钳制（NaN / 越界落默认、边界值保留） |
| ② | 武器判定（剑斧矛三叉戟重锤弓弩全中、小麦/石头/空手不误伤） |
| ③ | 态度判定（空手敌意、武器恐惧、逐物种美食诱惑、僵尸无豁免） |
| ④ | Goal 注入（恐惧/近战/诱惑/索敌四个 Goal 都挂上） |
| ⑤ | Boss 豁免（名单匹配、通配不误命中、凋灵构造即免注入） |
| ⑥ | 非动物防崩（发光鱿鱼不挂诱惑 Goal——26.2 TemptGoal 读 `tempt_range` 属性，非动物没有，v1.0.1 崩服回归项） |

**自检覆盖不到的部分**：真实战斗中「牛追着顶人」「僵尸见剑就跑」要进游戏目视确认 ——
自检服务器上没有玩家，Goal 的 canUse 不会触发（与 mobs 外观同理）。

## 相关

- 系列总览与公共规范：[yunxigames 文档仓库](https://github.com/Yunqingqingxi/yunxigames)
- 兄弟包：[yg-random-drops](https://github.com/Yunqingqingxi/yg-random-drops) ·
  [yg-more-enchants](https://github.com/Yunqingqingxi/yg-more-enchants) ·
  [yg-world-events](https://github.com/Yunqingqingxi/yg-world-events) ·
  [yg-bingo](https://github.com/Yunqingqingxi/yg-bingo) ·
  [yg-more-mobs](https://github.com/Yunqingqingxi/yg-more-mobs) ·
  [yg-random-swap](https://github.com/Yunqingqingxi/yg-random-swap)

## 更新记录

见 [CHANGELOG.md](CHANGELOG.md)。
