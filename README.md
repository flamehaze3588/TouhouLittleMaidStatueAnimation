# 车万女仆：雕像动画（Touhou Little Maid: Statue Animation）

车万女仆（TouhouLittleMaid）× Yes Steve Model 共同附属 mod。

准星对着女仆雕像/手办按快捷键，即可打开该雕像所用 YSM 模型的动作轮盘，让雕像播放动作。安装酒狐更多动画（moreanimation）后，还可用其动作控制终端右键雕像，控制表情与动作，TLM 皮肤包雕像同样生效。

## 功能特性

- **雕像动作轮盘**：对准雕像/手办按下快捷键（需在 设置→控制 中绑定，默认未绑定），弹出该雕像当前模型专属的动画轮盘，风格与 YSM 2.x 原版轮盘一致
  - 支持动画分类二级子菜单（`#` 前缀入口）、翻页浏览与停止播放
  - 支持 zip 与 `.ysm` 加密单文件模型，自动解密并解析其中的 extraAnimations
- **多人同步与持久化**：播放状态写入雕像 NBT，存档保存、多人可见；音乐类动画的音源正确定位在雕像处
- **moreanimation 软联动（可选）**：手持酒狐更多动画的"动作控制终端"（expression_item）右键雕像/手办，即可控制其表情、动作与形态
  - YSM 模型雕像与 TLM 原版皮肤包（bedrock 模型）雕像均可生效
  - 未安装 moreanimation 时不影响任何功能
- **兼容性零侵入**：不修改 TLM/YSM 本体文件，无 moreanimation 状态的非 YSM 雕像保持原有定格渲染行为

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47+ |
| [车万女仆 Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) | 必需（1.5.x） |
| [Yes Steve Model](https://github.com/YesSteveModel/YSM) | 必需（2.x） |
| 酒狐更多动画 moreanimation | 可选（联动功能） |

## 构建

```bash
gradlew.bat build
```

产物位于 `build/libs/`，安装时请使用带 `-all` 后缀的 jar（内含解析 `.ysm` 加密模型所需的 zstd 解压库）。

## 开源许可

[MIT License](LICENSE)
