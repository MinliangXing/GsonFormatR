package com.foxsteps.gsonformat.common;

import com.foxsteps.gsonformat.entity.FieldEntity;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;

/**
 * @author wangzejun
 * @description 必填 注释工具
 * @date 2020年07月03日 19:10:14
 */
public class RequiredAnnotationUtils {

    /**
     * 增加字段注释
     *
     * @param field
     * @param fieldEntity
     * @param factory
     */
    public static void addFieldComment(PsiField field, FieldEntity fieldEntity, PsiElementFactory factory) {
        StringBuffer annotation = new StringBuffer();

        if (StringUtils.isNotBlank(fieldEntity.getRequired())) {
            if (fieldEntity.getBriefType().equals("String")) {
                annotation.append("@javax.validation.constraints.NotBlank(message = \"");
            } else if (fieldEntity.getBriefType().equals("List") || fieldEntity.getBriefType().equals("Map")
                || fieldEntity.getBriefType().equals("Set")) {
                annotation.append("@javax.validation.constraints.NotEmpty(message = \"");
            } else {
                annotation.append("@javax.validation.constraints.NotNull(message = \"");
            }
            if (StringUtils.isNotBlank(fieldEntity.getFieldComment())) {
                annotation.append(fieldEntity.getFieldComment());
            } else {
                annotation.append(fieldEntity.getFieldName());
            }
            annotation.append("不能为空 \")");

        }
        PsiAnnotation comment = factory.createAnnotationFromText(String.valueOf(annotation), null);
        field.addBefore(comment, field.getFirstChild());
    }
}
