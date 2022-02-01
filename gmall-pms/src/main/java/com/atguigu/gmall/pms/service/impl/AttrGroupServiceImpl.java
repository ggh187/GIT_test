package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.mapper.SpuAttrValueMapper;
import com.atguigu.gmall.pms.vo.AttrValueVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.AttrGroupMapper;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;
import org.springframework.util.CollectionUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupMapper, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    private AttrMapper attrMapper;

    @Autowired
    private SpuAttrValueMapper attrValueMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<AttrGroupEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<AttrGroupEntity> queryGroupsWithAttrsByCid(Long cid) {

        // 1.根据分类id查询分组
        //这里需要注意了游戏分类里面是没有规格参数的,他就是没有,如果这种情况那就是空指针异常了
        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));
        if (CollectionUtils.isEmpty(groupEntities)){
            return null;
        }

        // 2.遍历分组的id查询组下的规格参数
        //因为我们的attr表里面是没有attrEnity这个字段的, 所以在这一步里面需要把那个list类型的List<AttrEntity> attrEntities放上我们需要的东西
        //遍历取出list里面的group_id 作为参数查询AttrEntity方法  selectList 方法是根据 entity 条件，查询全部记录
        //补充, AttrGroupEntity 里面的  attrEntities  列的特殊处理办法,  实际上自动化代码生产工具里面的所有表都有一个对应的对象, 这个对象默认里面的所以的字段都是表里面都有的,
        //我们不给自己加上的那个字段做特殊标记就会报错, 显示一个unknow column   这时候有一个注解就需要手动使用,   @TableField(exist = false)  这个注解是每个字段默认自己已经添加了,里面的一些属性也是默认的,
        //所以需要我们自己手动的修改她的一些配置          boolean exist() default true;  是否为数据库表字段默认 true 存在，false 不存在
        //pms_attr 表里面的字段type中1 为基本类型的销售参数, 0 的为销售类型的规格参数  AttrEntity

        groupEntities.forEach(group -> {
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", group.getId()).eq("type", 1));
            group.setAttrEntities(attrEntities);
        });

        return groupEntities;
    }

    @Override
    public List<ItemGroupVo> queryGroupWithAttrValuesBy(Long cid, Long spuId, Long skuId) {
        // 根据分类id查询出所有的分组信息
        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));
        if (CollectionUtils.isEmpty(groupEntities)){
            return null;
        }

        return groupEntities.stream().map(groupEntity -> {
            ItemGroupVo groupVo = new ItemGroupVo();
            // 获取每个分组下的规格参数列表 --> attrIds
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", groupEntity.getId()));
            if (!CollectionUtils.isEmpty(attrEntities)){
                // 获取attrId的集合
                List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());

                List<AttrValueVo> attrValueVos = new ArrayList<>();
                // 查询基本的规格参数及值
                List<SpuAttrValueEntity> spuAttrValueEntities = this.attrValueMapper.selectList(new QueryWrapper<SpuAttrValueEntity>().in("attr_id", attrIds).eq("spu_id", spuId));
                if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                    attrValueVos.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();
                        BeanUtils.copyProperties(spuAttrValueEntity, attrValueVo);
                        return attrValueVo;
                    }).collect(Collectors.toList()));
                }

                // 查询销售的规格参数及值
                List<SkuAttrValueEntity> skuAttrValueEntities = this.skuAttrValueMapper.selectList(new QueryWrapper<SkuAttrValueEntity>().in("attr_id", attrIds).eq("sku_id", skuId));
                if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                    attrValueVos.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();
                        BeanUtils.copyProperties(skuAttrValueEntity, attrValueVo);
                        return attrValueVo;
                    }).collect(Collectors.toList()));
                }

                groupVo.setAttrValue(attrValueVos);
            }

            groupVo.setId(groupEntity.getId());
            groupVo.setName(groupEntity.getName());
            return groupVo;
        }).collect(Collectors.toList());
    }

}
