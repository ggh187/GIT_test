package com.atguigu.gmall.common.bean;

import lombok.Data;

/**
 * 返回数据
 */
@Data
public class ResponseVo<T> {
	private static final long serialVersionUID = 1L;
//code  响应状态码 msg 响应信息 data 响应的数据  泛型T 可以是list集合  或者map集合
	private Integer code;
	private String msg;
	private T data;

	/**
	 * @return 响应失败设置响应状态码为1
	 */
	public static ResponseVo fail() {
		ResponseVo responseVo = new ResponseVo();
		responseVo.setCode(1);
		responseVo.setMsg("服务器错误！！！");
		return responseVo;
	}

	/**
	 * @param msg 此方法制定响应信息
	 * @return
	 */
	public static ResponseVo fail(String msg) {
		ResponseVo responseVo = new ResponseVo();
		responseVo.setCode(1);
		responseVo.setMsg(msg);
		return responseVo;
	}

	public static <T> ResponseVo<T> ok(T data) {
		ResponseVo responseVo = new ResponseVo();
		responseVo.setCode(0);
		responseVo.setData(data);
		return responseVo;
	}

	public static ResponseVo ok() {
		ResponseVo responseVo = new ResponseVo();
		responseVo.setCode(0);
		responseVo.setMsg("操作成功！");
		return responseVo;
	}

}
