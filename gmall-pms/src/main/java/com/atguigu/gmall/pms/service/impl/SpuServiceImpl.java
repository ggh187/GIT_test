package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.*;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.jsf.el.WebApplicationContextFacesELResolver;

import javax.naming.Name;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private SpuDescService descService;

    @Autowired
    private SpuAttrValueService baseAttrService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuImagesService imagesService;

    @Autowired
    private SkuAttrValueService saleAttrService;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidAndPage(PageParamVo pageParamVo, Long cid) {
        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        // category_id=225 and (id=7 or name like '%7%')
        // 分类id条件
        //下面的这段wapper的逻辑实际上就是一段sql
        // select * from pms_spu where  category_id = 255  and (id=7 or name like ='%7%')
        //后面的括号里面的是一个条件不能和去掉括号

        if (cid != 0){
            //查询分类id
            wrapper.eq("category_id", cid);
        }

        // 关键字查询
        //:pms/spu/category/225?t=1610394465998 &pageNum=1&pageSize=10&*key=xxxx
        String key = pageParamVo.getKey();
        //StringUtils.isNotEmpty()  return cs == null || cs.length() == 0
        if (StringUtils.isNotBlank(key)){
            //这是关键词查询  如何使用wapper实现添加的那两个括号
            // wrapper.and 这里面是一个消费类型的函数式接口 ,指的是需要一个消费参数,没有返回结果集
            //default Children and(Consumer<Param> consumer)
            //下面的t 是消费者,wapper对象
            //wrapper.and(t->t.eq("id",key).or().like("name",key));
            //扩展一下,or后面再加上一个括号呢   select * from pms_spu where  category_id = 255  and (id=7 or (name like ='%7%' and xxx="xx"))
            //wrapper.and(t->t.eq("id",key).or(k->k.like("name",key).and()))
            wrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        /*其实主要是下发的方法是我们返回去的对象,上面的querywapper是对查询条件的限制*/
        /*this.page方法 里面的参数需要传入一个page对象(其他方法转化为page方法)和 查询类型(queryWapper对象)*/
        IPage<SpuEntity> page = this.page(
                pageParamVo.getPage(), //这里getpage返回的是ipage对象
                wrapper
        );
        //此方法里面的把page里面的方法转化为pageRestulrVo里面的对象
        //这个方法看似简单只有几行代码,但是里面的逻辑转化还是挺复杂的  前端请求传过来的是pageparamVo对象,
        //pageparamVo里面的getpage方法把获取ipage对象, queryWapper对象查询ipage 获取ipage对象, 再把ipage对象转换为page
        //PageResultVo  返回给前端

        return new PageResultVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spu) throws FileNotFoundException {
        /* 首先分析一下,大保存方法里面的数据需要放到三个表里面既*/


        // 1.先保存spu相关信息
        spu.setCreateTime(new Date());
        this.save(spu);

        // 1.1. 保存pms_spu
        Long spuId = saveSpu(spu);

        // 1.2. 保存pms_spu_desc
        //这个spu_desc的对象的属性有
        // @TableId(type = IdType.INPUT)
        //	private Long spuId
        //	private String decript;


        //this.saveSpuDesc(spu, spuId);
        this.descService.saveSpuDesc(spu, spuId);

//        try {
//            TimeUnit.SECONDS.sleep(4);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        //
//        new FileInputStream("xxxx");

        // 1.3. 保存pms_spu_attr_value
        saveBaseAttr(spu, spuId);

        // 2.再保存sku相关信息
        saveSkuInfo(spu, spuId);

        this.rabbitTemplate.convertAndSend("PMS_ITEM_EXCHANGE", "item.insert", spuId);
//        int i = 1/0;
    }

    private void saveSkuInfo(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)){
            return;
        }
        skus.forEach(sku -> {
            // 2.1. 保存pms_sku
            sku.setSpuId(spuId);
            sku.setCategoryId(spu.getCategoryId());
            sku.setBrandId(spu.getBrandId());
            // 设置默认图片
            List<String> images = sku.getImages();
            //sku.getDefaultImage(0)这个方法没有容错率, 因为用户没有上传图片, 我们需要给他们一个默认的图片, 如果上传了才会使用他们自己的
            //sku.setDefaultImages(StringUntils.isNotBlank(sku.getDefaultImage()) ? sku.getDefaultImage() :  images.get(0))
            if (!CollectionUtils.isEmpty(images)){
                sku.setDefaultImage(StringUtils.isNotBlank(sku.getDefaultImage()) ? sku.getDefaultImage() : images.get(0));
            }
            this.skuMapper.insert(sku);
            Long skuId = sku.getId();

            // 2.2. 保存pms_sku_images
            if (!CollectionUtils.isEmpty(images)){
        // 这里的逻辑就是把一个图片的集合,转化为一个图片对象的集合

                this.imagesService.saveBatch(images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setUrl(image);
                    skuImagesEntity.setSkuId(skuId);
                    //判断当前图片是否为默认图片, 比较它们的地址 sku里面的图片的地址和图片对象的地址比较, 如果地址是一样的话他们就是同一个图片, 然后就可以设置状态了

                    skuImagesEntity.setDefaultStatus(StringUtils.equals(sku.getDefaultImage(), image) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList()));
            }

            // 2.3. 保存pms_sku_attr_v  alue
            List<SkuAttrValueEntity> saleAttrs = sku.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(skuAttrValueEntity -> skuAttrValueEntity.setSkuId(skuId));
                this.saleAttrService.saveBatch(saleAttrs);
            }

            // 3.最后保存营销信息
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(sku, skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.smsClient.saveSales(skuSaleVo);
        });
    }

    private void saveBaseAttr(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)){
            this.baseAttrService.saveBatch(baseAttrs.stream().map(spuAttrValueVo -> {
                SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrValueVo, spuAttrValueEntity);
                spuAttrValueEntity.setSpuId(spuId);
                return spuAttrValueEntity;
            }).collect(Collectors.toList()));
        }
    }

    private Long saveSpu(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        this.save(spu);
        return spu.getId();
    }

//    public static void main(String[] args) {
//        List<User> users = Arrays.asList(
//            new User(1l, "柳岩", 20),
//            new User(2l, "马蓉", 21),
//            new User(3l, "小鹿", 22),
//            new User(4l, "郑爽", 23),
//            new User(5l, "老王", 24)
//        );
//
//        // 过滤filter 集合之间的转化map 求和reduce
//        users.stream().filter(user -> user.getAge() > 22).collect(Collectors.toList()).forEach(System.out::println);
//
//        users.stream().map(User::getName).collect(Collectors.toList()).forEach(System.out::println);
//        users.stream().map(user -> {
//            Person person = new Person();
//            person.setId(user.getId());
//            person.setPersonName(user.getName());
//            person.setAge(user.getAge());
//            return person;
//        }).collect(Collectors.toList()).forEach(System.out::println);
//
//        System.out.println(users.stream().map(User::getAge).reduce((a, b) -> a + b).get());
//    }
}

//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//class User{
//
//    private Long id;
//    private String name;
//    private Integer age;
//}
//
//@Data
//class Person{
//    private Long id;
//    private String personName;
//    private Integer age;
//}
