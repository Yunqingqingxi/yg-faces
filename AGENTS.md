# AGENTS.md — 变脸（yg-faces）开发规范

> 本包是 yunxigames 系列的玩法包之一。系列总览、公共约定与全系列踩坑速查见
> [yunxigames 文档仓库](https://github.com/Yunqingqingxi/yunxigames) 的 AGENTS.md（必读）。
> 本文件是本仓库开发者（人类与 AI）的入口，开工前通读。

## 1. 本包是什么

**变脸**：生物对玩家的态度由玩家主手**实时**决定——
- 拿战斗用品（剑 / 斧 / 矛 / 三叉戟 / 重锤 / 弓 / 弩，标签 `#swords` `#axes` `#spears` + 散件）
  → 全场生物掉头就跑（含僵尸苦力怕）；
- 拿某生物的美食（`Animal#isFood`）→ 该生物不攻击还被诱惑跟着走（逐物种豁免，敌对生物无豁免）；
- 其他任何东西 / 空手 → 所有生物尝试攻击玩家（友好生物也装上攻击能力，伤害走配置不依赖原版属性）。

豁免名单默认凋灵 / 监守者（Boss 不走普通 Goal 体系，末影龙天然不是 Mob），支持 `命名空间:*` 通配；
**驯服宠物不豁免**（狼也会翻脸咬主人，玩法定版口径）。

- mod id：`yg_faces`，jar：`yg-faces-<版本>.jar`，配置：`config/yg-faces.json`，入口 `YunxiGamesFaces`

### 类地图

| 类 | 职责 |
| --- | --- |
| `FacesConfig` | 本包全部配置项 + `validate()` 钳制 |
| `MobAttitude` | **态度判定核心**（唯一真相源）：武器索引展开 + `attitudeOf` 纯函数（FEAR > TEMPT > HOSTILE）+ `shouldInject` |
| `AttitudeGoals` | 三个 Goal + 注入逻辑（AvoidEntity / AttitudeTargetGoal / AttitudeAttackGoal / TemptGoal 守卫） |
| `mixin/MobAttitudeMixin` | 挂 `Mob` **构造器 TAIL** 调 `AttitudeGoals.inject` |
| `FacesSelfTest` | 本包自检 ①~⑥ |

### 三条设计底线 / 向后兼容承诺

1. 只在服务端做判定（态度是主手物品的纯函数，零网络包）；2. 一局制、零持久化（唯一全局状态
是武器索引，配置加载 / 首次使用时展开）；3. 物品不凭空消失。
mod id / jar 名 / 配置文件名 / lang key 永不改；配置字段只增不删；删字段 / 改默认行为升 major；
语义化版本 + GitHub Release 附 jar。

## 2. 环境（硬性）

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| **JDK** | **25**（本机 `D:\Java\jdk-25`，runServer/build 必须显式指定） |

一切 gradle 命令加 `--offline`。

## 3. 常用命令

```bash
./gradlew compileJava --offline            # 开发期每个功能写完就跑
./gradlew test --offline                   # 三层 JUnit 测试
./gradlew smokeTest --offline              # 只跑冒烟
JAVA_HOME='D:\Java\jdk-25' ./gradlew runServer --offline > selftest-<版本>.log 2>&1
JAVA_HOME='D:\Java\jdk-25' ./gradlew build --offline
```

- runServer 工作目录是本仓库自己的 `run/`（首次跑改 `run/eula.txt` 为 `eula=true`）；
- 自检前把 `run/config/yg-faces.json` 的 `selfTestRolls` 改成 `200`，跑完**改回 `0`**；
- 自检完 runServer 不自退，手动结束 java 进程，否则 `run/` 被锁。

## 4. 代码规范

1. 一个功能一个类，类头 javadoc 写「是什么 + 为什么」；
2. 一切数值进本包 `FacesConfig`，带中文注释，每个功能独立开关（「爽但不劝退」）；
3. 新配置项必须在 `validate()` 钳制：`!(x >= lo && x <= hi)` 顺带治 NaN；
4. 中文注释 / 文案 / lang 键值；
5. 26.2 API 不确定：**先查反混淆 jar，别猜**。

## 5. 测试节奏

- 三层 JUnit（Smoke / Unit / Regression）+ runServer 自检；批量开发期只跑 `compileJava`；
- **新增功能必须同步新增自检项**并更新本包 README 的自检表；
- 配置测试基建：`YgConfig.configDirOverride`、`mergeMissingFields`（`raw.has` 判缺项补回，
  validate 对 null 列表要补**默认内容**而非空表——v1.0.0 测试抓过这个 bug）、`orDefaultIfNaN`；
  构造器与 `validate()` 包内可见是测试前提，别改回 private；
- 自检实体只 `create` 不 `addFreshEntity`（构造器里注入已跑完，构造完即可断言）。

## 6. 本包专属坑（全系列公共坑见系列仓库 AGENTS §7，本包贡献了其中两条）

- **Goal 注入点必须挂 `Mob` 构造器 TAIL**：`registerGoals` 会被大量生物覆写且不调 super
  （虚调用短路），注入基类方法整类漏掉；`Mob` 只有一个构造器，TAIL 必然执行且原版目标已注册完；
- **`TemptGoal` 的 `tempt_range` 属性崩服**（v1.0.1 事故）：26.2 的 `TemptGoal.canUse` 逐刻读
  `Attributes.TEMPT_RANGE`，只有带诱惑 AI 的动物有它——鱿鱼 / 蝙蝠 / 铁傀儡等非动物
  PathfinderMob 没有属性表项，直接挂第一个 AI 刻就 `IllegalArgumentException`。
  注册前必须守卫 `pathfinder.getAttribute(Attributes.TEMPT_RANGE) != null`（自检 ⑥ 回归项盖住）；
- **武器索引不能在入口期展开**：物品标签还没 bind，`getTagOrEmpty` 会抛 IllegalStateException
  —— 配置 load 后不调 `reindex`，首次 `isWeapon` 时惰性展开（`MobAttitude` 注释有说明）；
- `AvoidEntityGoal` 的谓词经构造器接进 `TargetingConditions`，只对满足谓词的目标生效
  （恐惧判定只对「拿武器的玩家」）；
- 变脸近战**不读原版 ATTACK_DAMAGE 属性**（牛羊没有），覆写 `checkAndPerformAttack`
  按配置伤害直接 `hurtServer` 结算——硬补属性要动 DefaultAttributes 全局表，别做；
- 美食判定复用 `Animal#isFood`（mod 动物自动兼容），敌对生物不是 Animal 天然无豁免。
