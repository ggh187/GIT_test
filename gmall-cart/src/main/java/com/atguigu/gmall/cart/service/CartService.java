package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;

import java.math.BigDecimal;
import java.sql.Time;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CartService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService asyncService;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

//    @Autowired
//    private ThreadPoolExecutor threadPoolExecutor;

    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    public void saveCart(Cart cart) {
        // 1.???????????????????????????
        String userId = getUserId();

        // ???????????????????????????????????????map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        // ????????????????????????????????????????????????
        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        if (hashOps.hasKey(skuId)) {
            // ?????????????????????
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
            // ???????????????????????????????????????redis????????????
            this.asyncService.updateCart(userId, skuId, cart);
            //this.cartMapper.update(cart, new UpdateWrapper<Cart>().eq("user_id", userId).eq("sku_id", skuId));
        } else {
            // ??????????????????????????????
            cart.setUserId(userId);
            cart.setCheck(true);

            // ??????sku????????????
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null){
                return ;
            }
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());

            // ????????????
            ResponseVo<List<WareSkuEntity>> listResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = listResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // ????????????
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySaleAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));
            // ??????????????????
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVos));

            //hashOps.put(skuId, JSON.toJSONString(cart));
            this.asyncService.insertCart(userId, cart);
            // ????????????????????????????????????
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuEntity.getPrice().toString());
        }
        hashOps.put(skuId, JSON.toJSONString(cart));
    }

    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (userInfo.getUserId() == null) {
            return userInfo.getUserKey();
        } else {
            return userInfo.getUserId().toString();
        }
    }

    public Cart queryCartBySkuId(Long skuId) {

        String userId = this.getUserId();

        // ????????????key???userId???userKey???????????????map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        if (hashOps.hasKey(skuId.toString())) {
            String cartJson = hashOps.get(skuId.toString()).toString();

            return JSON.parseObject(cartJson, Cart.class);
        }
        throw new CartException("????????????????????????????????????????????????");
    }

    @Async
    public void executor1() {
        try {
            System.out.println("executor1????????????????????????????????????");
            TimeUnit.SECONDS.sleep(4);
            int i = 1/0;
            System.out.println("executor1??????????????????==========");
            //return AsyncResult.forValue("hello executor1");
        } catch (InterruptedException e) {
            e.printStackTrace();
            //return AsyncResult.forExecutionException(e);
        }
    }

    @Async
    public void executor2() {
        try {
            System.out.println("executor2????????????????????????????????????");
            TimeUnit.SECONDS.sleep(5);
            System.out.println("executor2??????????????????==========");
            //return AsyncResult.forValue("hello executor2");
        } catch (Exception e) {
            e.printStackTrace();
            //return AsyncResult.forExecutionException(e);
        }
    }

    public List<Cart> queryCarts() {

        //1.??????userKey
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        // ??????key
        String unloginKey = KEY_PREFIX + userKey;
        //2.??????userKey???????????????????????????
        // ????????????????????????????????????map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(unloginKey);
        // ?????????????????????????????????????????????List<String>
        List<Object> unloginCartJsons = hashOps.values();
        List<Cart> unloginCarts  = null;
        if (!CollectionUtils.isEmpty(unloginCartJsons)){
            unloginCarts  = unloginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        //3.??????userId
        Long userId = userInfo.getUserId();
        //4.??????userId?????????????????????
        if (userId == null) {
            return unloginCarts;
        }

        //5.?????????????????????????????????????????????????????????
        String loginKey = KEY_PREFIX + userId;
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(loginKey);
        if (!CollectionUtils.isEmpty(unloginCarts)){
            unloginCarts.forEach(cart -> { // ?????????????????????????????????
                String skuId = cart.getSkuId().toString();
                BigDecimal count = cart.getCount();
                if (loginHashOps.hasKey(skuId)){
                    // ???????????????????????????????????????????????????
                    String cartJson = loginHashOps.get(skuId).toString(); // ????????????????????????????????????
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    // ??????redis ????????????mysql
                    this.asyncService.updateCart(userId.toString(), skuId, cart);
                    //this.cartMapper.update(cart, new QueryWrapper<Cart>().eq("user_id", userId.toString()).eq("sku_id", skuId));
                } else {
                    // ???????????????????????????????????????????????????
                    cart.setUserId(userId.toString());
                    this.asyncService.insertCart(userId.toString(), cart);
                }
                loginHashOps.put(skuId, JSON.toJSONString(cart));
            });
        }

        //6.??????????????????????????????
        this.redisTemplate.delete(unloginKey);
        this.asyncService.deleteCart(userKey);

        //7.??????????????????????????????
        List<Object> cartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(cartJsons)){
             return cartJsons.stream().map(cartJson -> {
                 Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                 cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                 return cart;
             }).collect(Collectors.toList());
        }
        return null;
    }

    public void updateNum(Cart cart) {

        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!hashOps.hasKey(cart.getSkuId().toString())){
            throw new CartException("??????????????????????????????????????????");
        }
        BigDecimal count = cart.getCount();
        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        cart = JSON.parseObject(cartJson, Cart.class);
        cart.setCount(count);

        hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        this.asyncService.updateCart(userId, cart.getSkuId().toString(), cart);
    }

    public void deleteCart(Long skuId) {

        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!hashOps.hasKey(skuId.toString())){
            throw new CartException("??????????????????????????????????????????");
        }
        hashOps.delete(skuId.toString());
        this.asyncService.deleteCartBySkuId(userId, skuId);
    }

    public List<Cart> queryCheckedCarts(Long userId) {

        String key = KEY_PREFIX + userId;

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        List<Object> cartJsons = hashOps.values();
        if (CollectionUtils.isEmpty(cartJsons)){
            return null;
        }

        return cartJsons.stream()
                .map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class))
                .filter(Cart::getCheck)
                .collect(Collectors.toList());
    }
}
