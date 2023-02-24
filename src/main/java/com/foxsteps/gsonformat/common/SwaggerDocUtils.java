package com.foxsteps.gsonformat.common;

import com.foxsteps.gsonformat.entity.FieldEntity;

/**
 * @author wangzejun
 * @description SwaggerDoc注释工具
 * @date 2020年07月03日 19:10:14
 */
public class SwaggerDocUtils {

    /**
     * 增加字段注释
     *
     * @param fieldEntity
     */
    public static String getFieldAnnotation(FieldEntity fieldEntity) {
        StringBuffer swaggerDoc = new StringBuffer();
        //        @ApiModelProperty(value = "就诊类别名称")
        swaggerDoc.append("@io.swagger.annotations.ApiModelProperty(value = \"");
        if (StringUtils.isNotBlank(fieldEntity.getFieldComment())) {
            swaggerDoc.append(StringUtils.escapeExprSpecialWord(fieldEntity.getFieldComment())).append("\"");
        } else {
            swaggerDoc.append(fieldEntity.getFieldName()).append("\"");
        }

        if ("是".equals(fieldEntity.getRequired())) {
            swaggerDoc.append(",required = true ");
        }
        swaggerDoc.append(")\n");
        return swaggerDoc.toString();
    }

}
