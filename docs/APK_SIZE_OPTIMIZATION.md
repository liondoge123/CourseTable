# APK 体积优化方案

## 目标与边界

本方案用于缩小正式 APK 的下载体积，同时保持现有功能、离线能力、识别精度、PDF 兼容性和支持的 ABI 不变。

必须遵守以下边界：

- 保留图片与扫描 PDF 的离线中文 OCR。
- 保留文字层 PDF 解析，以及当前已经支持的字体、字符集和加密 PDF 行为。
- 保留 `arm64-v8a` 和 `armeabi-v7a`；正式发布仍生成两个单 ABI APK 和 universal APK。
- 不通过降低模型精度、删除模型、改为首次联网下载模型或移除 PDF 能力来换取体积。
- 发布构建、版本号、命名、签名与校验继续遵循 [`RELEASE.md`](../RELEASE.md)。

## 基线

基线取自 v1.6.2 正式构建。后续每次优化都应使用同一套构建脚本和测试样本重新测量，不能拿 Preview 包与 Release 包比较。

| 产物 | 大小 |
| --- | ---: |
| `CourseTable-v1.6.2-arm64-v8a-release.apk` | 21,047,683 B |
| `CourseTable-v1.6.2-armeabi-v7a-release.apk` | 19,704,305 B |
| `CourseTable-v1.6.2-universal-release.apk` | 30,165,906 B |

arm64 APK 的主要压缩后体积如下：

| 内容 | 大小 | APK 占比（约） |
| --- | ---: | ---: |
| ONNX Runtime native 库 | 6.47 MB | 30.7% |
| Tiny OCR ONNX 模型 | 4.12 MB | 19.6% |
| ML Kit native 库 | 3.99 MB | 18.9% |
| DEX | 2.57 MB | 12.2% |
| ML Kit 模型 | 1.92 MB | 9.1% |
| PDFBox 字体与 CMap 等资源 | 1.60 MB | 7.6% |

现有构建已经启用 R8、资源压缩、ABI 拆分和 native 库压缩，普通图片/资源优化的收益很小。优化重点应放在 R8 keep 规则和 ONNX Runtime。

## 实施顺序

### 阶段 0：固定测量与回归基线

在修改依赖或裁剪规则之前：

1. 使用当前版本执行 `scripts/build-apk.ps1 release`，保存三种 APK 的字节数和 SHA-256。
2. 保存 APK Analyzer 或等价脚本导出的 DEX、`lib/`、`assets/` 分类体积。
3. 运行完整单元测试、Lint 和现有 OCR instrumentation/benchmark。
4. 固定一组回归输入，至少覆盖：
   - 带文字层的中文 PDF；
   - 扫描 PDF；
   - 中文与中英混排图片；
   - 旋转、低清晰度和多页输入；
   - 当前版本能够处理的特殊字体、字符集和加密 PDF。
5. 记录每个样本的识别结果、警告、耗时和峰值内存，作为后续验收基线。

每个优化阶段单独提交、单独测量，避免无法判断具体收益或快速回退。

### 阶段 1：收窄 R8 keep 规则

当前 `app/proguard-rules.pro` 对 PDFBox、FontBox、ML Kit 和 ONNX Runtime 使用了包级全量 `-keep`，会阻止大量类、字段和方法被裁剪、优化及混淆。

实施步骤：

1. 打开 `app/build/outputs/mapping/release/configanalyzer.html`，按阻止 shrinking/optimization 数量从高到低检查规则。
2. 先删除能够由依赖自带 consumer rules 覆盖的重复规则；其余规则改为仅保留反射或 JNI 实际需要的类和成员。
3. 每次只调整一个依赖族，顺序建议为 PDFBox/FontBox、ML Kit、ONNX Runtime。
4. 每次调整后构建 Release，并运行阶段 0 的全部测试和样本。
5. 检查 `usage.txt`、`seeds.txt`、映射文件和 APK 内 DEX 大小，确认确实发生裁剪。

预期收益为数百 KB 至约 1 MB。此阶段不设置强制节省值；只有测试完全通过的规则才能保留。

验收条件：

- 所有导入、OCR、PDF 和启动路径无崩溃或反射/JNI 错误。
- OCR 输出与基线一致；若存在非确定性，必须逐项人工确认差异不来自裁剪。
- Release DEX 体积不增加。

### 阶段 2：为 Tiny OCR 构建定制 ONNX Runtime

这是预计收益最大的方案。当前应用只通过 ONNX Runtime 执行 `PP-OCRv6_rec_tiny.onnx`，但依赖的是包含完整算子集合的 `onnxruntime-android`。

实施步骤：

1. 使用与当前依赖一致的 ONNX Runtime release tag，从现有模型生成 reduced-operator 配置。
2. 先构建仅裁剪未使用算子的 Android AAR，保持 ONNX 模型格式和 Java/Kotlin 调用接口不变。
3. 分别产出 `arm64-v8a` 和 `armeabi-v7a` native 库，并确认 AAR 不包含 x86/x86_64。
4. 将 Maven 依赖替换为仓库内固定版本的本地 AAR；同时记录源码 tag、构建命令、配置文件和许可证信息，确保可复现。
5. 在两种 ABI 的真实设备或等价测试环境上运行阶段 0 的全部 OCR 回归。
6. 稳定后再评估是否转换为 ORT 模型并启用 minimal build/type reduction。模型格式转换和 minimal build 应作为独立提交，不能与首次 runtime 裁剪同时落地。

微软官方的示例中，定制 arm64 `libonnxruntime.so` 的原始体积可从约 16.3 MB 降至约 4.0 MB；实际收益取决于本模型需要的算子。项目目标按保守范围估计为 APK 减少约 3～5 MB，不能把该估计当作发布门槛。

验收条件：

- 两种 ABI 均能加载模型，且所有固定 OCR 样本的输出与基线一致。
- 不新增联网、Google Play 服务或运行时下载要求。
- OCR 耗时和峰值内存不出现不可解释的明显回退；建议以超过基线 10% 作为调查阈值，而不是自动判定失败。
- 自定义 AAR 能从记录的 tag、配置和命令重复构建。
- AAR 与 APK 内包含所需许可证和 NOTICE。

参考资料：

- [ONNX Runtime custom build](https://onnxruntime.ai/docs/build/custom.html)
- [Reduced operator config file](https://onnxruntime.ai/docs/reference/operators/reduced-operator-config-file.html)
- [ORT model format](https://onnxruntime.ai/docs/performance/model-optimizations/ort-format-models.html)

### 阶段 3：发布方式优化

直接分发时，优先向用户提供匹配设备的单 ABI APK；universal APK 继续作为不知道设备 ABI 时的兼容选项。应用商店若支持 AAB，则由商店按设备交付 ABI，不需要改变应用功能。

这一步不会缩小 universal 文件本身，但会把大多数用户的实际下载量从约 30 MB 降到约 20 MB 或后续优化后的单 ABI 体积。

## 明确不采用的做法

除非产品要求发生变化，否则不要采用以下手段：

- 将 ML Kit 中文识别改为非捆绑/动态下载模型：会影响首次使用和完整离线能力。
- 量化或替换 Tiny OCR 模型：可能改变识别精度，应视为模型升级而非无损瘦身。
- 手工删除 ML Kit 或 PDFBox assets：资源裁剪器不会分析 `assets/`，但这些文件可能在运行时按名称加载；删除会缩小兼容范围。
- 排除 Bouncy Castle、字体或 CMap：可能破坏加密 PDF、特殊字体或非拉丁字符解析。
- 移除 `armeabi-v7a`：会缩小 universal APK，但会减少支持设备范围。
- 对已压缩的 ONNX 模型重复套用普通 ZIP 压缩：当前模型从 4.49 MB 压到约 4.12 MB，收益有限。

## 发布验收与目标

完成任一阶段后，正式交付仍必须通过 `scripts/build-apk.ps1 release`，并按照 `RELEASE.md` 验证签名、包名、版本、ABI 和 SHA-256。

体积目标分两级：

- 收窄 R8 规则后：以不回归为前提，目标是 arm64 APK 低于当前 21.05 MB 基线。
- 完成定制 ONNX Runtime 后：arm64 APK 的参考目标为 16～18 MB。

目标值只用于判断方案是否值得维护，不能覆盖功能和兼容性验收。任何阶段只要无法证明与基线等价，就回退该阶段，不继续叠加下一阶段。
