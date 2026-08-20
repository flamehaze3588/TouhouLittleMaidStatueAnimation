package com.tlmstatueanimation.client.model.ysmfile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 移植自 OpenYSM（https://github.com/OpenYSM/OpenYSM，YSM 2.6.5 反编译重构），仅供兼容互操作。
 * YSMBinaryDeserializer 的完整走读器：deserializeModern（format >= 16）分支逐字节对应
 * 参考反序列化器；legacy V15（format 4~15）分支参照 OpenYSM/YSMParser（C++ 版，
 * deserializeLegacyV15）移植——二进制流顺序变长，所有段都必须按格式走读，
 * 不需要的数据读后即弃，仅保留 extraAnimations（key → 默认显示名，保持声明顺序）、
 * extraAnimationClassify（子菜单 id → 有序 key→默认名；format <= 9 的二进制没有该段，恒为空表）
 * 与 languageFiles（locale → (lang key → value)，用于动作显示名本地化；legacy V15 流中
 * 没有语言文件段，恒为空表）。
 * legacy V1（format < 4）直接抛异常，由调用方跳过。
 */
public final class YsmBinaryModelWalker {

    /**
     * @param extraAnimations        轮盘动作：key → 默认显示名（LinkedHashMap，保持声明顺序）
     * @param extraAnimationClassify 子菜单表：classify id → （key → 默认显示名，保持声明顺序）
     * @param languageFiles          locale → (lang key → value)
     */
    public record YsmModelData(LinkedHashMap<String, String> extraAnimations,
                               Map<String, LinkedHashMap<String, String>> extraAnimationClassify,
                               Map<String, Map<String, String>> languageFiles) {
    }

    private final YsmByteReader reader;
    private final int format;
    private final LinkedHashMap<String, String> extraAnimations = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, String>> extraAnimationClassify = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> languageFiles = new LinkedHashMap<>();

    private YsmBinaryModelWalker(byte[] decompressedData) {
        this.reader = new YsmByteReader(decompressedData);
        this.format = (int) this.reader.readDword();
    }

    /**
     * 走读解密后的 YSM 二进制模型数据。
     *
     * @throws UnsupportedOperationException format < 4（legacy V1，不支持）
     */
    public static YsmModelData walk(byte[] decompressedData) {
        YsmBinaryModelWalker walker = new YsmBinaryModelWalker(decompressedData);
        if (walker.format < 4) {
            throw new UnsupportedOperationException("Unsupported YSM binary format " + walker.format + " (legacy V1)");
        }
        if (walker.format <= 15) {
            walker.deserializeLegacyV15();
        } else {
            walker.deserializeModern();
        }
        return new YsmModelData(walker.extraAnimations, walker.extraAnimationClassify, walker.languageFiles);
    }

    // ============ legacy V15 格式（format 4 ~ 15，移植自 OpenYSM/YSMParser 的 deserializeLegacyV15）============
    // 与 modern 布局差异：无 sound/function/language 前置段（sound 挪到纹理之后且无 hash），
    // 模型/动画以 (varint id, varint unk, 数据) 列表组织，纹理为裸 RGBA（无 hash/format/unkFlag），
    // 结尾多三张哈希对照表，最后才是 ysmJson 段。

    private void deserializeLegacyV15() {
        int needSkipBytes = reader.readVarInt();
        reader.skipBytes(needSkipBytes);

        int modelCount = reader.readVarInt();
        for (int i = 0; i < modelCount; ++i) {
            reader.readVarInt(); // modelId（1=main, 2=arm, 3=arrow）
            reader.readVarInt(); // unk
            parseModels(); // legacy 无前置 sha256
        }

        int animationBlobCount = reader.readVarInt();
        for (int i = 0; i < animationBlobCount; ++i) {
            reader.readVarInt(); // animType id（3=extra 等）
            reader.readVarInt(); // unk
            parseAnimations(); // legacy 无前置 file hash
        }

        if (format > 9) {
            // 注意：controllerCount 在 parseAnimationControllers 内部读取
            //（与 deserializeModern 一致，不要在这里再读一次）
            parseAnimationControllers();
            int controllerTableSize = reader.readVarInt();
            for (int i = 0; i < controllerTableSize; ++i) {
                reader.readString(); // controller name
                reader.readString(); // controller hash
            }
        }

        int textureCount = reader.readVarInt();
        for (int i = 0; i < textureCount; ++i) {
            reader.readString(); // texture name
            reader.readByteArray(); // raw RGBA data
            reader.readVarInt(); // width
            reader.readVarInt(); // height
            int subTextureSize = reader.readVarInt();
            for (int j = 0; j < subTextureSize; ++j) {
                reader.readVarInt(); // specularType（1=NORMAL，2=高光）
                reader.readByteArray(); // raw RGBA data
                reader.readVarInt(); // width
                reader.readVarInt(); // height
            }
        }

        if (format > 9) {
            parseSoundFiles(); // format <= 15：无 hash 字段
            int soundTableCount = reader.readVarInt();
            for (int i = 0; i < soundTableCount; ++i) {
                reader.readString(); // sound name
                reader.readString(); // sound hash
            }
        }

        int avatarCount = reader.readVarInt();
        for (int i = 0; i < avatarCount; ++i) {
            reader.readString(); // avatar name
            reader.readByteArray(); // raw RGBA data
            reader.readVarInt(); // width
            reader.readVarInt(); // height
        }

        int modelTableSize = reader.readVarInt();
        for (int i = 0; i < modelTableSize; ++i) {
            reader.readVarInt(); // modelId
            reader.readString(); // model hash
        }

        int animationsTableSize = reader.readVarInt();
        for (int i = 0; i < animationsTableSize; ++i) {
            reader.readVarInt(); // animType id
            reader.readString(); // animation hash
        }

        int textureTableSize = reader.readVarInt();
        for (int i = 0; i < textureTableSize; ++i) {
            reader.readString(); // texture name
            reader.readString(); // texture hash
            int subTextureSize = reader.readVarInt();
            for (int j = 0; j < subTextureSize; ++j) {
                reader.readVarInt(); // specularType
                reader.readString(); // sub texture hash
            }
        }

        parseYSMJson();
    }

    // ============ 现代格式（format >= 16）============

    private void deserializeModern() {
        parseSoundFiles();
        parseFunctionFiles();
        parseLanguageFiles();

        if (format < 26) {
            int subEntityTotalCount = reader.readVarInt();
            for (int i = 0; i < subEntityTotalCount; ++i) {
                parseSubEntity(i);
            }
            reader.readVarInt(); // footerFlag, always 00
        } else {
            int vehiclesTotalCount = reader.readVarInt();
            for (int i = 0; i < vehiclesTotalCount; ++i) {
                parseSubEntity(i);
            }
            int projectilesTotalCount = reader.readVarInt();
            for (int i = 0; i < projectilesTotalCount; ++i) {
                parseSubEntity(i);
            }
        }

        int unknownEntityFlag = reader.readVarInt();
        if (unknownEntityFlag != 1) throw new IllegalStateException("Expected 1 after SubEntities");

        int animationCount = reader.readVarInt();
        for (int i = 0; i < animationCount; ++i) {
            reader.readVarInt(); // anim type
            reader.readString(); // file hash
            parseAnimations();
        }

        parseAnimationControllers();
        parseTextureFiles();

        int modelTotalCount = reader.readVarInt();
        for (int i = 0; i < modelTotalCount; ++i) {
            int modelType = reader.readVarInt();
            reader.readString(); // sha256
            parseModels();
            if (modelType < 1 || modelType > 3) {
                throw new IllegalStateException("Unknown model type: " + modelType);
            }
        }

        parseYSMJson();
    }

    private void parseSubEntity(int index) {
        if (format <= 26) {
            reader.readString(); // subModuleName
        }
        int animationCount = reader.readVarInt();
        for (int i = 0; i < animationCount; ++i) {
            reader.readString(); // file hash
            parseAnimations();
        }
        int separator = reader.readVarInt();
        if (separator != 0) throw new IllegalStateException("Separator != 0");

        // base texture
        parseSpecialImage();
        reader.readVarInt(); // width
        reader.readVarInt(); // height
        reader.readVarInt(); // imageFormat
        reader.readVarInt(); // unknownFlag

        int subTextureSize = reader.readVarInt();
        for (int i = 0; i < subTextureSize; ++i) {
            reader.readVarInt(); // specularType
            parseSpecialImage();
            reader.readVarInt(); // width
            reader.readVarInt(); // height
            reader.readVarInt(); // imageFormat
            reader.readVarInt(); // unknownFlag
        }

        reader.readString(); // model hash
        parseModels();

        if (format > 26) {
            reader.readVarInt(); // footerFlag, always 01
            reader.readString(); // footerSubModuleName
        }
    }

    private void parseModels() {
        int boneCount = reader.readVarInt();
        for (int i = 0; i < boneCount; i++) {
            reader.readString(); // parentName
            int cubeCount = reader.readVarInt();

            for (int j = 0; j < cubeCount; j++) {
                int faceCount = reader.readVarInt();
                for (int k = 0; k < faceCount; k++) {
                    readVector3D(); // normal
                    for (int v = 0; v < 4; v++) {
                        readVector3D(); // position
                        reader.readFloat(); // u
                        reader.readFloat(); // v
                    }
                }
                reader.readVarInt(); // unkInt1
                reader.readVarInt(); // unkInt2
                reader.readVarInt(); // unkInt3
            }

            reader.readString(); // bone name
            reader.readVarInt(); // unkPad1
            reader.readVarInt(); // unkPad2
            reader.readVarInt(); // unkPad3
            reader.readVarInt(); // unkPad4
            reader.readVarInt(); // unkPad5

            readVector3D(); // pivot
            readVector3D(); // rotation
        }

        reader.readString(); // identifier
        reader.readFloat(); // textureHeight
        reader.readFloat(); // textureWidth
        reader.readFloat(); // visibleBoundsHeight
        reader.readFloat(); // visibleBoundsWidth

        int visibleBoundsOffsetSize = reader.readVarInt();
        for (int i = 0; i < visibleBoundsOffsetSize; i++) {
            reader.readFloat();
        }

        reader.readFloat(); // unkFloat1
        reader.readFloat(); // unkFloat2

        int hasInfoJsonFlag = reader.readVarInt();
        if (hasInfoJsonFlag > 0) {
            parseLegacyYSMInfo();
        }

        reader.readVarInt(); // footerPad1
        reader.readVarInt(); // footerPad2
        reader.readVarInt(); // footerPad3
    }

    /** 关键目标段：extraAnimations 与 classify 在这里；buttons（molang 配置面板）只走读不保留 */
    private void parseYSMJson() {
        reader.readString(); // properties sha256
        int isNewVersionYsm = reader.readVarInt();

        if (isNewVersionYsm != 0) {
            if (format <= 15) {
                reader.readVarInt(); // legacy 额外字段
            }
            reader.readString(); // metadata name
            reader.readString(); // tips
            reader.readString(); // licenseType
            reader.readString(); // licenseDescription

            int authorsCount = reader.readVarInt();
            for (int i = 0; i < authorsCount; i++) {
                reader.readString(); // author name
                reader.readString(); // role
                int contactsCount = reader.readVarInt();
                for (int j = 0; j < contactsCount; j++) {
                    reader.readString(); // contact key
                    reader.readString(); // contact value
                }
                reader.readString(); // comment
            }

            int linksCount = reader.readVarInt();
            for (int i = 0; i < linksCount; i++) {
                reader.readString(); // link key
                reader.readString(); // link value
            }
        }

        reader.readFloat(); // widthScale
        reader.readFloat(); // heightScale

        int extraAnimationsCount = reader.readVarInt();
        for (int i = 0; i < extraAnimationsCount; i++) {
            extraAnimations.put(reader.readString(), reader.readString());
        }

        if (format > 9) {
            int extraAnimationButtonsCount = reader.readVarInt();
            for (int i = 0; i < extraAnimationButtonsCount; i++) {
                reader.readString(); // button id
                reader.readString(); // button name
                reader.readVarInt(); // buttonPadding

                int configurationFormsCount = reader.readVarInt();
                for (int j = 0; j < configurationFormsCount; j++) {
                    reader.readString(); // form type
                    reader.readString(); // title
                    reader.readString(); // description
                    reader.readString(); // defaultValue
                    reader.readFloat(); // step
                    reader.readFloat(); // min
                    reader.readFloat(); // max
                    int labelsSize = reader.readVarInt();
                    for (int l = 0; l < labelsSize; l++) {
                        reader.readString(); // label key
                        reader.readString(); // label value
                    }
                }
            }

            int extraAnimationClassifyCount = reader.readVarInt();
            for (int i = 0; i < extraAnimationClassifyCount; i++) {
                String classifyId = reader.readString();
                LinkedHashMap<String, String> entries = new LinkedHashMap<>();
                int classificationExtrasCount = reader.readVarInt();
                for (int j = 0; j < classificationExtrasCount; j++) {
                    entries.put(reader.readString(), reader.readString()); // extra key → 默认显示名
                }
                extraAnimationClassify.put(classifyId, entries);
            }
        }

        reader.readString(); // defaultTexture
        reader.readString(); // previewAnimation
        reader.readVarInt(); // isFree

        if (format > 4) {
            reader.readVarInt(); // renderLayersFirst
        }

        if (format >= 15) {
            reader.readVarInt(); // allCutout
            reader.readVarInt(); // disablePreviewRotation
        }

        if (format > 15) {
            reader.readVarInt(); // guiNoLighting
            if (format >= 32) {
                reader.readVarInt(); // mergeMultilineExpr
            }

            reader.readString(); // guiForeground
            reader.readString(); // guiBackground

            int avatarsCount = reader.readVarInt();
            for (int i = 0; i < avatarsCount; i++) {
                reader.readString(); // avatar name
                reader.readByteArray(); // avatar data
                reader.readVarInt(); // width
                reader.readVarInt(); // height
                reader.readVarInt(); // format
                reader.readVarInt(); // unknownFlag
            }
        }

        // format <= 15 在此结束（无背景图段）
        if (format <= 15) {
            return;
        }
        int backgroundImagesCount = reader.readVarInt();
        for (int i = 0; i < backgroundImagesCount; i++) {
            reader.readString(); // name
            reader.readByteArray(); // data
            reader.readVarInt(); // width
            reader.readVarInt(); // height
            reader.readVarInt(); // format
            reader.readVarInt(); // unknownFlag
        }
    }

    private void parseLegacyYSMInfo() {
        reader.readString(); // name
        reader.readString(); // tips
        int extraAnimationsCount = reader.readVarInt();
        for (int i = 0; i < extraAnimationsCount; i++) {
            reader.readString();
        }
        int authorsCount = reader.readVarInt();
        for (int i = 0; i < authorsCount; i++) {
            reader.readString(); // author name
        }
        reader.readString(); // licenseType
        reader.readVarInt(); // isFree
    }

    private void parseAnimations() {
        int animationCount = reader.readVarInt();
        for (int animIndex = 0; animIndex < animationCount; ++animIndex) {
            reader.readString(); // anim name
            reader.readFloat(); // length
            reader.readVarInt(); // loopMode

            if (format > 9) {
                reader.readVarInt(); // unkInt1
                reader.readVarInt(); // unkInt2
                int blendWeightMolangCount = reader.readVarInt();
                for (int i = 0; i < blendWeightMolangCount; i++) {
                    readTaggedValue();
                }
                reader.readVarInt(); // unkInt4
            }

            int boneCount = reader.readVarInt();
            for (int i = 0; i < boneCount; ++i) {
                reader.readString(); // boneName
                parseChannel(); // rotation
                parseChannel(); // position
                parseChannel(); // scale
            }

            int timelineEventGroupsCount = reader.readVarInt();
            for (int i = 0; i < timelineEventGroupsCount; ++i) {
                int timelineEventsCount = reader.readVarInt();
                for (int j = 0; j < timelineEventsCount; ++j) {
                    reader.readString(); // event
                }
                reader.readFloat(); // timestamp
            }

            if (format > 9) {
                int soundEffectsCount = reader.readVarInt();
                for (int i = 0; i < soundEffectsCount; i++) {
                    reader.readString(); // effectName
                    reader.readFloat(); // timestamp
                }
            }
        }
    }

    private void parseChannel() {
        int keyframeCount = reader.readVarInt();
        if (keyframeCount == 0) return;

        for (int i = 0; i < keyframeCount; i++) {
            reader.readFloat(); // timestamp
            reader.readVarInt(); // interpolationMode

            for (int j = 0; j < 3; j++) {
                readTaggedValue();
            }

            boolean hasPreData = reader.readVarInt() > 0;
            if (hasPreData) {
                for (int j = 0; j < 3; j++) {
                    readTaggedValue();
                }
            }
        }
    }

    /** datatype 标签值：0x01 → float，0x02 → string（其余类型不读数据，与参考实现一致） */
    private void readTaggedValue() {
        byte datatype = reader.readByte();
        if (datatype == 0x01) {
            reader.readFloat();
        } else if (datatype == 0x02) {
            reader.readString();
        }
    }

    private void parseAnimationControllers() {
        int controllerCount = reader.readVarInt();
        for (int i = 0; i < controllerCount; i++) {
            if (format <= 15) {
                reader.readVarInt(); // legacyUnknownInt（无 name/hash）
            } else {
                // format >= 16：name + hash
                reader.readString(); // controller name
                reader.readString(); // hash
            }

            int animationCount = reader.readVarInt();
            for (int animIndex = 0; animIndex < animationCount; ++animIndex) {
                reader.readString(); // animationName
                reader.readString(); // initialState

                int statesCount = reader.readVarInt();
                for (int s = 0; s < statesCount; s++) {
                    reader.readString(); // state name

                    int animationsSize = reader.readVarInt();
                    for (int j = 0; j < animationsSize; j++) {
                        reader.readString();
                        reader.readString();
                    }
                    int transitionsSize = reader.readVarInt();
                    for (int j = 0; j < transitionsSize; j++) {
                        reader.readString();
                        reader.readString();
                    }
                    int onEntryCount = reader.readVarInt();
                    for (int j = 0; j < onEntryCount; j++) {
                        reader.readString();
                    }
                    int onExitCount = reader.readVarInt();
                    for (int j = 0; j < onExitCount; j++) {
                        reader.readString();
                    }
                    if (reader.readVarInt() != 0) {
                        reader.readFloat(); // blendTransitionValue
                    } else {
                        int blendTransitionsCount = reader.readVarInt();
                        for (int j = 0; j < blendTransitionsCount; j++) {
                            reader.readFloat();
                            reader.readFloat();
                        }
                    }
                    reader.readVarInt(); // blendViaShortestPath
                    if (format > 26) {
                        int soundEffectsCount = reader.readVarInt();
                        for (int j = 0; j < soundEffectsCount; j++) {
                            reader.readString();
                        }
                    }
                }
            }
        }
    }

    private void parseSoundFiles() {
        int soundCount = reader.readVarInt();
        for (int i = 0; i < soundCount; i++) {
            reader.readString(); // soundName
            if (format > 15) {
                reader.readString(); // hash
            }
            reader.readByteArray(); // data
        }
    }

    private void parseFunctionFiles() {
        int functionCount = reader.readVarInt();
        for (int i = 0; i < functionCount; i++) {
            reader.readString(); // functionName
            reader.readString(); // hash
            reader.readByteArray(); // data
        }
    }

    /** 语言文件：locale → (lang key → value)，动作显示名本地化就在这里 */
    private void parseLanguageFiles() {
        int languageCount = reader.readVarInt();
        for (int i = 0; i < languageCount; i++) {
            String languageName = reader.readString();
            reader.readString(); // hash
            int nodesCount = reader.readVarInt();
            Map<String, String> langMap = new LinkedHashMap<>();
            for (int j = 0; j < nodesCount; j++) {
                langMap.put(reader.readString(), reader.readString());
            }
            languageFiles.put(languageName, langMap);
        }
    }

    private void parseTextureFiles() {
        int textureCount = reader.readVarInt();
        for (int i = 0; i < textureCount; i++) {
            reader.readString(); // name
            reader.readString(); // hash
            reader.readByteArray(); // data
            reader.readVarInt(); // width
            reader.readVarInt(); // height
            reader.readVarInt(); // imageFormat
            reader.readVarInt(); // unknownFlag

            int subTextureSize = reader.readVarInt();
            for (int j = 0; j < subTextureSize; j++) {
                reader.readVarInt(); // specularType
                parseSpecialImage();
                reader.readVarInt(); // width
                reader.readVarInt(); // height
                reader.readVarInt(); // imageFormat
                reader.readVarInt(); // unknownFlag
            }
        }
    }

    private void parseSpecialImage() {
        reader.readString(); // image hash
        reader.readByteArray(); // image data
    }

    private void readVector3D() {
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
    }
}
