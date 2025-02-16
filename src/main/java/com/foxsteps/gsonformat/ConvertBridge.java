package com.foxsteps.gsonformat;

import com.foxsteps.gsonformat.action.DataWriter;
import com.foxsteps.gsonformat.common.CheckUtil;
import com.foxsteps.gsonformat.common.FieldHelper;
import com.foxsteps.gsonformat.common.PsiClassUtil;
import com.foxsteps.gsonformat.common.StringUtils;
import com.foxsteps.gsonformat.common.Utils;
import com.foxsteps.gsonformat.config.Config;
import com.foxsteps.gsonformat.entity.ClassEntity;
import com.foxsteps.gsonformat.entity.DataType;
import com.foxsteps.gsonformat.entity.FieldApiInfo;
import com.foxsteps.gsonformat.entity.FieldArrayEntity;
import com.foxsteps.gsonformat.entity.FieldEntity;
import com.foxsteps.gsonformat.entity.IterableFieldEntity;
import com.foxsteps.gsonformat.tools.json.JSONArray;
import com.foxsteps.gsonformat.tools.json.JSONObject;
import com.foxsteps.gsonformat.ui.FieldsDialog;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTypesUtil;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.util.TextUtils;


/**
 * Created by dim on 2015/8/21.
 * 把 json 转成 实体类
 */
public class ConvertBridge {

    private PsiClass targetClass;
    private PsiClass currentClass;
    private PsiElementFactory factory;
    private Project project;
    private PsiFile file;

    /**
     * json字符串
     */
    private String jsonStr;

    /**
     * json注释
     */
    private String jsonComment;

    /**
     * 声明的字段
     */
    private Map<String, FieldEntity> declareFields;

    /**
     * 声明的类
     */
    private Map<String, ClassEntity> declareClass;

    /**
     * 生成的类
     */
    private String generateClassName;
    private ClassEntity generateClassEntity = new ClassEntity();

    /**
     * 过滤的正则
     */
    private StringBuilder fullFilterRegex = null;
    private StringBuilder briefFilterRegex = null;
    private String filterRegex = null;

    private Operator operator;
    private String packageName;

    public ConvertBridge(Operator operator,
                         String jsonStr, String jsonComment, PsiFile file, Project project,
                         PsiClass targetClass,
                         PsiClass currentClass, String generateClassName) {
        factory = JavaPsiFacade.getElementFactory(project);
        this.file = file;
        this.generateClassName = generateClassName;
        this.operator = operator;
        this.jsonStr = jsonStr;
        this.jsonComment = jsonComment;
        this.project = project;
        this.targetClass = targetClass;
        this.currentClass = currentClass;

        declareFields = new HashMap<>();
        declareClass = new HashMap<>();
        packageName = StringUtils.getPackage(generateClassName);
        fullFilterRegex = new StringBuilder();
        briefFilterRegex = new StringBuilder();

        //清理已声明的数据
        CheckUtil.getInstant().cleanDeclareData();

        //获取配置注解字符串
        String[] arg = Config.getInstant().getAnnotationStr().replace("{filed}", "(\\w+)").split("\\.");

        for (int i = 0; i < arg.length; i++) {
            String s = arg[i];
            if (i == arg.length - 1) {
                briefFilterRegex.append(s);
                fullFilterRegex.append(s);
                Matcher matcher = Pattern.compile("\\w+").matcher(s);
                if (matcher.find()) {
                    filterRegex = matcher.group();
                }
            } else {
                fullFilterRegex.append(s).append("\\s*\\.\\s*");
            }
        }


    }

    public void run() {
        JSONObject json = null;
        //清理错误信息
        operator.cleanErrorInfo();
        try {
            //转换为JSONObject
            json = parseJSONObject(jsonStr);
        } catch (Exception e) {
            String jsonTS = removeComment(jsonStr);
            jsonTS = jsonTS.replaceAll("^.*?\\{", "{");
            try {
                json = parseJSONObject(jsonTS);
            } catch (Exception e2) {
                handleDataError(e2);
            }
        }
        if (json != null) {
            try {
                //获取目标类
                ClassEntity classEntity = collectClassAttribute(targetClass, Config.getInstant().isReuseEntity());
                if (classEntity != null) {
                    //添加已声明的字段
                    for (FieldEntity item : classEntity.getFields()) {
                        declareFields.put(item.getKey(), item);
                        CheckUtil.getInstant().addDeclareFieldName(item.getKey());
                    }
                }
                //单独生成子类
                if (Config.getInstant().isSplitGenerate()) {
                    collectPackAllClassName();
                }
                operator.setVisible(false);
                parseJson(json);
            } catch (Exception e2) {
                handleDataError(e2);
                operator.setVisible(true);
            }
        }
        declareFields = null;
        declareClass = null;
    }

    private JSONObject parseJSONObject(String jsonStr) {
        if (jsonStr.startsWith("{")) {
            return new JSONObject(jsonStr);
        } else if (jsonStr.startsWith("[")) {
            JSONArray jsonArray = new JSONArray(jsonStr);

            if (jsonArray.length() > 0 && jsonArray.get(0) instanceof JSONObject) {
                return getJsonObject(jsonArray);
            }
        }
        return null;

    }

    private JSONObject getJsonObject(JSONArray jsonArray) {
        JSONObject resultJSON = jsonArray.getJSONObject(0);

        for (int i = 1; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (!(value instanceof JSONObject)) {
                break;
            }
            JSONObject json = (JSONObject) value;
            for (String key : json.keySet()) {
                if (!resultJSON.keySet().contains(key)) {
                    resultJSON.put(key, json.get(key));
                }
            }
        }
        return resultJSON;
    }

    private void collectPackAllClassName() {
        File packageFile = PsiClassUtil.getPackageFile(file, packageName);
        if (packageFile != null) {
            File[] files = packageFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (packageName == null) {
                        CheckUtil.getInstant().addDeclareClassName(file.getName());
                    } else {
                        CheckUtil.getInstant().addDeclareClassName(packageName + "." + file.getName());
                    }
                }
            }
        }

    }

    private void handleDataError(Exception e2) {
        e2.printStackTrace();
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        e2.printStackTrace(printWriter);
        printWriter.close();
        operator.showError(Error.DATA_ERROR);
        operator.setErrorInfo(writer.toString());
    }

    /**
     * 收集类属性
     *
     * @param psiClass
     * @param collectInnerClass 是否收集内部类
     * @return
     */
    private ClassEntity collectClassAttribute(PsiClass psiClass, boolean collectInnerClass) {
        if (psiClass == null) {
            return null;
        }
        ClassEntity innerClass = new ClassEntity();
        innerClass.setLock(true);
        declareClass.put(psiClass.getQualifiedName(), innerClass);
        CheckUtil.getInstant().addDeclareClassName(psiClass.getQualifiedName());
        innerClass.setClassName(psiClass.getName());
        innerClass.addAllFields(collectDeclareFields(psiClass));
        innerClass.setPsiClass(psiClass);
        innerClass.setPackName(getPackName(psiClass));
        if (collectInnerClass) {
            recursionInnerClass(innerClass);
        }
        return innerClass;
    }

    /**
     * 递归内部类
     *
     * @param classEntity
     */
    private void recursionInnerClass(ClassEntity classEntity) {
        PsiClass[] innerClassArray = classEntity.getPsiClass().getAllInnerClasses();
        for (PsiClass psiClass : innerClassArray) {
            ClassEntity item = new ClassEntity();
            item.setLock(true);
            if (declareClass.containsKey(psiClass.getQualifiedName())) {
                return;
            }
            declareClass.put(psiClass.getQualifiedName(), item);
            CheckUtil.getInstant().addDeclareClassName(psiClass.getQualifiedName());
            item.setClassName(psiClass.getName());
            item.addAllFields(collectDeclareFields(psiClass));
            item.setPsiClass(psiClass);
            item.setPackName(getPackName(psiClass));
            recursionInnerClass(item);
        }
    }

    public String getPackName(PsiClass psiClass) {
        String packName = null;
        if (psiClass.getQualifiedName() != null) {
            int i = psiClass.getQualifiedName().lastIndexOf(".");
            if (i >= 0) {
                packName = psiClass.getQualifiedName().substring(0, i);
            } else {
                packName = psiClass.getQualifiedName();
            }
        }
        return packName;

    }

    /**
     * 过滤掉// 和/** 注释
     *
     * @param str
     * @return
     */
    public String removeComment(String str) {
        String temp = str.replaceAll("/\\*" +
                "[\\S\\s]*?" +
                "\\*/", "");
        return temp.replaceAll("//[\\S\\s]*?\n", "");
    }

    /**
     * 收集声明的字段
     *
     * @param mClass
     * @return
     */
    private List<FieldEntity> collectDeclareFields(PsiClass mClass) {
        ArrayList<FieldEntity> filterFieldList = new ArrayList<>();
        if (mClass != null) {
            PsiField[] psiFields = mClass.getAllFields();
            for (PsiField psiField : psiFields) {
                String fileName = null;

                String psiFieldText = removeComment(psiField.getText());
                if (filterRegex != null && psiFieldText.contains(filterRegex)) {
                    boolean isSerializedName = false;
                    psiFieldText = psiFieldText.trim();
                    Pattern pattern = Pattern.compile(fullFilterRegex.toString());
                    Matcher matcher = pattern.matcher(psiFieldText);
                    if (matcher.find()) {
                        fileName = matcher.group(1);
                        isSerializedName = true;
                    }
                    pattern = Pattern.compile(briefFilterRegex.toString());
                    matcher = pattern.matcher(psiFieldText);
                    if (matcher.find()) {
                        fileName = matcher.group(1);
                        isSerializedName = true;
                    }
                    if (!isSerializedName) {
                        fileName = psiField.getName();
                    }
                } else {
                    fileName = psiField.getName();
                }
                FieldEntity fieldEntity = evalFieldEntity(null, psiField.getType());
                fieldEntity.setKey(fileName);
                fieldEntity.setFieldName(fileName);
                filterFieldList.add(fieldEntity);
            }
        }

        return filterFieldList;
    }

    private FieldEntity evalFieldEntity(FieldEntity fieldEntity, PsiType type) {

        if (type instanceof PsiPrimitiveType) {
            if (fieldEntity == null) {
                fieldEntity = new FieldEntity();
            }
            fieldEntity.setType(type.getPresentableText());
            return fieldEntity;
        } else if (type instanceof PsiArrayType) {
            if (fieldEntity == null) {
                fieldEntity = new IterableFieldEntity();
            }
            IterableFieldEntity iterableFieldEntity = (IterableFieldEntity) fieldEntity;
            iterableFieldEntity.setDeep(iterableFieldEntity.getDeep() + 1);
            return evalFieldEntity(fieldEntity, ((PsiArrayType) type).getComponentType());
        } else if (type instanceof PsiClassReferenceType) {
            PsiClass psi = ((PsiClassReferenceType) type).resolveGenerics().getElement();

            if (isCollection(psi)) {
                if (fieldEntity == null) {
                    fieldEntity = new IterableFieldEntity();
                }
                IterableFieldEntity iterableFieldEntity = (IterableFieldEntity) fieldEntity;
                iterableFieldEntity.setDeep(iterableFieldEntity.getDeep() + 1);
                PsiType[] parameters = ((PsiClassReferenceType) type).getParameters();
                if (parameters.length > 0) {
                    PsiType parameter = parameters[0];
                    if (parameter instanceof PsiWildcardType) {
                        if (((PsiWildcardType) parameter).isExtends()) {
                            final PsiType extendsBound = ((PsiWildcardType) parameter).getExtendsBound();

                            evalFieldEntity(fieldEntity, extendsBound);
                        }
                        if (((PsiWildcardType) parameter).isSuper()) {
                            final PsiType superBound = ((PsiWildcardType) parameter).getSuperBound();
                            evalFieldEntity(fieldEntity, superBound);
                        }
                    } else if (parameter instanceof PsiClassReferenceType) {

                        PsiClass element = ((PsiClassReferenceType) parameter).resolveGenerics().getElement();
                        handleClassReferenceType(fieldEntity, element);
                    }
                }
                return fieldEntity;
            } else {

                if (fieldEntity == null) {
                    fieldEntity = new FieldEntity();
                }
                handleClassReferenceType(fieldEntity, psi);
                return fieldEntity;
            }

        }
        if (fieldEntity == null) {
            fieldEntity = new IterableFieldEntity();
        }
        return fieldEntity;
    }

    /**
     * 处理类引用类型
     *
     * @param fieldEntity
     * @param psi
     */
    private void handleClassReferenceType(FieldEntity fieldEntity, PsiClass psi) {
        if (psi == null || psi.getQualifiedName() == null) {
            return;
        }
        switch (psi.getQualifiedName()) {
            case "java.lang.String":
                fieldEntity.setType("String");
                break;
            case "java.lang.Boolean":
                fieldEntity.setType("Boolean");
                break;
            case "java.lang.Integer":
                fieldEntity.setType("Integer");
                break;
            case "java.lang.Double":
                fieldEntity.setType("Double");
                break;
            case "java.lang.Long":
                fieldEntity.setType("Long");
                break;
            default:
                ClassEntity classEntity = declareClass.get(psi.getQualifiedName());
                if (classEntity == null) {
                    classEntity = collectClassAttribute(psi, true);
                }
                fieldEntity.setTargetClass(classEntity);
                break;
        }
    }

    private boolean isCollection(PsiClass element) {

        if ("java.util.Collection".equals(element.getQualifiedName())) {
            return true;
        }
        for (PsiClass psiClass : element.getInterfaces()) {
            if (isCollection(psiClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析Json
     *
     * @param json
     */
    private void parseJson(JSONObject json) {
        List<String> generateFiled = collectGenerateFiled(json);
        if (Config.getInstant().isVirgoMode()) {
            handleVirgoMode(json, generateFiled);
        } else {
            handleNormal(json, generateFiled);
        }
    }

    private void handleVirgoMode(JSONObject json, List<String> fieldList) {
        generateClassEntity.setClassName("");
        generateClassEntity.setPsiClass(targetClass);
        Map<String, FieldApiInfo> fieldApiInfoMap = FieldHelper.getFieldApiInfo(jsonComment, Config.getInstant().getSuffixStr());

        List<FieldEntity> fields = createFields(json, fieldApiInfoMap, fieldList, generateClassEntity);
        generateClassEntity.addAllFields(fields);
        FieldsDialog fieldsDialog = new FieldsDialog(operator, generateClassEntity, factory,
                targetClass, currentClass, file, project, generateClassName);
        fieldsDialog.setSize(900, 500);
        fieldsDialog.setLocationRelativeTo(null);
        fieldsDialog.setVisible(true);
    }

    private void handleNormal(JSONObject json, List<String> generateFiled) {
        WriteCommandAction.runWriteCommandAction(project, new Runnable() {
            @Override
            public void run() {
                if (targetClass == null) {
                    try {
                        targetClass = PsiClassUtil.getPsiClass(file, project, generateClassName);
                    } catch (Throwable throwable) {
                        handlePathError(throwable);
                    }
                }
                if (targetClass != null) {
                    generateClassEntity.setPsiClass(targetClass);
                    try {
                        generateClassEntity.addAllFields(createFields(json, null, generateFiled, generateClassEntity));
                        operator.setVisible(false);
                        DataWriter dataWriter = new DataWriter(file, project, targetClass);
                        dataWriter.execute(generateClassEntity);
                        Config.getInstant().saveCurrentPackPath(packageName);
                        operator.dispose();
                    } catch (Exception e) {
                        throw e;
                    }
                }
            }
        });
    }

    /**
     * 收集要生成的字段
     *
     * @param json
     * @return
     */
    private List<String> collectGenerateFiled(JSONObject json) {
        Set<String> keySet = json.keySet();
        List<String> fieldList = new ArrayList<String>();
        for (String key : keySet) {
            if (!existDeclareField(key, json)) {
                fieldList.add(key);
            }
        }
        return fieldList;
    }

    private boolean existDeclareField(String key, JSONObject json) {
        FieldEntity fieldEntity = declareFields.get(key);
        if (fieldEntity == null) {
            return false;
        }
        return fieldEntity.isSameType(json.get(key));
    }

    private void handlePathError(Throwable throwable) {
        throwable.printStackTrace();
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.close();
        operator.setErrorInfo(writer.toString());
        operator.setVisible(true);
        operator.showError(Error.PATH_ERROR);
    }

    /**
     * 创建字段
     *
     * @param json
     * @param fieldApiInfoMap
     * @param fieldList
     * @param parentClass
     * @return
     */
    private List<FieldEntity> createFields(JSONObject json, Map<String, FieldApiInfo> fieldApiInfoMap, List<String> fieldList, ClassEntity parentClass) {
        List<FieldEntity> fieldEntityList = new ArrayList<FieldEntity>();
        List<FieldArrayEntity> listEntityList = new ArrayList<FieldArrayEntity>();
        //是否添加注释
        //boolean writeExtra = Config.getInstant().isGenerateComments();
        //去掉构造函数注释
        boolean writeExtra=false;
        for (int i = 0; i < fieldList.size(); i++) {
            String key = fieldList.get(i);
            Object value = json.get(key);
            if (value instanceof JSONArray) {
                FieldArrayEntity arrayEntity=new FieldArrayEntity();
                arrayEntity.setSortNo(i);
                arrayEntity.setKey(key);
                listEntityList.add(arrayEntity);
                continue;
            }
            //创建字段实体
            FieldEntity fieldEntity = createField(parentClass, key, value, fieldApiInfoMap);
            fieldEntity.setSortNo(i);
            fieldEntityList.add(fieldEntity);
            if (writeExtra) {
                writeExtra = false;
                parentClass.setExtra(Utils.createCommentString(json, fieldList));
            }
        }

        //集合类型
        for (int i = 0; i < listEntityList.size(); i++) {
            FieldArrayEntity entity = listEntityList.get(i);
            Object type = json.get(entity.getKey());
            FieldEntity fieldEntity = createField(parentClass, entity.getKey(), type, fieldApiInfoMap);
            String className=null;
            if (StringUtils.isNotBlank(parentClass.getQualifiedName())) {
                className=parentClass.getClassName();
            }
            String fieldComment = FieldHelper.getFieldComment(fieldApiInfoMap, entity.getKey(),className);
            fieldEntity.setFieldComment(fieldComment);
            fieldEntity.setRequired(FieldHelper.getFieldRequired(fieldApiInfoMap, entity.getKey(),className));
            fieldEntity.setSortNo(entity.getSortNo());
            fieldEntityList.add(fieldEntity);
        }
        List<FieldEntity> resultList = fieldEntityList.stream()
                .sorted(Comparator.comparing(FieldEntity::getSortNo))
                .collect(Collectors.toList());
        return resultList;
    }


    private FieldEntity createField(ClassEntity parentClass, String key, Object type, Map<String, FieldApiInfo> fieldApiInfoMap) {
        //过滤 不符合规则的key
        String fieldName = CheckUtil.getInstant().handleArg(key);
        if (Config.getInstant().isUseSerializedName() && fieldName.contains("_")) {
            if (fieldName.endsWith("_")) {
                fieldName = fieldName.substring(0, fieldName.lastIndexOf("_"));
            }
            fieldName = StringUtils.captureStringLeaveUnderscore(fieldName.toLowerCase());
        } else {
            //fieldName是全部大写
            if (Config.getInstant().isUseSerializedName() && StringUtils.isAcronym(fieldName)) {
                fieldName = fieldName.toLowerCase();
            }
            //首字母是大写，存在其他字母是小写
            if (StringUtils.isFirsrtUpper(fieldName)) {
                if (fieldName.length() == 1) {
                    fieldName = fieldName.toLowerCase();
                }else{
                    fieldName = fieldName.substring(0,1).toLowerCase()+ fieldName.substring(1);
                }
            }

        }
        fieldName = handleDeclareFieldName(fieldName, "");

        FieldEntity fieldEntity = typeByValue(parentClass, key, type, fieldApiInfoMap);
        fieldEntity.setFieldName(fieldName);
        return fieldEntity;
    }

    private String convertSerializedName(String fieldName) {
        if (Config.getInstant().isUseFieldNamePrefix() &&
                !TextUtils.isEmpty(Config.getInstant().getFiledNamePreFixStr())) {
            fieldName = Config.getInstant().getFiledNamePreFixStr() + "_" + fieldName;
        }
        return fieldName;
    }

    /**
     * 值的类型
     *
     * @param parentClass
     * @param key
     * @param type
     * @param fieldApiInfoMap
     * @return
     */
    private FieldEntity typeByValue(ClassEntity parentClass, String key, Object type, Map<String, FieldApiInfo> fieldApiInfoMap) {
        FieldEntity result;
        if (type instanceof JSONObject) {
            //对象类型
            ClassEntity classEntity = existDeclareClass((JSONObject) type);
            //内部类
            if (classEntity == null) {
                FieldEntity fieldEntity = new FieldEntity();
                ClassEntity innerClassEntity = createInnerClass(createSubClassName(key, type), (JSONObject) type, parentClass, fieldApiInfoMap);
                fieldEntity.setKey(key);
                fieldEntity.setTargetClass(innerClassEntity);
                result = fieldEntity;
            } else {
                FieldEntity fieldEntity = new FieldEntity();
                fieldEntity.setKey(key);
                fieldEntity.setTargetClass(classEntity);
                String innerComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, parentClass.getClassName());
                if (!StringUtils.isNotBlank(innerComment)) {
                    innerComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, null);
                    fieldEntity.setFieldComment(innerComment);
                    fieldEntity.setRequired(FieldHelper.getFieldRequired(fieldApiInfoMap, key,null));

                }
                result = fieldEntity;
            }
            String fieldComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, null);
            result.setFieldComment(fieldComment);
            result.setRequired(FieldHelper.getFieldRequired(fieldApiInfoMap, key,null));

        } else if (type instanceof JSONArray) {
            //集合类型
            result = handleJSONArray(parentClass, (JSONArray) type, key, 1, fieldApiInfoMap);
            String fieldComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, null);
            result.setFieldComment(fieldComment);
            result.setRequired(FieldHelper.getFieldRequired(fieldApiInfoMap, key,null));
        } else {
            FieldEntity fieldEntity = new FieldEntity();
            fieldEntity.setKey(key);
            String vType;
            if (Config.getInstant().isUseWrapperClass()) {
                vType = PsiTypesUtil.boxIfPossible(DataType.getWrapperTypeSimpleName(DataType.typeOfObject(type)));
            } else {
                vType = DataType.typeOfObject(type).getValue();
            }
            fieldEntity.setType(vType);
            result = fieldEntity;
            if (type != null) {
                result.setValue(type.toString());
            }
            String fieldComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, parentClass.getClassName());
            if (!StringUtils.isNotBlank(fieldComment)) {
                fieldComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, null);
                result.setFieldComment(fieldComment);
            }
            result.setFieldComment(fieldComment);

            String required = FieldHelper.getFieldRequired(fieldApiInfoMap, key, parentClass.getClassName());
            if (!StringUtils.isNotBlank(required)) {
                required = FieldHelper.getFieldRequired(fieldApiInfoMap, key, null);
                result.setRequired(FieldHelper.getFieldRequired(fieldApiInfoMap, key,null));
            }
            result.setRequired(required);

        }
        result.setKey(key);
        return result;
    }

    private ClassEntity existDeclareClass(JSONObject jsonObject) {
        for (ClassEntity classEntity : declareClass.values()) {
            Iterator<String> keys = jsonObject.keys();
            boolean had = false;
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonObject.get(key);
                had = false;
                for (FieldEntity fieldEntity : classEntity.getFields()) {
                    if (fieldEntity.getKey().equals(key) && DataType.isSameDataType(DataType.typeOfString(fieldEntity.getType()), DataType.typeOfObject(value))) {
                        had = true;
                        break;
                    }
                }
                if (!had) {
                    break;
                }
            }
            if (had) {
                return classEntity;
            }
        }
        return null;
    }

    /**
     * @param className
     * @param json
     * @param parentClass
     * @param fieldApiInfoMap
     * @return
     */
    private ClassEntity createInnerClass(String className, JSONObject json, ClassEntity parentClass, Map<String, FieldApiInfo> fieldApiInfoMap) {

        //拆分生成子类
        if (Config.getInstant().isSplitGenerate()) {
            String qualifiedName = packageName == null ? className : packageName + "." + className;
            if (CheckUtil.getInstant().containsDeclareClassName(qualifiedName)) {
                //存在同名。
                PsiClass psiClass = PsiClassUtil.exist(file, qualifiedName);
                if (psiClass != null) {
                    ClassEntity classEntity = collectClassAttribute(psiClass, false);
                    classEntity.setLock(true);
                    if (classEntity.isSame(json)) {
//                        if (Config.getInstant().isReuseEntity()) {
                        declareClass.put(classEntity.getQualifiedName(), classEntity);
//                        }
                        return classEntity;
                    }
                }
            }
        }

        //生成内部类
        ClassEntity subClassEntity = new ClassEntity();
        if (Config.getInstant().isSplitGenerate()) {
            subClassEntity.setPackName(packageName);
        } else {
            subClassEntity.setPackName(parentClass.getQualifiedName());
        }
        subClassEntity.setClassName(className);
        String subComment = FieldHelper.getFieldComment(fieldApiInfoMap, className, null);
        subClassEntity.setClassDesc(subComment);
        Set<String> set = json.keySet();
        List<String> list = new ArrayList<String>(set);
        List<FieldEntity> fields = createFields(json, fieldApiInfoMap, list, subClassEntity);
        subClassEntity.addAllFields(fields);

        if (handleDeclareClassName(subClassEntity, "")) {
            CheckUtil.getInstant().addDeclareClassName(subClassEntity.getQualifiedName());
        }
        if (Config.getInstant().isReuseEntity()) {
            declareClass.put(subClassEntity.getQualifiedName(), subClassEntity);
        }
        parentClass.addInnerClass(subClassEntity);

        return subClassEntity;
    }

    private boolean handleDeclareClassName(ClassEntity classEntity, String appendName) {

        classEntity.setClassName(classEntity.getClassName() + appendName);
        if (CheckUtil.getInstant().containsDeclareClassName(classEntity.getQualifiedName())) {
            return handleDeclareClassName(classEntity, "X");
        }
        return true;
    }

    private String handleDeclareFieldName(String fieldName, String appendName) {
        fieldName += appendName;
        if (CheckUtil.getInstant().containsDeclareFieldName(fieldName)) {
            return handleDeclareFieldName(fieldName, "X");
        }
        return fieldName;
    }

    private String createSubClassName(String key, Object o) {
        String name = "";
        if (o instanceof JSONObject) {
            if (TextUtils.isEmpty(key)) {
                return key;
            }
            String[] strings = key.split("_");
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < strings.length; i++) {
                stringBuilder.append(StringUtils.captureName(strings[i]));
            }
            name = stringBuilder.toString() + Config.getInstant().getSuffixStr();
        }
        return name;

    }

    private FieldEntity handleJSONArray(ClassEntity parentClass, JSONArray jsonArray, String key, int deep, Map<String, FieldApiInfo> fieldApiInfoMap) {

        FieldEntity fieldEntity;
        if (jsonArray.length() > 0) {
            Object item = jsonArray.get(0);
            if (item instanceof JSONObject) {
                item = getJsonObject(jsonArray);
            }
            fieldEntity = listTypeByValue(parentClass, key, item, deep, fieldApiInfoMap);
        } else {
            fieldEntity = new IterableFieldEntity();
            fieldEntity.setKey(key);
            fieldEntity.setType("?");
            ((IterableFieldEntity) fieldEntity).setDeep(deep);
        }
        return fieldEntity;
    }

    private FieldEntity listTypeByValue(ClassEntity parentClass, String key, Object type, int deep, Map<String, FieldApiInfo> fieldApiInfoMap) {

        FieldEntity item = null;
        if (type instanceof JSONObject) {
            ClassEntity classEntity = existDeclareClass((JSONObject) type);
            if (classEntity == null) {
                IterableFieldEntity iterableFieldEntity = new IterableFieldEntity();
                ClassEntity innerClassEntity = createInnerClass(createSubClassName(key, type), (JSONObject) type, parentClass, fieldApiInfoMap);
                String simpleName = innerClassEntity.getClassName();
                if (simpleName.contains(".")) {
                    simpleName = simpleName.substring(simpleName.lastIndexOf(".") + 1);
                }
                iterableFieldEntity.setKey(key);
                iterableFieldEntity.setDeep(deep);
                iterableFieldEntity.setTargetClass(innerClassEntity);
                item = iterableFieldEntity;
                String innerClassComment = FieldHelper.getFieldComment(fieldApiInfoMap, simpleName, null);
                innerClassEntity.setClassDesc(innerClassComment);
                item.setFieldComment(innerClassComment);
                item.setRequired(FieldHelper.getFieldRequired(fieldApiInfoMap, simpleName,null));
            } else {
                IterableFieldEntity fieldEntity = new IterableFieldEntity();
                fieldEntity.setKey(key);
                fieldEntity.setTargetClass(classEntity);
                fieldEntity.setType(classEntity.getQualifiedName());
                fieldEntity.setDeep(deep);
                item = fieldEntity;
                String innerFieldComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, classEntity.getClassName());
                if (!StringUtils.isNotBlank(innerFieldComment)) {
                    innerFieldComment = FieldHelper.getFieldComment(fieldApiInfoMap, key, null);
                }
                fieldEntity.setFieldComment(innerFieldComment);

                String required = FieldHelper.getFieldRequired(fieldApiInfoMap, key, classEntity.getClassName());
                if (!StringUtils.isNotBlank(required)) {
                    required = FieldHelper.getFieldRequired(fieldApiInfoMap, key, null);
                }
                fieldEntity.setRequired(required);

            }

        } else if (type instanceof JSONArray) {
            FieldEntity fieldEntity = handleJSONArray(parentClass, (JSONArray) type, key, ++deep, fieldApiInfoMap);
            fieldEntity.setKey(key);
            item = fieldEntity;
        } else {
            IterableFieldEntity fieldEntity = new IterableFieldEntity();
            fieldEntity.setKey(key);
            fieldEntity.setType(type.getClass().getSimpleName());
            fieldEntity.setDeep(deep);
            item = fieldEntity;
        }
        return item;
    }


    public interface Operator {

        void showError(Error err);

        void dispose();

        void setVisible(boolean visible);

        void setErrorInfo(String error);

        void cleanErrorInfo();
    }

    public enum Error {
        /**
         * 错误枚举
         */
        DATA_ERROR,
        PARSE_ERROR,
        PATH_ERROR;
    }
}

