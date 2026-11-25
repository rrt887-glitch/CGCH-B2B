package cn.iocoder.yudao.module.channel.dal.mysql.file;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 附件安全迁移用：按「表 + 列」批量找文件地址、批量替换。
 *
 * 表名和列名来自白名单常量，不会拼接用户输入。
 */
@Mapper
public interface ChannelFileSecurityMapper {

    @Select("SELECT DISTINCT ${column} FROM ${table} WHERE ${column} LIKE '%/admin-api/infra/file/%'")
    List<String> selectFileUrls(@Param("table") String table, @Param("column") String column);

    @Update("UPDATE ${table} SET ${column} = REPLACE(${column}, #{oldUrl}, #{newUrl}) "
            + "WHERE ${column} LIKE CONCAT('%', #{oldUrl}, '%')")
    int replaceUrl(@Param("table") String table, @Param("column") String column,
                   @Param("oldUrl") String oldUrl, @Param("newUrl") String newUrl);
}
