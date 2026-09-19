# 车万女仆：雕像动画（Touhou Little Maid: Statue Animation）

车万女仆（TouhouLittleMaid）× Yes Steve Model 共同附属 mod。

准星对着女仆雕像/手办按快捷键，即可打开该雕像所用 YSM 模型的动作轮盘，让雕像播放动作。安装酒狐更多动画（moreanimation）后，还可用其动作控制终端右键雕像，控制表情与动作，TLM 皮肤包雕像同样生效。

## 功能特性

- **雕像动作轮盘**：对准雕像/手办按下快捷键（需在 设置→控制 中绑定，默认未绑定），弹出该雕像当前模型专属的动画轮盘
- **moreanimation 联动**：手持酒狐更多动画的"动作控制终端"（expression_item）右键雕像/手办，即可控制其表情、动作与形态
  - **蹲下 + 右键**（终端或原版木棍均可）：不打开界面，直接切换雕像的站姿/坐姿；坐姿会随机切换原版坐姿或 moreanimation 新增坐姿

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
