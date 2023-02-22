package com.foxsteps.gsonformat.common;

import com.foxsteps.gsonformat.entity.FieldEntity;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;

/**
 * @author wangzejun
 * @description SwaggerDoc注释工具
 * @date 2020年07月03日 19:10:14
 */
public class SwaggerDocUtils {

    /**
     * 增加字段注释
     *
     * @param field
     * @param fieldEntity
     * @param factory
     */
    public static void addFieldComment(PsiField field, FieldEntity fieldEntity, PsiElementFactory factory) {
        StringBuffer swaggerDoc = new StringBuffer();
        //        @ApiModelProperty(value = "就诊类别名称")
        swaggerDoc.append("@io.swagger.annotations.ApiModelProperty(value = \"");
        if (StringUtils.isNotBlank(fieldEntity.getFieldComment())) {
            swaggerDoc.append(fieldEntity.getFieldComment()).append("\"");
        } else {
            swaggerDoc.append(fieldEntity.getFieldName()).append("\"");
        }

        if ("是".equals(fieldEntity.getRequired())) {
            swaggerDoc.append(",required = true ");
        }
        swaggerDoc.append(")");
        PsiAnnotation comment = factory.createAnnotationFromText(String.valueOf(swaggerDoc), null);
        field.addBefore(comment, field.getFirstChild());
    }

}
