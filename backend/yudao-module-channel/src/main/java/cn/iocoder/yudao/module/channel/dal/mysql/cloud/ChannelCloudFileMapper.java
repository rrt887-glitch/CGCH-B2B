package cn.iocoder.yudao.module.channel.dal.mysql.cloud;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.cloud.ChannelCloudFileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelCloudFileMapper extends BaseMapperX<ChannelCloudFileDO> {

    /** 目录下的文件：文件夹在前，再按更新时间倒序 */
    default List<ChannelCloudFileDO> selectChildren(String scope, Long ownerUserId, Long parentId, String keyword) {
        return selectList(new LambdaQueryWrapperX<ChannelCloudFileDO>()
                .eq(ChannelCloudFileDO::getScope, scope)
                .eqIfPresent(ChannelCloudFileDO::getOwnerUserId, ownerUserId)
                .eq(keyword == null, ChannelCloudFileDO::getParentId, parentId)
                .likeIfPresent(ChannelCloudFileDO::getName, keyword)
                .orderByDesc(ChannelCloudFileDO::getFolder).orderByDesc(ChannelCloudFileDO::getUpdateTime));
    }

    /** AI 可以发给客户的企业云盘文件 */
    default List<ChannelCloudFileDO> selectAiSendable(int limit) {
        return selectList(new LambdaQueryWrapperX<ChannelCloudFileDO>()
                .eq(ChannelCloudFileDO::getScope, ChannelCloudFileDO.SCOPE_ENTERPRISE)
                .eq(ChannelCloudFileDO::getFolder, false).eq(ChannelCloudFileDO::getAiSendable, true)
                .orderByDesc(ChannelCloudFileDO::getUpdateTime).last("LIMIT " + limit));
    }

}
